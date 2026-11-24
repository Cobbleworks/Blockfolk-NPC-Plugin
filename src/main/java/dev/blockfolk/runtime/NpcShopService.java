package dev.blockfolk.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.ShopState;
import dev.blockfolk.repository.ShopRepository;
import dev.blockfolk.repository.ShopStateRepository;
import dev.blockfolk.util.Durations;
import dev.blockfolk.util.UiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Runs shared shops for players: decides which trades a shop shows right now
 * (rotating assortments), restocks limited trades when a period ends, executes
 * trades with items and money, and opens the right screen. Shops with only
 * unlimited item-for-item trades open in the vanilla trading screen; shops with
 * money or stock limits open in the Blockfolk shop menu.
 */
public final class NpcShopService implements Listener {

    /**
     * The Blockfolk shop menu, for shops the vanilla trading screen cannot show.
     */
    public interface Storefront {
        void open(Player player, Shop shop);

        /** Re-renders every open menu of the shop, for example after a restock. */
        void refresh(String shopKey);

        /** Keys of shops someone is looking at. */
        Set<String> viewedShops();

        /** Called once a second to update countdowns. */
        void tick();
    }

    /**
     * The trades a shop shows right now, in editor order, and when it restocks next
     * ({@code -1} for never).
     */
    public record Assortment(List<ShopOffer> offers, long nextRefreshAt) {
        public boolean refreshes() {
            return nextRefreshAt >= 0;
        }
    }

    /**
     * How a click went: {@code trades} completed before {@code stopReason}
     * ({@link ShopTrade.Result#DONE} when every requested trade happened).
     */
    public record TradeOutcome(ShopOffer offer, int trades, ShopTrade.Result stopReason) {
    }

    private static final long TICK_PERIOD = 20L;

    private final Plugin plugin;
    private final ShopRepository shops;
    private final ShopStateRepository states;
    private final ShopEconomy economy;
    private final LongSupplier clock;
    private final RandomGenerator random;
    private final Logger logger;
    private final Map<UUID, String> merchantViewers = new HashMap<>();
    private Storefront storefront;
    private BukkitTask task;

    public NpcShopService(Plugin plugin, ShopRepository shops, ShopStateRepository states, ShopEconomy economy) {
        this(plugin, shops, states, economy, System::currentTimeMillis, RandomGenerator.getDefault());
    }

    NpcShopService(Plugin plugin, ShopRepository shops, ShopStateRepository states, ShopEconomy economy,
            LongSupplier clock, RandomGenerator random) {
        this.plugin = plugin;
        this.shops = shops;
        this.states = states;
        this.economy = economy;
        this.clock = clock;
        this.random = random;
        this.logger = plugin == null ? Logger.getLogger("Blockfolk") : plugin.getLogger();
    }

    public void setStorefront(Storefront storefront) {
        this.storefront = storefront;
    }

    public ShopEconomy economy() {
        return economy;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void stop() {
        if (task != null)
            task.cancel();
        task = null;
    }

    /**
     * The trades {@code shop} shows now. Starts a new stock period first when the
     * last one ended, which restocks limited trades and draws a new rotation.
     */
    public Assortment assortment(Shop shop) {
        long now = clock.getAsLong();
        ShopState state = states.get(shop.key(), now);
        int slots = shop.assortment().rotatingSlots();
        boolean changed = false;
        boolean restocked = false;
        long period = ShopRotation.currentPeriodStart(state.periodStart(), shop.assortment().refreshMillis(), now);
        if (period != state.periodStart()) {
            Set<String> previous = Set.copyOf(state.picks());
            state.restock(period);
            state.setPicks(shop.assortment().rotates()
                    ? ShopRotation.draw(shop.rotationPool(), slots, previous, random)
                    : List.of());
            changed = true;
            restocked = true;
        } else if (shop.assortment().rotates()) {
            List<String> reconciled = ShopRotation.reconcile(state.picks(), shop.rotationPool(), slots, random);
            if (!reconciled.equals(state.picks())) {
                state.setPicks(reconciled);
                changed = true;
            }
        }
        changed |= state.retainOffers(shop.offers().stream().map(ShopOffer::id).collect(Collectors.toSet()));
        if (changed)
            states.save();
        if (restocked)
            announceRestock(shop.key());
        long next = shop.assortment().refreshes() ? state.periodStart() + shop.assortment().refreshMillis() : -1;
        return new Assortment(ShopRotation.visible(shop, state.picks()), next);
    }

    /**
     * Restocks every limited trade and draws a new rotation now; the next automatic
     * restock follows one interval later.
     */
    public void restockNow(Shop shop) {
        long now = clock.getAsLong();
        ShopState state = states.get(shop.key(), now);
        Set<String> previous = Set.copyOf(state.picks());
        state.restock(now);
        state.setPicks(shop.assortment().rotates()
                ? ShopRotation.draw(shop.rotationPool(), shop.assortment().rotatingSlots(), previous, random)
                : List.of());
        states.save();
        announceRestock(shop.key());
    }

    /** Trades of {@code offer} that {@code buyer} can still make this period. */
    public int remaining(Shop shop, ShopOffer offer, UUID buyer) {
        if (!offer.terms().limited())
            return Integer.MAX_VALUE;
        ShopState state = states.get(shop.key(), clock.getAsLong());
        return Math.max(0, offer.terms().stockLimit() - state.sold(offer.id(), offer.terms().stockScope(), buyer));
    }

    /** How many items exactly like {@code item} the player carries. */
    public static int carried(Player player, org.bukkit.inventory.ItemStack item) {
        return InventoryTradeGoods.count(player.getInventory(), item);
    }

    /** Trades sold this period, over all players. */
    public int soldThisPeriod(Shop shop, ShopOffer offer) {
        ShopState state = states.get(shop.key(), clock.getAsLong());
        int shared = state.sharedSales().getOrDefault(offer.id(), 0);
        int perPlayer = state.playerSales().getOrDefault(offer.id(), Map.of()).values().stream()
                .mapToInt(Integer::intValue).sum();
        return shared + perPlayer;
    }

    /**
     * Makes up to {@code maxTrades} trades of the offer with id {@code offerId} for
     * {@code player}, stopping at the first one that cannot happen. The offer must
     * be in the shop's current assortment.
     */
    public TradeOutcome trade(Player player, Shop shop, String offerId, int maxTrades) {
        ShopOffer offer = assortment(shop).offers().stream().filter(candidate -> candidate.id().equals(offerId))
                .findFirst().orElse(null);
        if (offer == null)
            return new TradeOutcome(shop.findOffer(offerId).orElse(null), 0, ShopTrade.Result.UNAVAILABLE);
        ShopTrade.Wallet wallet = wallet(player);
        ShopState state = states.get(shop.key(), clock.getAsLong());
        int trades = 0;
        ShopTrade.Result result = ShopTrade.Result.DONE;
        while (trades < maxTrades) {
            if (remaining(shop, offer, player.getUniqueId()) <= 0) {
                result = ShopTrade.Result.SOLD_OUT;
                break;
            }
            result = ShopTrade.execute(offer, new InventoryTradeGoods(player.getInventory()), wallet,
                    amount -> logger.severe(() -> "Shop " + shop.key() + ": refunding " + amount + " to "
                            + player.getName() + " failed after a payout was refused. Refund it manually."));
            if (result != ShopTrade.Result.DONE)
                break;
            trades++;
            state.recordSale(offer.id(), offer.terms().stockScope(), player.getUniqueId(), 1);
        }
        if (trades > 0 && offer.terms().limited())
            states.save();
        if (trades > 0)
            player.updateInventory();
        return new TradeOutcome(offer, trades, trades == maxTrades ? ShopTrade.Result.DONE : result);
    }

    private ShopTrade.Wallet wallet(Player player) {
        return new ShopTrade.Wallet() {
            @Override
            public boolean available() {
                return economy.available();
            }

            @Override
            public boolean has(double amount) {
                return economy.has(player, amount);
            }

            @Override
            public boolean withdraw(double amount) {
                return economy.withdraw(player, amount);
            }

            @Override
            public boolean deposit(double amount) {
                return economy.deposit(player, amount);
            }
        };
    }

    /**
     * Opens the shop stored under {@code shopKey} for {@code player}. Admins are
     * told why nothing opened.
     *
     * @return whether a shop screen was opened
     */
    public boolean open(Player player, String shopKey) {
        if (shopKey == null) {
            notifyAdmin(player, "This Open Shop action has no shop selected.");
            return false;
        }
        Shop shop = shops.find(shopKey).orElse(null);
        if (shop == null) {
            notifyAdmin(player, "The shop '" + shopKey + "' no longer exists.");
            return false;
        }
        return open(player, shop);
    }

    public boolean open(Player player, Shop shop) {
        Assortment assortment = assortment(shop);
        if (assortment.offers().isEmpty()) {
            notifyAdmin(player,
                    shop.offeredTrades().isEmpty()
                            ? "The shop " + shop.name() + " has no complete, offered trades."
                            : "The shop " + shop.name() + " shows no trades right now.");
            return false;
        }
        if (shop.needsShopMenu() && storefront != null) {
            if (!economy.available() && assortment.offers().stream().anyMatch(offer -> offer.terms().usesMoney()))
                notifyAdmin(player, "Money trades of " + shop.name()
                        + " cannot be used: no economy plugin is connected through Vault.");
            storefront.open(player, shop);
            return true;
        }
        Merchant merchant = Bukkit.getServer().createMerchant();
        merchant.setRecipes(assortment.offers().stream().map(NpcShopService::recipe).toList());
        Component title = Component.text(shop.name());
        if (assortment.refreshes())
            title = title.append(Component.text(
                    " · new stock in " + Durations.remaining(assortment.nextRefreshAt() - clock.getAsLong()),
                    NamedTextColor.DARK_GRAY));
        MenuType.MERCHANT.builder().merchant(merchant).title(title).checkReachable(false).build(player).open();
        merchantViewers.put(player.getUniqueId(), shop.key());
        return true;
    }

    /** Removes the stock state of a deleted shop. */
    public void forget(String shopKey) {
        states.delete(shopKey);
    }

    private static MerchantRecipe recipe(ShopOffer offer) {
        // A fresh merchant is built for every opening, so the use limit never locks a
        // trade.
        MerchantRecipe recipe = new MerchantRecipe(offer.result(), 0, Integer.MAX_VALUE, false, 0, 0f, true);
        recipe.addIngredient(offer.cost());
        if (offer.secondCost() != null)
            recipe.addIngredient(offer.secondCost());
        return recipe;
    }

    /**
     * Restocks shops someone is looking at, so open screens change the moment the
     * period ends.
     */
    void tick() {
        Set<String> viewed = new HashSet<>(merchantViewers.values());
        if (storefront != null) {
            viewed.addAll(storefront.viewedShops());
            storefront.tick();
        }
        for (String key : viewed)
            shops.find(key).ifPresent(this::assortment);
    }

    /**
     * Tells the shop's viewers about the new stock and shows it. Runs on the next
     * tick, because a restock can be noticed in the middle of an inventory click.
     */
    private void announceRestock(String shopKey) {
        if (plugin == null || !plugin.isEnabled())
            return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (storefront != null)
                storefront.refresh(shopKey);
            for (Map.Entry<UUID, String> viewer : List.copyOf(merchantViewers.entrySet())) {
                Player player = Bukkit.getPlayer(viewer.getKey());
                if (player == null || !viewer.getValue().equals(shopKey))
                    continue;
                player.sendMessage(UiText.info("The shop has new stock."));
                shops.find(shopKey).ifPresent(shop -> open(player, shop));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof MerchantInventory)
            merchantViewers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        merchantViewers.remove(event.getPlayer().getUniqueId());
    }

    private static void notifyAdmin(Player player, String message) {
        if (player.hasPermission("blockfolk.admin"))
            player.sendMessage(UiText.warning(message));
    }
}
