package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.repository.ShopRepository;
import dev.blockfolk.runtime.NpcShopService;
import dev.blockfolk.runtime.ShopEconomy;
import dev.blockfolk.runtime.ShopTrade;
import dev.blockfolk.util.Durations;
import dev.blockfolk.util.UiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The Blockfolk shop menu that players trade in when a shop uses money or stock
 * limits. Every trade shows its real item (name, enchantments, and lore
 * included) followed by what it costs, what it pays, and how much stock is
 * left, colored by whether the player can afford it. Clicking trades once;
 * shift-clicking repeats the trade up to a stack.
 */
public final class ShopStorefront implements Listener, NpcShopService.Storefront {

    static final int OFFERS_PER_PAGE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BALANCE_SLOT = 47;
    private static final int HELP_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int RESTOCK_SLOT = 50;
    private static final int NEXT_SLOT = 53;
    /** Shift-clicked sales repeat at most this often, like selling a full stack. */
    private static final int MAX_REPEATED_SALES = 64;
    private static final TextColor LABEL = NamedTextColor.GRAY;
    private static final TextColor VALUE = NamedTextColor.WHITE;
    private static final TextColor SHORT = NamedTextColor.RED;

    private final ShopRepository shops;
    private final NpcShopService service;

    private static final class Holder implements InventoryHolder {
        private final String shopKey;
        private int page;
        private final List<String> slotOffers = new ArrayList<>();

        private Holder(String shopKey, int page) {
            this.shopKey = shopKey;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public ShopStorefront(ShopRepository shops, NpcShopService service) {
        this.shops = shops;
        this.service = service;
    }

    @Override
    public void open(Player player, Shop shop) {
        Holder holder = new Holder(shop.key(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(shop.name()));
        fill(player, inventory, holder, shop);
        player.openInventory(inventory);
    }

    @Override
    public void refresh(String shopKey) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder && holder.shopKey.equals(shopKey)) {
                Shop shop = shops.find(shopKey).orElse(null);
                if (shop == null) {
                    player.closeInventory();
                    continue;
                }
                player.sendMessage(UiText.info("The shop has new stock."));
                fill(player, top, holder, shop);
            }
        }
    }

    @Override
    public Set<String> viewedShops() {
        Set<String> keys = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)
                keys.add(holder.shopKey);
        }
        return keys;
    }

    @Override
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof Holder holder))
                continue;
            ItemStack restock = shops.find(holder.shopKey).map(this::restockItem).orElse(null);
            if (restock != null)
                top.setItem(RESTOCK_SLOT, restock);
        }
    }

    private void fill(Player player, Inventory inventory, Holder holder, Shop shop) {
        inventory.clear();
        List<ShopOffer> offers = service.assortment(shop).offers();
        int pages = Math.max(1, (offers.size() + OFFERS_PER_PAGE - 1) / OFFERS_PER_PAGE);
        holder.page = Math.clamp(holder.page, 0, pages - 1);
        holder.slotOffers.clear();
        int first = holder.page * OFFERS_PER_PAGE;
        for (int index = first; index < Math.min(offers.size(), first + OFFERS_PER_PAGE); index++) {
            ShopOffer offer = offers.get(index);
            holder.slotOffers.add(offer.id());
            inventory.setItem(index - first, offerIcon(player, shop, offer));
        }
        if (holder.page > 0)
            inventory.setItem(PREVIOUS_SLOT, GuiItems.previousPage(holder.page, pages));
        if (holder.page + 1 < pages)
            inventory.setItem(NEXT_SLOT, GuiItems.nextPage(holder.page, pages));
        ShopEconomy economy = service.economy();
        if (economy.available() && offers.stream().anyMatch(offer -> offer.terms().usesMoney()))
            inventory.setItem(BALANCE_SLOT, item(Material.GOLD_INGOT, "Your Balance",
                    List.of(line("Balance: ", economy.format(economy.balance(player)), VALUE))));
        inventory.setItem(HELP_SLOT,
                item(Material.BOOK, "How to Trade",
                        List.of(Component.text("Left-click: trade once", NamedTextColor.YELLOW),
                                Component.text("Shift-click: repeat, up to a stack", NamedTextColor.YELLOW),
                                Component.empty(), Component.text("Red lines show what you are missing.", LABEL),
                                Component.text("Items must match exactly, including", LABEL),
                                Component.text("their names and enchantments.", LABEL))));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", List.of()));
        inventory.setItem(RESTOCK_SLOT, restockItem(shop));
        GuiLayout.fillMainBar(inventory);
    }

    private ItemStack restockItem(Shop shop) {
        NpcShopService.Assortment assortment = service.assortment(shop);
        if (!assortment.refreshes())
            return null;
        long left = assortment.nextRefreshAt() - System.currentTimeMillis();
        List<Component> lore = new ArrayList<>();
        lore.add(line("New stock in ", Durations.remaining(left), VALUE));
        lore.add(Component.text(shop.assortment().rotates()
                ? "Restocks sold-out trades and brings new ones"
                : "Restocks sold-out trades", LABEL));
        return item(Material.CLOCK, "Restock", lore);
    }

    /** The trade's real item with the terms appended to its own lore. */
    private ItemStack offerIcon(Player player, Shop shop, ShopOffer offer) {
        boolean selling = offer.result() == null;
        ItemStack icon = selling ? offer.cost() : offer.result();
        ShopEconomy economy = service.economy();
        List<Component> terms = new ArrayList<>();
        terms.add(Component.empty());
        if (selling)
            terms.add(Component.text("Sell to the shop", NamedTextColor.GOLD));
        boolean moneyMissing = offer.terms().usesMoney() && !economy.available();
        if (offer.terms().payout() > 0)
            terms.add(line(selling ? "You get: " : "You also get: ", economy.format(offer.terms().payout()),
                    NamedTextColor.GREEN));
        if (offer.terms().price() > 0) {
            boolean affordable = economy.available() && economy.has(player, offer.terms().price());
            terms.add(line("Price: ", economy.format(offer.terms().price()), affordable ? VALUE : SHORT));
        }
        for (ItemStack cost : combinedCosts(offer)) {
            int carried = NpcShopService.carried(player, cost);
            Component value = Component.text(cost.getAmount() + " × ", VALUE).append(cost.effectiveName());
            Component costLine = Component.text(selling ? "You give: " : "Cost: ", LABEL)
                    .append(value.color(carried >= cost.getAmount() ? VALUE : SHORT));
            if (carried < cost.getAmount())
                costLine = costLine.append(Component.text(" (you have " + carried + ")", SHORT));
            terms.add(costLine);
        }
        int remaining = service.remaining(shop, offer, player.getUniqueId());
        if (offer.terms().limited()) {
            String scope = offer.terms().stockScope() == dev.blockfolk.model.StockScope.PER_PLAYER ? " for you" : "";
            terms.add(remaining == 0
                    ? Component.text("Sold out" + scope + " until the next restock", SHORT)
                    : line("Stock: ", remaining + " left" + scope, VALUE));
        }
        terms.add(Component.empty());
        if (moneyMissing) {
            terms.add(Component.text("Unavailable: the server has no economy", SHORT));
        } else if (remaining > 0) {
            String verb = selling ? "sell" : "buy";
            terms.add(Component.text("Left-click: " + verb + " once", NamedTextColor.YELLOW));
            terms.add(Component.text("Shift-click: " + verb + " up to " + repeatLimit(offer) + " times",
                    NamedTextColor.YELLOW));
        }
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null)
            lore.addAll(meta.lore());
        terms.forEach(
                component -> lore.add(component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * The trade's item costs, merged into one stack when both costs are the same
     * item, so the player sees the total they need.
     */
    static List<ItemStack> combinedCosts(ShopOffer offer) {
        ItemStack first = offer.cost();
        ItemStack second = offer.secondCost();
        if (first == null)
            return second == null ? List.of() : List.of(second);
        if (second == null)
            return List.of(first);
        if (first.isSimilar(second)) {
            first.setAmount(first.getAmount() + second.getAmount());
            return List.of(first);
        }
        return List.of(first, second);
    }

    /** Shift-click trades: up to a full stack of the result, or 64 sales. */
    static int repeatLimit(ShopOffer offer) {
        ItemStack result = offer.result();
        if (result == null)
            return MAX_REPEATED_SALES;
        return Math.max(1, result.getMaxStackSize() / Math.max(1, result.getAmount()));
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
        Inventory inventory = event.getView().getTopInventory();
        Shop shop = shops.find(holder.shopKey).orElse(null);
        if (shop == null) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case CLOSE_SLOT -> player.closeInventory();
            case PREVIOUS_SLOT, NEXT_SLOT -> {
                holder.page += slot == NEXT_SLOT ? 1 : -1;
                fill(player, inventory, holder, shop);
            }
            default -> {
                if (slot < holder.slotOffers.size())
                    trade(player, inventory, holder, shop, holder.slotOffers.get(slot), event.isShiftClick());
            }
        }
    }

    private void trade(Player player, Inventory inventory, Holder holder, Shop shop, String offerId, boolean repeat) {
        ShopOffer clicked = shop.findOffer(offerId).orElse(null);
        int times = repeat && clicked != null ? repeatLimit(clicked) : 1;
        NpcShopService.TradeOutcome outcome = service.trade(player, shop, offerId, times);
        if (outcome.trades() > 0) {
            Component message = describe(outcome.offer(), outcome.trades(), service.economy());
            if (outcome.stopReason() != ShopTrade.Result.DONE)
                message = message.append(Component.text(" Stopped: " + reason(outcome.stopReason()), LABEL));
            player.sendMessage(UiText.success(message));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        } else {
            player.sendMessage(UiText.warning(capitalize(reason(outcome.stopReason()))));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
        }
        fill(player, inventory, holder, shop);
    }

    /** "You got 16 × Bread for 32 € and 1 × Emerald." */
    static Component describe(ShopOffer offer, int trades, ShopEconomy economy) {
        List<Component> received = new ArrayList<>();
        ItemStack result = offer.result();
        if (result != null)
            received.add(amount(result, trades));
        if (offer.terms().payout() > 0)
            received.add(Component.text(economy.format(offer.terms().payout() * trades)));
        List<Component> paid = new ArrayList<>();
        if (offer.terms().price() > 0)
            paid.add(Component.text(economy.format(offer.terms().price() * trades)));
        for (ItemStack cost : combinedCosts(offer))
            paid.add(amount(cost, trades));
        return Component.text("You got ").append(join(received)).append(Component.text(" for ")).append(join(paid))
                .append(Component.text("."));
    }

    private static Component amount(ItemStack item, int trades) {
        return Component.text((long) item.getAmount() * trades + " × ").append(item.effectiveName());
    }

    private static Component join(List<Component> parts) {
        Component joined = Component.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0)
                joined = joined.append(Component.text(index == parts.size() - 1 ? " and " : ", "));
            joined = joined.append(parts.get(index));
        }
        return joined;
    }

    static String reason(ShopTrade.Result result) {
        return switch (result) {
            case DONE -> "done.";
            case SOLD_OUT -> "this trade is sold out until the next restock.";
            case MISSING_ITEMS -> "you do not carry the items this trade costs.";
            case NOT_ENOUGH_MONEY -> "you cannot afford this trade.";
            case NO_ROOM -> "make room in your inventory first.";
            case NO_ECONOMY -> "money trades are unavailable because the server has no economy.";
            case PAYMENT_FAILED -> "the payment did not go through, so nothing was traded.";
            case UNAVAILABLE -> "this trade is no longer offered.";
        };
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    private static Component line(String label, String value, TextColor valueColor) {
        return Component.text(label, LABEL).append(Component.text(value, valueColor));
    }

    private static ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(component -> component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
