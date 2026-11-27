package dev.blockfolk.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
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
 * (all pages, or one page per Minecraft day), restocks limited trades at every
 * sunrise, executes trades with items and money, and opens the right screen.
 * Shops with only unlimited item-for-item trades open in the vanilla trading
 * screen; shops with money or stock limits open in the Blockfolk shop menu.
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
     * The clock shops follow: the main overworld's. Days start at sunrise.
     */
    public interface DayClock {
        /** The world's full time in ticks, counting days. */
        long fullTime();

        /** Whether time passes, so that new days come. */
        boolean advances();

        static DayClock overworld() {
            return new DayClock() {
                @Override
                public long fullTime() {
                    World world = mainWorld();
                    return world == null ? 0 : world.getFullTime();
                }

                @Override
                public boolean advances() {
                    World world = mainWorld();
                    return world != null && Boolean.TRUE.equals(world.getGameRuleValue(GameRules.ADVANCE_TIME));
                }

                private static World mainWorld() {
                    List<World> worlds = Bukkit.getWorlds();
                    return worlds.isEmpty() ? null : worlds.getFirst();
                }
            };
        }
    }

    /**
     * The trades a shop shows right now, in editor order, the page on sale in a
     * one-page-per-day shop, and the time until the next restock ({@code -1} while
     * time stands still).
     */
    public record Assortment(List<ShopOffer> offers, int page, long millisUntilRestock) {
        public boolean restocks() {
            return millisUntilRestock >= 0;
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
    private final DayClock clock;
    private final Logger logger;
    private final Map<UUID, String> merchantViewers = new HashMap<>();
    private Storefront storefront;
    private BukkitTask task;

    public NpcShopService(Plugin plugin, ShopRepository shops, ShopStateRepository states, ShopEconomy economy) {
        this(plugin, shops, states, economy, DayClock.overworld());
    }

    NpcShopService(Plugin plugin, ShopRepository shops, ShopStateRepository states, ShopEconomy economy,
            DayClock clock) {
        this.plugin = plugin;
        this.shops = shops;
        this.states = states;
        this.economy = economy;
        this.clock = clock;
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
     * The trades {@code shop} shows now. When a new Minecraft day began since the
     * shop was last looked at, limited trades are restocked first and a
     * one-page-per-day shop turns as many pages as days went by.
     */
    public Assortment assortment(Shop shop) {
        long fullTime = clock.fullTime();
        long today = ShopDays.day(fullTime);
        ShopState state = state(shop, today);
        List<Integer> pages = shop.pagesWithTrades();
        int onSale = ShopDays.pageOnSale(pages, state.page());
        boolean changed = false;
        boolean restocked = false;
        if (!state.knowsDay()) {
            state.setDay(today);
            changed = true;
        } else if (state.day() != today) {
            // Normally one day passes; after a time jump backwards (such as
            // /time set) the shop still moves on by one page.
            long days = today > state.day() ? today - state.day() : 1;
            state.restock(today);
            if (shop.onePagePerDay())
                onSale = ShopDays.pageAfter(pages, onSale, days);
            changed = true;
            restocked = true;
        }
        if (shop.onePagePerDay() && !pages.isEmpty() && onSale != state.page()) {
            state.setPage(onSale);
            changed = true;
        }
        changed |= state.retainOffers(shop.offers().stream().map(ShopOffer::id).collect(Collectors.toSet()));
        if (changed)
            states.save();
        if (restocked)
            announceRestock(shop.key());
        long untilRestock = clock.advances() ? ShopDays.ticksUntilNextDay(fullTime) * 50L : -1;
        return new Assortment(ShopDays.visible(shop, onSale), onSale, untilRestock);
    }

    /**
     * Restocks every limited trade now and, in a one-page-per-day shop, turns to
     * the next page. The next restock still comes at sunrise.
     */
    public void restockNow(Shop shop) {
        long today = ShopDays.day(clock.fullTime());
        ShopState state = state(shop, today);
        List<Integer> pages = shop.pagesWithTrades();
        int onSale = ShopDays.pageOnSale(pages, state.page());
        state.restock(today);
        if (shop.onePagePerDay())
            state.setPage(ShopDays.pageAfter(pages, onSale, 1));
        states.save();
        announceRestock(shop.key());
    }

    private ShopState state(Shop shop, long today) {
        return states.get(shop.key(), today);
    }

    /** Trades of {@code offer} that {@code buyer} can still make today. */
    public int remaining(Shop shop, ShopOffer offer, UUID buyer) {
        if (!offer.terms().limited())
            return Integer.MAX_VALUE;
        ShopState state = state(shop, ShopDays.day(clock.fullTime()));
        return Math.max(0, offer.terms().stockLimit() - state.sold(offer.id(), offer.terms().stockScope(), buyer));
    }

    /** How many items exactly like {@code item} the player carries. */
    public static int carried(Player player, org.bukkit.inventory.ItemStack item) {
        return InventoryTradeGoods.count(player.getInventory(), item);
    }

    /** Trades sold since the last restock, over all players. */
    public int soldSinceRestock(Shop shop, ShopOffer offer) {
        ShopState state = state(shop, ShopDays.day(clock.fullTime()));
        int shared = state.sharedSales().getOrDefault(offer.id(), 0);
        int perPlayer = state.playerSales().getOrDefault(offer.id(), Map.of()).values().stream()
                .mapToInt(Integer::intValue).sum();
        return shared + perPlayer;
    }

    /**
     * Makes up to {@code maxTrades} trades of the offer with id {@code offerId} for
     * {@code player}, stopping at the first one that cannot happen. The offer must
     * be on sale right now.
     */
    public TradeOutcome trade(Player player, Shop shop, String offerId, int maxTrades) {
        ShopOffer offer = assortment(shop).offers().stream().filter(candidate -> candidate.id().equals(offerId))
                .findFirst().orElse(null);
        if (offer == null)
            return new TradeOutcome(shop.findOffer(offerId).orElse(null), 0, ShopTrade.Result.UNAVAILABLE);
        ShopTrade.Wallet wallet = wallet(player);
        ShopState state = state(shop, ShopDays.day(clock.fullTime()));
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
            notifyAdmin(player, "The shop " + shop.name() + " has no complete trades.");
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
        if (assortment.restocks() && shop.changesAtSunrise())
            title = title
                    .append(Component.text(" · new stock in " + Durations.remaining(assortment.millisUntilRestock()),
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
     * Restocks shops someone is looking at, so open screens change the moment a new
     * day begins.
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
