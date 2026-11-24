package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

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
import dev.blockfolk.model.OfferAvailability;
import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopAssortment;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.StockScope;
import dev.blockfolk.repository.ShopRepository;
import dev.blockfolk.runtime.NpcShopService;
import dev.blockfolk.runtime.ShopEconomy;
import dev.blockfolk.util.Durations;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;

/**
 * Settings of one trade (money, whether it is offered, stock) and of a whole
 * shop's assortment (rotation and restocking). Opened from the trade editor;
 * every change is saved at once.
 */
public final class ShopSettingsGuiService implements Listener {

    /** Trade settings: Money, Assortment, and Stock columns. */
    private static final int OFFER_SIZE = 45;
    private static final int MONEY_HEADER_SLOT = 1;
    private static final int PRICE_SLOT = 10;
    private static final int PAYOUT_SLOT = 19;
    private static final int ECONOMY_SLOT = 28;
    private static final int OFFER_ASSORTMENT_HEADER_SLOT = 4;
    private static final int AVAILABILITY_SLOT = 13;
    private static final int WEIGHT_SLOT = 22;
    private static final int ROTATION_INFO_SLOT = 31;
    private static final int STOCK_HEADER_SLOT = 7;
    private static final int STOCK_LIMIT_SLOT = 16;
    private static final int STOCK_SCOPE_SLOT = 25;
    private static final int STOCK_INFO_SLOT = 34;
    private static final int TRADE_PREVIEW_SLOT = 38;
    private static final int OFFER_BACK_SLOT = 40;
    private static final int OFFER_HELP_SLOT = 42;

    /** Shop assortment: Rotation and Restock columns. */
    private static final int ASSORTMENT_SIZE = 45;
    private static final int ROTATION_HEADER_SLOT = 2;
    private static final int ROTATING_SLOTS_SLOT = 11;
    private static final int POOL_SLOT = 20;
    private static final int SHOWING_SLOT = 29;
    private static final int RESTOCK_HEADER_SLOT = 6;
    private static final int INTERVAL_SLOT = 15;
    private static final int RESTOCK_NOW_SLOT = 24;
    private static final int NEXT_RESTOCK_SLOT = 33;
    private static final int ASSORTMENT_HELP_SLOT = 38;
    private static final int ASSORTMENT_BACK_SLOT = 40;

    private static final int MONEY_STEP = 1;
    private static final int WEIGHT_STEP = 1;
    private static final int STOCK_STEP = 1;
    private static final int SLOT_STEP = 1;
    private static final int INTERVAL_STEP_MINUTES = 5;

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

    private record AssortmentHolder(String shopKey, Consumer<Player> back) implements Holder {
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

        inventory.setItem(OFFER_ASSORTMENT_HEADER_SLOT, GuiItems.sectionHeader(Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                LegacyText.AQUA, "Assortment", "Whether players see this trade"));
        List<GuiItems.Option> availabilities = Arrays.stream(OfferAvailability.values())
                .map(value -> new GuiItems.Option(value.icon(), value.displayName(), value.summary())).toList();
        inventory.setItem(AVAILABILITY_SLOT, GuiItems.cycle("Availability", availabilities,
                terms.availability().ordinal(), List.of("Not Offered keeps the trade here", "without selling it")));
        int poolWeight = shop.rotationPool().stream().mapToInt(candidate -> candidate.terms().weight()).sum();
        List<String> weightLore = new ArrayList<>();
        weightLore.add("Chance to be drawn when the shop rotates;");
        weightLore.add("a trade with weight 20 comes up about");
        weightLore.add("twice as often as one with weight 10");
        if (terms.availability() == OfferAvailability.OFFERED && poolWeight > 0)
            weightLore.add("Share of the pool: " + LegacyText.WHITE + percent(terms.weight(), poolWeight));
        if (!shop.assortment().rotates())
            weightLore.add(LegacyText.DARK_GRAY + "Unused: this shop does not rotate");
        else if (terms.availability() != OfferAvailability.OFFERED)
            weightLore.add(LegacyText.DARK_GRAY + "Unused: only Offered trades are drawn");
        weightLore.add("Range: " + OfferTerms.MIN_WEIGHT + "-" + OfferTerms.MAX_WEIGHT);
        inventory.setItem(WEIGHT_SLOT, numeric(Material.AMETHYST_SHARD, "Draw Weight: " + terms.weight(), weightLore));
        inventory.setItem(ROTATION_INFO_SLOT, rotationSummary(shop, List.of("", GuiItems.hint("Click", "change"))));

        inventory.setItem(STOCK_HEADER_SLOT, GuiItems.sectionHeader(Material.LIME_STAINED_GLASS_PANE, LegacyText.GREEN,
                "Stock", "How often it can be bought"));
        inventory.setItem(STOCK_LIMIT_SLOT, numeric(Material.BARREL,
                "Stock: " + (terms.limited() ? terms.stockLimit() : "Unlimited"),
                List.of("Trades available until the next restock", LegacyText.DARK_GRAY + "0 = unlimited",
                        shop.assortment().refreshes()
                                ? "Restocks every " + Durations.minutes(shop.assortment().refreshMinutes())
                                : LegacyText.GOLD + "This shop never restocks on its own",
                        "Step: " + STOCK_STEP)));
        List<GuiItems.Option> scopes = Arrays.stream(StockScope.values())
                .map(value -> new GuiItems.Option(value.icon(), value.displayName(), value.summary())).toList();
        inventory.setItem(STOCK_SCOPE_SLOT, GuiItems.cycle("Stock Applies", scopes, terms.stockScope().ordinal(),
                terms.limited() ? List.of() : List.of(LegacyText.DARK_GRAY + "Unused: the stock is unlimited")));
        List<String> stockInfo = new ArrayList<>();
        stockInfo.add("Sold since the last restock: " + LegacyText.WHITE + shopService.soldThisPeriod(shop, offer));
        if (terms.limited() && terms.stockScope() == StockScope.SHARED)
            stockInfo.add("Left: " + LegacyText.WHITE + shopService.remaining(shop, offer, player.getUniqueId()));
        inventory.setItem(STOCK_INFO_SLOT, item(Material.PAPER, "Sales", stockInfo));

        inventory.setItem(TRADE_PREVIEW_SLOT, tradePreview(offer, economy));
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
            case AVAILABILITY_SLOT -> {
                int direction = GuiItems.cycleDirection(click);
                if (direction == 0)
                    return;
                saveTerms(shop, offer, terms.withAvailability(GuiItems.step(terms.availability(), direction)));
            }
            case WEIGHT_SLOT -> {
                if (click == ClickType.MIDDLE) {
                    requestNumber(player,
                            "Type the draw weight (" + OfferTerms.MIN_WEIGHT + "-" + OfferTerms.MAX_WEIGHT
                                    + "), or 'cancel':",
                            OfferTerms.MAX_WEIGHT, value -> withOffer(holder,
                                    (current, currentOffer) -> currentOffer.terms().withWeight(value)),
                            reopen);
                    return;
                }
                int direction = NumericControl.direction(click);
                if (direction == 0)
                    return;
                saveTerms(shop, offer,
                        terms.withWeight(terms.weight() + direction * WEIGHT_STEP * (event.isShiftClick() ? 5 : 1)));
            }
            case STOCK_LIMIT_SLOT -> {
                if (click == ClickType.MIDDLE) {
                    requestNumber(player,
                            "Type how many trades are available per restock (0 = unlimited), or 'cancel':",
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
            case ROTATION_INFO_SLOT -> {
                openAssortment(player, holder.shopKey(), reopenPlayer -> reopen.run());
                return;
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

    // ----------------------------------------------------------- assortment

    public void openAssortment(Player player, String shopKey, Consumer<Player> back) {
        Shop shop = shops.find(shopKey).orElse(null);
        if (shop == null) {
            player.sendMessage(UiText.warning("That shop no longer exists."));
            back.accept(player);
            return;
        }
        ShopAssortment assortment = shop.assortment();
        NpcShopService.Assortment current = shopService.assortment(shop);
        Inventory inventory = Bukkit.createInventory(new AssortmentHolder(shopKey, back), ASSORTMENT_SIZE,
                UiText.title("Assortment", shop.name()));

        inventory.setItem(ROTATION_HEADER_SLOT, GuiItems.sectionHeader(Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                LegacyText.AQUA, "Rotation", "Which trades are on sale"));
        int pool = shop.rotationPool().size();
        inventory.setItem(ROTATING_SLOTS_SLOT, numeric(Material.ITEM_FRAME,
                "Rotating Trades: " + (assortment.rotates() ? assortment.rotatingSlots() : "Off"),
                List.of("How many Offered trades are drawn at", "random for each stock period",
                        LegacyText.DARK_GRAY + "0 = rotation off: every offered trade",
                        LegacyText.DARK_GRAY + "is on sale", "Always Offered trades are added on top",
                        assortment.rotates() && assortment.rotatingSlots() >= pool && pool > 0
                                ? LegacyText.GOLD + "All " + pool + " Offered trades fit; nothing rotates"
                                : "Pool: " + LegacyText.WHITE + pool + " Offered trades",
                        "Step: " + SLOT_STEP)));
        long always = shop.offeredTrades().stream()
                .filter(offer -> offer.terms().availability() == OfferAvailability.ALWAYS).count();
        long hidden = shop.validOffers().stream()
                .filter(offer -> offer.terms().availability() == OfferAvailability.HIDDEN).count();
        inventory.setItem(POOL_SLOT,
                item(Material.BOOKSHELF, "Trades",
                        List.of("Offered: " + LegacyText.WHITE + pool, "Always Offered: " + LegacyText.WHITE + always,
                                "Not Offered: " + LegacyText.WHITE + hidden,
                                "Incomplete: " + LegacyText.WHITE + (shop.offers().size() - shop.validOffers().size()),
                                "", LegacyText.DARK_GRAY + "Click a trade's arrow in the editor",
                                LegacyText.DARK_GRAY + "to change its availability")));
        inventory.setItem(SHOWING_SLOT, item(Material.SPYGLASS, "On Sale Now",
                List.of("Trades players see: " + LegacyText.WHITE + current.offers().size())));

        inventory.setItem(RESTOCK_HEADER_SLOT, GuiItems.sectionHeader(Material.LIME_STAINED_GLASS_PANE,
                LegacyText.GREEN, "Restock", "When stock and rotation renew"));
        inventory.setItem(INTERVAL_SLOT, numeric(Material.CLOCK,
                "Restock Every: " + (assortment.refreshes() ? Durations.minutes(assortment.refreshMinutes()) : "Never"),
                List.of("Refills limited trades and, when the shop", "rotates, draws a new set of trades",
                        "Real time, also while the server is off",
                        LegacyText.DARK_GRAY + "0 = only when restocked by hand",
                        "Step: " + INTERVAL_STEP_MINUTES + " min",
                        LegacyText.DARK_GRAY + "Typed values accept 90, 1h30m or 2d")));
        inventory.setItem(RESTOCK_NOW_SLOT,
                item(Material.BELL, "Restock Now",
                        List.of("Refills every limited trade and draws", "a new rotation immediately",
                                "The next restock follows one interval later", "", GuiItems.hint("Click", "restock"))));
        inventory
                .setItem(NEXT_RESTOCK_SLOT,
                        item(Material.RECOVERY_COMPASS, "Next Restock",
                                List.of(current.refreshes()
                                        ? "In " + LegacyText.WHITE
                                                + Durations
                                                        .remaining(current.nextRefreshAt() - System.currentTimeMillis())
                                        : "Never (no restock interval)")));

        inventory.setItem(ASSORTMENT_HELP_SLOT,
                item(Material.BOOK, "How the Assortment Works",
                        List.of("Example: 30 Offered trades, 10 rotating,", "restock every 1 h. Players see 10 random",
                                "trades, and an hour later 10 others.", "Trades are drawn by weight, and trades from",
                                "the last rotation are avoided when possible.",
                                "Every NPC using this shop sees the same", "assortment and shares its stock.")));
        inventory.setItem(ASSORTMENT_BACK_SLOT, GuiItems.back(""));
        GuiLayout.fillRow(inventory, 0);
        GuiLayout.fillColumn(inventory, 4);
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void onAssortmentClick(InventoryClickEvent event, Player player, AssortmentHolder holder) {
        Shop shop = shops.find(holder.shopKey()).orElse(null);
        if (shop == null) {
            holder.back().accept(player);
            return;
        }
        Runnable reopen = () -> openAssortment(player, holder.shopKey(), holder.back());
        ClickType click = event.getClick();
        switch (event.getRawSlot()) {
            case ROTATING_SLOTS_SLOT -> {
                if (click == ClickType.MIDDLE) {
                    requestNumber(player, "Type how many trades rotate (0 = rotation off), or 'cancel':",
                            ShopAssortment.MAX_ROTATING_SLOTS,
                            value -> changeAssortment(holder, current -> current.withRotatingSlots(value)), reopen);
                    return;
                }
                int direction = NumericControl.direction(click);
                if (direction == 0)
                    return;
                changeAssortment(holder, current -> current.withRotatingSlots(
                        current.rotatingSlots() + direction * SLOT_STEP * (event.isShiftClick() ? 5 : 1)));
            }
            case INTERVAL_SLOT -> {
                if (click == ClickType.MIDDLE) {
                    requestInterval(player, holder, reopen);
                    return;
                }
                int direction = NumericControl.direction(click);
                if (direction == 0)
                    return;
                changeAssortment(holder, current -> current.withRefreshMinutes(
                        current.refreshMinutes() + direction * INTERVAL_STEP_MINUTES * (event.isShiftClick() ? 5 : 1)));
            }
            case RESTOCK_NOW_SLOT -> {
                shopService.restockNow(shop);
                player.sendMessage(UiText.success("Restocked " + shop.name() + "."));
            }
            case ASSORTMENT_BACK_SLOT -> {
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

    private void changeAssortment(AssortmentHolder holder, Function<ShopAssortment, ShopAssortment> change) {
        shops.find(holder.shopKey()).ifPresent(shop -> {
            Shop updated = shop.withAssortment(change.apply(shop.assortment()));
            shops.save(updated);
            // Draws or trims the rotation right away so the menu shows the result.
            shopService.assortment(updated);
        });
    }

    private void requestInterval(Player player, AssortmentHolder holder, Runnable reopen) {
        input.request(player, "Type the restock interval, such as 60, 90m, 1h30m or 2d (0 = never), or 'cancel':",
                value -> {
                    int minutes = Durations.parseMinutes(value);
                    if (minutes < 0 || minutes > ShopAssortment.MAX_REFRESH_MINUTES)
                        player.sendMessage(UiText.error("Use a duration from 0 to 30 days, such as 90m or 1h30m."));
                    else
                        changeAssortment(holder, current -> current.withRefreshMinutes(minutes));
                    reopen.run();
                }, reopen);
    }

    // --------------------------------------------------------------- shared

    /** Rotation and restock summary used by the trade editor and trade settings. */
    ItemStack rotationSummary(Shop shop, List<String> extra) {
        ShopAssortment assortment = shop.assortment();
        List<String> lore = new ArrayList<>();
        lore.add("Rotation: " + LegacyText.WHITE
                + (assortment.rotates()
                        ? assortment.rotatingSlots() + " of " + shop.rotationPool().size() + " Offered trades"
                        : "off, every offered trade is on sale"));
        lore.add("Restock: " + LegacyText.WHITE
                + (assortment.refreshes() ? "every " + Durations.minutes(assortment.refreshMinutes()) : "never"));
        lore.addAll(extra);
        return item(Material.CLOCK, "Assortment & Restock", lore);
    }

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
        else if (holder instanceof AssortmentHolder assortment)
            onAssortmentClick(event, player, assortment);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    static String percent(int part, int whole) {
        double share = whole <= 0 ? 0 : part * 100.0 / whole;
        return share >= 10 || share == 0
                ? Math.round(share) + "%"
                : String.format(java.util.Locale.ROOT, "%.1f%%", share);
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
