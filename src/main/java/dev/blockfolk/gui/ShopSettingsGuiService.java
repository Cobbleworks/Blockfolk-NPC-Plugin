package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.StockScope;
import dev.blockfolk.repository.ShopRepository;
import dev.blockfolk.runtime.NpcShopService;
import dev.blockfolk.runtime.ShopEconomy;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;

/**
 * Settings of one trade: money and stock. Opened from the trade editor; every
 * change is saved at once.
 */
public final class ShopSettingsGuiService implements Listener {

    /** Trade settings: Money, Trade, and Stock columns. */
    private static final int OFFER_SIZE = 45;
    private static final int MONEY_HEADER_SLOT = 1;
    private static final int PRICE_SLOT = 10;
    private static final int PAYOUT_SLOT = 19;
    private static final int ECONOMY_SLOT = 28;
    private static final int TRADE_HEADER_SLOT = 4;
    private static final int TRADE_PREVIEW_SLOT = 13;
    private static final int PAGE_INFO_SLOT = 22;
    private static final int STOCK_HEADER_SLOT = 7;
    private static final int STOCK_LIMIT_SLOT = 16;
    private static final int STOCK_SCOPE_SLOT = 25;
    private static final int STOCK_INFO_SLOT = 34;
    private static final int OFFER_BACK_SLOT = 40;
    private static final int OFFER_HELP_SLOT = 42;

    private static final int MONEY_STEP = 1;
    private static final int STOCK_STEP = 1;

    private final ShopRepository shops;
    private final NpcShopService shopService;
    private final ChatInputService input;

    private interface Holder extends InventoryHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private record OfferHolder(String shopKey, String offerId, Consumer<Player> back) implements Holder {
    }

    public ShopSettingsGuiService(ShopRepository shops, NpcShopService shopService, ChatInputService input) {
        this.shops = shops;
        this.shopService = shopService;
        this.input = input;
    }

    // ---------------------------------------------------------------- trade

    public void openOffer(Player player, String shopKey, String offerId, Consumer<Player> back) {
        Shop shop = shops.find(shopKey).orElse(null);
        ShopOffer offer = shop == null ? null : shop.findOffer(offerId).orElse(null);
        if (offer == null) {
            player.sendMessage(UiText.warning("That trade no longer exists."));
            back.accept(player);
            return;
        }
        OfferTerms terms = offer.terms();
        ShopEconomy economy = shopService.economy();
        Inventory inventory = Bukkit.createInventory(new OfferHolder(shopKey, offerId, back), OFFER_SIZE,
                UiText.title("Trade Settings", shop.name()));

        inventory.setItem(MONEY_HEADER_SLOT, GuiItems.sectionHeader(Material.YELLOW_STAINED_GLASS_PANE, LegacyText.GOLD,
                "Money", "What the player pays and receives"));
        inventory.setItem(PRICE_SLOT,
                numeric(Material.GOLD_INGOT, "Price: " + money(economy, terms.price()),
                        List.of("Money the player pays per trade", "Combined with any cost items",
                                LegacyText.DARK_GRAY + "0 = no money price", "Step: " + MONEY_STEP)));
        inventory.setItem(PAYOUT_SLOT,
                numeric(Material.GOLD_NUGGET, "Payout: " + money(economy, terms.payout()),
                        List.of("Money the player receives per trade", "Leave the result empty to buy items",
                                "from players, e.g. 16 wheat for 5 coins", LegacyText.DARK_GRAY + "0 = no payout",
                                "Step: " + MONEY_STEP)));
        inventory.setItem(ECONOMY_SLOT, economyItem(economy));

        inventory.setItem(TRADE_HEADER_SLOT, GuiItems.sectionHeader(Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                LegacyText.AQUA, "Trade", "What changes hands"));
        inventory.setItem(TRADE_PREVIEW_SLOT, tradePreview(offer, economy));
        inventory.setItem(PAGE_INFO_SLOT, item(Material.BOOK, "Page " + (offer.page() + 1),
                shop.onePagePerDay()
                        ? List.of("On sale on the days this page", "is shown, one page per day")
                        : List.of("On sale every day, like every", "other page of this shop")));

        inventory.setItem(STOCK_HEADER_SLOT, GuiItems.sectionHeader(Material.LIME_STAINED_GLASS_PANE, LegacyText.GREEN,
                "Stock", "How often it can be bought"));
        inventory.setItem(STOCK_LIMIT_SLOT, numeric(Material.BARREL,
                "Stock: " + (terms.limited() ? terms.stockLimit() : "Unlimited"),
                List.of("Trades available per Minecraft day", LegacyText.DARK_GRAY + "0 = unlimited",
                        "Restocks at every sunrise", "Step: " + STOCK_STEP)));
        List<GuiItems.Option> scopes = Arrays.stream(StockScope.values())
                .map(value -> new GuiItems.Option(value.icon(), value.displayName(), value.summary())).toList();
        inventory.setItem(STOCK_SCOPE_SLOT, GuiItems.cycle("Stock Applies", scopes, terms.stockScope().ordinal(),
                terms.limited() ? List.of() : List.of(LegacyText.DARK_GRAY + "Unused: the stock is unlimited")));
        List<String> stockInfo = new ArrayList<>();
        stockInfo.add("Sold since the last restock: " + LegacyText.WHITE + shopService.soldSinceRestock(shop, offer));
        if (terms.limited() && terms.stockScope() == StockScope.SHARED)
            stockInfo.add("Left: " + LegacyText.WHITE + shopService.remaining(shop, offer, player.getUniqueId()));
        inventory.setItem(STOCK_INFO_SLOT, item(Material.PAPER, "Sales", stockInfo));

        inventory.setItem(OFFER_BACK_SLOT, GuiItems.back("the trade editor"));
        inventory.setItem(OFFER_HELP_SLOT,
                item(Material.BOOK, "How Trade Settings Work",
                        List.of("Players pay the cost items and the price,", "and receive the result and the payout.",
                                "Money and stock limits open the shop in", "the Blockfolk shop menu instead of the",
                                "villager trading screen.", "", "Items trade exactly as placed, so a renamed",
                                "or enchanted item can be sold or bought.")));
        GuiLayout.fillRow(inventory, 0);
        GuiLayout.fillColumn(inventory, 3);
        GuiLayout.fillColumn(inventory, 5);
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void onOfferClick(InventoryClickEvent event, Player player, OfferHolder holder) {
        Shop shop = shops.find(holder.shopKey()).orElse(null);
        ShopOffer offer = shop == null ? null : shop.findOffer(holder.offerId()).orElse(null);
        if (offer == null) {
            player.sendMessage(UiText.warning("That trade no longer exists."));
            holder.back().accept(player);
            return;
        }
        OfferTerms terms = offer.terms();
        Runnable reopen = () -> openOffer(player, holder.shopKey(), holder.offerId(), holder.back());
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        switch (slot) {
            case PRICE_SLOT, PAYOUT_SLOT -> {
                boolean price = slot == PRICE_SLOT;
                if (click == ClickType.MIDDLE) {
                    requestMoney(player, holder, price);
                    return;
                }
                int direction = NumericControl.direction(click);
                if (direction == 0)
                    return;
                double current = price ? terms.price() : terms.payout();
                double next = Math.max(0, current + direction * MONEY_STEP * (event.isShiftClick() ? 5 : 1));
                saveTerms(shop, offer, price ? terms.withPrice(next) : terms.withPayout(next));
            }
            case STOCK_LIMIT_SLOT -> {
                if (click == ClickType.MIDDLE) {
                    requestNumber(player,
                            "Type how many trades are available per day (0 = unlimited), or 'cancel':",
                            OfferTerms.MAX_STOCK, value -> withOffer(holder,
                                    (current, currentOffer) -> currentOffer.terms().withStockLimit(value)),
                            reopen);
                    return;
                }
                int direction = NumericControl.direction(click);
                if (direction == 0)
                    return;
                saveTerms(shop, offer, terms.withStockLimit(
                        Math.max(0, terms.stockLimit() + direction * STOCK_STEP * (event.isShiftClick() ? 5 : 1))));
            }
            case STOCK_SCOPE_SLOT -> {
                int direction = GuiItems.cycleDirection(click);
                if (direction == 0)
                    return;
                saveTerms(shop, offer, terms.withStockScope(GuiItems.step(terms.stockScope(), direction)));
            }
            case OFFER_BACK_SLOT -> {
                holder.back().accept(player);
                return;
            }
            default -> {
                return;
            }
        }
        GuiItems.click(player);
        reopen.run();
    }

    private void saveTerms(Shop shop, ShopOffer offer, OfferTerms terms) {
        shops.save(shop.withOffer(offer.withTerms(terms)));
    }

    /** Re-reads the trade after chat input, which may arrive much later. */
    private void withOffer(OfferHolder holder, java.util.function.BiFunction<Shop, ShopOffer, OfferTerms> change) {
        shops.find(holder.shopKey()).ifPresent(shop -> shop.findOffer(holder.offerId())
                .ifPresent(offer -> saveTerms(shop, offer, change.apply(shop, offer))));
    }

    private void requestMoney(Player player, OfferHolder holder, boolean price) {
        Runnable reopen = () -> openOffer(player, holder.shopKey(), holder.offerId(), holder.back());
        input.request(player,
                "Type the " + (price ? "price" : "payout") + " per trade, such as 12.50 (0 = none), or 'cancel':",
                value -> {
                    double amount = ShopEconomy.parse(value);
                    if (amount < 0 || amount > OfferTerms.MAX_MONEY) {
                        player.sendMessage(UiText
                                .error("Use a number from 0 to " + ShopEconomy.plain(OfferTerms.MAX_MONEY) + "."));
                    } else {
                        withOffer(holder, (shop,
                                offer) -> price ? offer.terms().withPrice(amount) : offer.terms().withPayout(amount));
                    }
                    reopen.run();
                }, reopen);
    }

    private ItemStack economyItem(ShopEconomy economy) {
        return economy.available()
                ? item(Material.EMERALD_BLOCK, "Economy: Connected",
                        List.of("Provider: " + LegacyText.WHITE + economy.providerName(),
                                "Money is paid and received through Vault"))
                : item(Material.REDSTONE_BLOCK, "Economy: Not Connected",
                        List.of(LegacyText.RED + "Money trades cannot be used", "Install Vault and an economy plugin,",
                                "such as EssentialsX, to trade for money",
                                "Found: " + LegacyText.WHITE + economy.providerName()));
    }

    private static ItemStack tradePreview(ShopOffer offer, ShopEconomy economy) {
        ItemStack icon = offer.result() != null ? offer.result() : offer.cost();
        List<String> lore = new ArrayList<>();
        lore.add("Player gives:");
        if (offer.terms().price() > 0)
            lore.add(LegacyText.WHITE + "• " + economy.format(offer.terms().price()));
        for (ItemStack cost : ShopStorefront.combinedCosts(offer))
            lore.add(LegacyText.WHITE + "• " + cost.getAmount() + " × " + LegacyText.plainText(cost.effectiveName()));
        lore.add("Player gets:");
        if (offer.result() != null)
            lore.add(LegacyText.WHITE + "• " + offer.result().getAmount() + " × "
                    + LegacyText.plainText(offer.result().effectiveName()));
        if (offer.terms().payout() > 0)
            lore.add(LegacyText.WHITE + "• " + economy.format(offer.terms().payout()));
        if (!offer.isValid())
            lore.add(LegacyText.RED + "Incomplete: needs something to give and to get");
        lore.add(LegacyText.DARK_GRAY + "Change the items in the trade editor");
        return GuiItems.item(icon == null ? new ItemStack(Material.BARRIER) : icon, LegacyText.GOLD + "Trade",
                gray(lore));
    }

    // --------------------------------------------------------------- shared

    private void requestNumber(Player player, String prompt, int max, java.util.function.IntConsumer apply,
            Runnable reopen) {
        input.request(player, prompt, value -> {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed < 0 || parsed > max)
                    player.sendMessage(UiText.error("Use a whole number from 0 to " + max + "."));
                else
                    apply.accept(parsed);
            } catch (NumberFormatException exception) {
                player.sendMessage(UiText.error("Use a whole number from 0 to " + max + "."));
            }
            reopen.run();
        }, reopen);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)
                || !(event.getWhoClicked() instanceof Player player))
            return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize())
            return;
        if (holder instanceof OfferHolder offer)
            onOfferClick(event, player, offer);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    private static String money(ShopEconomy economy, double amount) {
        return amount <= 0 ? "None" : economy.format(amount);
    }

    private static ItemStack numeric(Material material, String name, List<String> description) {
        List<String> lore = new ArrayList<>(description);
        lore.addAll(NumericControl.lore());
        return item(material, name, lore);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        return GuiItems.item(material, name, gray(lore));
    }

    /** Lore lines default to gray unless they start with their own color. */
    private static List<String> gray(List<String> lore) {
        return lore.stream().map(line -> line.isEmpty() || line.startsWith("§") ? line : LegacyText.GRAY + line)
                .toList();
    }
}
