package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.OfferAvailability;
import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.StockScope;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.RouteRepository;
import dev.blockfolk.repository.ShopRepository;
import dev.blockfolk.runtime.NpcShopService;
import dev.blockfolk.runtime.ShopEconomy;
import dev.blockfolk.util.Durations;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;

/**
 * Shared shop library, shop picker for the Open Shop action, and trade editor.
 * Trades are edited with real items: each trade occupies a cost, an optional
 * second cost and a result slot. The arrow between them opens the trade's
 * settings (money, availability, stock), which stay with the trade while its
 * items change. Editor changes are saved on close, page change and every
 * button.
 */
public final class ShopGuiService implements Listener {
    static final int TRADES_PER_PAGE = 10;
    static final int MAX_PAGES = 5;
    private static final int LIST_PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int HELP_SLOT = 46;
    private static final int USAGE_SLOT = 47;
    private static final int TITLE_SLOT = 48;
    private static final int BACK_SLOT = 49;
    private static final int PREVIEW_SLOT = 50;
    private static final int SAVE_SLOT = 51;
    private static final int ASSORTMENT_SLOT = 52;
    private static final int NEXT_SLOT = 53;
    private static final int LIST_PREVIOUS_SLOT = 45;
    private static final int LIST_BACK_SLOT = 49;
    private static final int LIST_CREATE_SLOT = 51;
    private static final int LIST_NEXT_SLOT = 53;

    private final Plugin plugin;
    private final ShopRepository shops;
    private final NpcShopService shopService;
    private final ChatInputService input;
    private final NpcDefinitionRepository definitions;
    private final RouteRepository routes;
    private final Consumer<Player> mainMenu;
    private final Set<UUID> explicitSaves = new HashSet<>();
    private ShopSettingsGuiService settings;

    private interface Holder extends InventoryHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private record LibraryHolder(int page, Consumer<Player> back) implements Holder {
    }

    private record SelectHolder(int page, Consumer<String> select, Consumer<Player> back) implements Holder {
    }

    private record DeleteHolder(String key, Consumer<Player> back) implements Holder {
    }

    /**
     * {@code rowTerms} are the settings of the trades shown on this page, by row;
     * they belong to the row until the page is saved.
     */
    private record EditorHolder(String key, int page, int fingerprint, List<OfferTerms> rowTerms,
            Consumer<Player> back) implements Holder {
    }

    /** A saved page: the shop, and the trade of every row (empty rows included). */
    private record SavedPage(Shop shop, List<ShopOffer> rows) {
    }

    public ShopGuiService(Plugin plugin, ShopRepository shops, NpcShopService shopService, ChatInputService input,
            NpcDefinitionRepository definitions, RouteRepository routes, Consumer<Player> mainMenu) {
        this.plugin = plugin;
        this.shops = shops;
        this.shopService = shopService;
        this.input = input;
        this.definitions = definitions;
        this.routes = routes;
        this.mainMenu = mainMenu;
    }

    public void setSettings(ShopSettingsGuiService settings) {
        this.settings = settings;
    }

    /** Opens the shop library; Back returns to the main menu. */
    public void open(Player player) {
        openLibrary(player, mainMenu);
    }

    public void openLibrary(Player player, Consumer<Player> back) {
        library(player, 0, back);
    }

    /**
     * Lets the player pick a shop for an Open Shop action. Right-click edits a shop
     * and returns to the picker.
     */
    public void selectShop(Player player, Consumer<String> select, Consumer<Player> back) {
        selector(player, new SelectHolder(0, select, back));
    }

    /** Opens the trade editor of {@code key}, or {@code back} when it is gone. */
    public void edit(Player player, String key, Consumer<Player> back) {
        Shop shop = shops.find(key).orElse(null);
        if (shop == null) {
            player.sendMessage(UiText.error("Unknown shop: " + key));
            back.accept(player);
            return;
        }
        open(player, shop, 0, back);
    }

    /** Opens page {@code page} of the trade editor of {@code key}. */
    public void editPage(Player player, String key, int page, Consumer<Player> back) {
        shops.find(key).ifPresentOrElse(shop -> open(player, shop, page, back), () -> back.accept(player));
    }

    /** Asks for a name in chat and creates an empty shop with a unique key. */
    public void create(Player player, Consumer<Shop> created, Runnable cancelled) {
        input.request(player, "Enter a name for the new shop, or 'cancel':", name -> {
            if (Shop.normalizeKey(name).isEmpty()) {
                player.sendMessage(UiText.error("Use a name containing letters or numbers."));
                cancelled.run();
                return;
            }
            Shop shop = Shop.create(shops.uniqueKey(name), name);
            shops.save(shop);
            player.sendMessage(UiText.success("Created shop '" + shop.name() + "' (" + shop.key() + ")."));
            created.accept(shop);
        }, cancelled);
    }

    /** Display label for an Open Shop action value. */
    public String shopName(String key) {
        if (key == null)
            return "No shop selected";
        return shops.find(key).map(Shop::name).orElse(key + " (missing)");
    }

    /**
     * Display names of the NPCs and routes whose Open Shop actions use {@code key}.
     */
    public List<String> usage(String key) {
        String normalized = Shop.normalizeKey(key);
        List<String> users = new ArrayList<>();
        for (NpcDefinition definition : definitions.findAll()) {
            boolean[] used = {false};
            definition.forEachAction(action -> used[0] |= opensShop(action, normalized));
            if (used[0])
                users.add(definition.getDisplayName());
        }
        for (NpcRoute route : routes.findAll()) {
            boolean[] used = {false};
            route.forEachAction(action -> used[0] |= opensShop(action, normalized));
            if (used[0])
                users.add("Route " + route.getDisplayName());
        }
        if (sceneRepository != null) {
            for (dev.blockfolk.model.Scene scene : sceneRepository.findAll()) {
                boolean[] used = {false};
                scene.forEachAction(action -> used[0] |= opensShop(action, normalized));
                if (used[0])
                    users.add("Scene " + scene.getDisplayName());
            }
        }
        return users;
    }

    private dev.blockfolk.repository.SceneRepository sceneRepository;

    public void setSceneRepository(dev.blockfolk.repository.SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    private static boolean opensShop(BehaviourAction action, String key) {
        return action.type() == BehaviourActionType.OPEN_SHOP && action.value() != null
                && Shop.normalizeKey(action.value()).equals(key);
    }

    private void library(Player player, int requestedPage, Consumer<Player> back) {
        List<Shop> all = shops.findAll();
        int page = listPage(requestedPage, all.size());
        Inventory inventory = Bukkit.createInventory(new LibraryHolder(page, back), 54,
                UiText.title("Shops", "Shop Library"));
        for (int index = page * LIST_PAGE_SIZE; index < Math.min(all.size(), (page + 1) * LIST_PAGE_SIZE); index++) {
            Shop shop = all.get(index);
            List<String> lore = summary(shop);
            lore.add("");
            lore.add(GuiItems.hint("Left-click", "edit trades"));
            lore.add(GuiItems.hint("Right-click", "preview"));
            lore.add(GuiItems.hint("Shift-left-click", "duplicate"));
            lore.add(GuiItems.danger("Shift-right-click", "delete"));
            inventory.setItem(index % LIST_PAGE_SIZE, shopIcon(shop, lore));
        }
        if (all.isEmpty())
            inventory.setItem(22, GuiItems.empty("No Shops Yet", "Use Create Shop below to add one"));
        listFooter(inventory, page, all.size());
        inventory.setItem(HELP_SLOT,
                item(Material.BOOK, "How Shops Work",
                        List.of("Shops are shared between NPCs.",
                                "Give an NPC the " + LegacyText.WHITE + "Open Shop" + LegacyText.GRAY + " action",
                                "and choose a shop, e.g. on right-click.")));
        inventory.setItem(LIST_CREATE_SLOT, GuiItems.create("Create Shop", "Then name it and add trades"));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void selector(Player player, SelectHolder holder) {
        List<Shop> all = shops.findAll();
        int page = listPage(holder.page(), all.size());
        Inventory inventory = Bukkit.createInventory(new SelectHolder(page, holder.select(), holder.back()), 54,
                UiText.title("Open Shop", "Choose Shop"));
        for (int index = page * LIST_PAGE_SIZE; index < Math.min(all.size(), (page + 1) * LIST_PAGE_SIZE); index++) {
            Shop shop = all.get(index);
            List<String> lore = summary(shop);
            lore.add("");
            lore.add(GuiItems.hint("Left-click", "select"));
            lore.add(GuiItems.hint("Right-click", "edit trades"));
            inventory.setItem(index % LIST_PAGE_SIZE, shopIcon(shop, lore));
        }
        if (all.isEmpty())
            inventory.setItem(22, GuiItems.empty("No Shops Yet", "Use Create Shop below to add one"));
        listFooter(inventory, page, all.size());
        inventory.setItem(LIST_CREATE_SLOT, GuiItems.create("Create Shop", "Creates a shop and opens its trades"));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void confirmDelete(Player player, DeleteHolder holder) {
        Shop shop = shops.find(holder.key()).orElse(null);
        if (shop == null) {
            holder.back().accept(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(holder, 27, UiText.title("Delete Shop", shop.name()));
        List<String> users = usage(shop.key());
        List<String> lore = new ArrayList<>(List.of("Removes the shop and all its trades"));
        if (users.isEmpty()) {
            lore.add(LegacyText.GRAY + "No NPC uses this shop");
        } else {
            lore.add("Open Shop actions of these will stop working:");
            users.stream().limit(8).forEach(name -> lore.add(LegacyText.WHITE + "• " + name));
            if (users.size() > 8)
                lore.add(LegacyText.GRAY + "…and " + (users.size() - 8) + " more");
        }
        inventory.setItem(11, GuiItems.confirm(lore));
        inventory.setItem(13, shopIcon(shop, summary(shop)));
        inventory.setItem(15, GuiItems.cancel());
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void open(Player player, Shop shop, int requestedPage, Consumer<Player> back) {
        int page = Math.clamp(requestedPage, 0, pageCount(shop.offers().size()) - 1);
        List<ShopOffer> offers = shop.offers();
        List<OfferTerms> rowTerms = new ArrayList<>(TRADES_PER_PAGE);
        for (int index = 0; index < TRADES_PER_PAGE; index++) {
            int offerIndex = page * TRADES_PER_PAGE + index;
            rowTerms.add(offerIndex < offers.size() ? offers.get(offerIndex).terms() : OfferTerms.DEFAULT);
        }
        EditorHolder holder = new EditorHolder(shop.key(), page, fingerprint(shop), List.copyOf(rowTerms), back);
        Inventory inventory = Bukkit.createInventory(holder, 54, UiText.title("Shop", shop.name()));
        for (int index = 0; index < TRADES_PER_PAGE; index++) {
            int offerIndex = page * TRADES_PER_PAGE + index;
            int base = tradeBaseSlot(index);
            if (offerIndex < offers.size()) {
                ShopOffer offer = offers.get(offerIndex);
                inventory.setItem(base, offer.cost());
                inventory.setItem(base + 1, offer.secondCost());
                inventory.setItem(base + 3, offer.result());
            }
            if (index % 2 == 0)
                inventory.setItem(base + 4, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        }
        refreshArrows(inventory, shop, holder);
        inventory.setItem(BACK_SLOT, item(Material.BARRIER, "Back", List.of("Saves this page first")));
        inventory.setItem(HELP_SLOT, item(Material.BOOK, "How Shops Work",
                List.of("Place items into a trade row:", LegacyText.WHITE + "cost, second cost (optional), result",
                        "Click the arrow of a trade to set a money", "price or payout, a stock limit, and whether",
                        "it is offered or rotates.", "Items trade exactly as placed, including",
                        "names and enchantments.", "Players open the shop through the",
                        LegacyText.WHITE + "Open Shop" + LegacyText.GRAY + " behaviour action, e.g. on right-click.",
                        "Changes apply to every NPC using this shop.")));
        if (settings != null)
            inventory.setItem(ASSORTMENT_SLOT,
                    settings.rotationSummary(shop, List.of("", GuiItems.hint("Click", "rotation and restocking"))));
        List<String> users = usage(shop.key());
        List<String> usageLore = new ArrayList<>();
        usageLore.add("Key: " + LegacyText.WHITE + shop.key());
        if (users.isEmpty()) {
            usageLore.add("Not used by any Open Shop action yet.");
        } else {
            usageLore.add("Opened by:");
            users.stream().limit(8).forEach(name -> usageLore.add(LegacyText.WHITE + "• " + name));
            if (users.size() > 8)
                usageLore.add("…and " + (users.size() - 8) + " more");
        }
        inventory.setItem(USAGE_SLOT, item(Material.PLAYER_HEAD, "Used By " + users.size(), usageLore));
        inventory.setItem(TITLE_SLOT,
                item(Material.NAME_TAG, "Shop Name", List.of("Current: " + LegacyText.WHITE + shop.name(),
                        "Shown as the trading screen title", "", GuiItems.hint("Click", "rename"))));
        inventory.setItem(SAVE_SLOT, item(Material.WRITABLE_BOOK, "Save",
                List.of("Closing the menu also saves", "", GuiItems.hint("Click", "save now"))));
        inventory.setItem(PREVIEW_SLOT, item(Material.EMERALD, "Preview Shop", List.of(
                "Opens the shop as players see it",
                shop.needsShopMenu() ? "Uses the shop menu (money or stock)" : "Uses the villager trading screen",
                "Offered trades: " + LegacyText.WHITE + shop.offeredTrades().size(),
                LegacyText.GOLD + "Trades in the preview are real", "", GuiItems.hint("Click", "preview"))));
        if (page > 0)
            inventory.setItem(PREVIOUS_SLOT,
                    item(Material.ARROW, "Previous Page", List.of("Page " + page + " of " + MAX_PAGES)));
        if (page + 1 < MAX_PAGES)
            inventory.setItem(NEXT_SLOT,
                    item(Material.ARROW, "Next Page", List.of("Page " + (page + 2) + " of " + MAX_PAGES,
                            "Opens once this page holds " + TRADES_PER_PAGE + " trades")));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof Holder holder))
            return;
        if (holder instanceof EditorHolder editor) {
            onEditorClick(event, player, editor);
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize())
            return;
        if (holder instanceof LibraryHolder library)
            onLibraryClick(event, player, library, slot);
        else if (holder instanceof SelectHolder select)
            onSelectClick(event, player, select, slot);
        else if (holder instanceof DeleteHolder delete) {
            if (slot == 11) {
                shops.find(delete.key()).ifPresent(shop -> {
                    shops.delete(shop.key());
                    shopService.forget(shop.key());
                    player.sendMessage(UiText.success("Deleted shop '" + shop.name() + "'."));
                });
                delete.back().accept(player);
            } else if (slot == 15)
                delete.back().accept(player);
        }
    }

    private void onLibraryClick(InventoryClickEvent event, Player player, LibraryHolder holder, int slot) {
        Consumer<Player> reopen = p -> library(p, holder.page(), holder.back());
        List<Shop> all = shops.findAll();
        int index = holder.page() * LIST_PAGE_SIZE + slot;
        if (slot < LIST_PAGE_SIZE) {
            if (index >= all.size())
                return;
            Shop shop = all.get(index);
            if (event.isShiftClick() && event.isRightClick())
                confirmDelete(player, new DeleteHolder(shop.key(), reopen));
            else if (event.isShiftClick() && event.isLeftClick())
                duplicate(player, shop, reopen);
            else if (event.isRightClick())
                shopService.open(player, shop);
            else if (event.isLeftClick())
                open(player, shop, 0, reopen);
            return;
        }
        switch (slot) {
            case LIST_PREVIOUS_SLOT -> library(player, holder.page() - 1, holder.back());
            case LIST_NEXT_SLOT -> library(player, holder.page() + 1, holder.back());
            case LIST_BACK_SLOT -> holder.back().accept(player);
            case LIST_CREATE_SLOT -> create(player, shop -> open(player, shop, 0, reopen), () -> reopen.accept(player));
            default -> {
            }
        }
    }

    private void onSelectClick(InventoryClickEvent event, Player player, SelectHolder holder, int slot) {
        Consumer<Player> reopen = p -> selector(p, holder);
        List<Shop> all = shops.findAll();
        int index = holder.page() * LIST_PAGE_SIZE + slot;
        if (slot < LIST_PAGE_SIZE) {
            if (index >= all.size())
                return;
            Shop shop = all.get(index);
            if (event.isRightClick())
                open(player, shop, 0, reopen);
            else if (event.isLeftClick()) {
                player.sendMessage(UiText.success("Selected shop '" + shop.name() + "'."));
                holder.select().accept(shop.key());
            }
            return;
        }
        switch (slot) {
            case LIST_PREVIOUS_SLOT ->
                selector(player, new SelectHolder(holder.page() - 1, holder.select(), holder.back()));
            case LIST_NEXT_SLOT ->
                selector(player, new SelectHolder(holder.page() + 1, holder.select(), holder.back()));
            case LIST_BACK_SLOT -> holder.back().accept(player);
            case LIST_CREATE_SLOT -> create(player, shop -> open(player, shop, 0, reopen), () -> reopen.accept(player));
            default -> {
            }
        }
    }

    private void duplicate(Player player, Shop source, Consumer<Player> back) {
        String name = source.name() + " (copy)";
        Shop copy = source.copyAs(shops.uniqueKey(source.key()), name);
        shops.save(copy);
        player.sendMessage(UiText.success("Duplicated '" + source.name() + "' as '" + copy.name() + "'."));
        back.accept(player);
    }

    private void onEditorClick(InventoryClickEvent event, Player player, EditorHolder holder) {
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize())
            return;
        if (isItemSlot(slot)) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshArrows(top, null, holder));
            return;
        }
        event.setCancelled(true);
        int arrowRow = arrowRow(slot);
        if (arrowRow >= 0) {
            openTradeSettings(player, top, holder, arrowRow);
            return;
        }
        switch (slot) {
            case ASSORTMENT_SLOT -> {
                Shop shop = persist(player, top, holder);
                if (shop != null && settings != null) {
                    explicitSaves.add(player.getUniqueId());
                    settings.openAssortment(player, shop.key(),
                            p -> editPage(p, shop.key(), holder.page(), holder.back()));
                }
            }
            case BACK_SLOT -> {
                if (persist(player, top, holder) != null) {
                    explicitSaves.add(player.getUniqueId());
                    holder.back().accept(player);
                }
            }
            case TITLE_SLOT -> {
                Shop shop = persist(player, top, holder);
                if (shop != null) {
                    explicitSaves.add(player.getUniqueId());
                    requestName(player, shop.key(), holder);
                }
            }
            case SAVE_SLOT -> {
                Shop shop = persist(player, top, holder);
                if (shop != null) {
                    player.sendMessage(UiText.success("Shop " + shop.name() + " saved."));
                    reopen(player, shop, holder.page(), holder.back());
                }
            }
            case PREVIEW_SLOT -> {
                Shop shop = persist(player, top, holder);
                if (shop == null)
                    return;
                if (shop.offeredTrades().isEmpty()) {
                    player.sendMessage(UiText.warning("Add at least one complete, offered trade first."));
                    reopen(player, shop, holder.page(), holder.back());
                    return;
                }
                explicitSaves.add(player.getUniqueId());
                shopService.open(player, shop);
            }
            case PREVIOUS_SLOT, NEXT_SLOT -> {
                int target = holder.page() + (slot == NEXT_SLOT ? 1 : -1);
                if (target < 0 || target >= MAX_PAGES)
                    return;
                Shop shop = persist(player, top, holder);
                if (shop == null)
                    return;
                if (target >= pageCount(shop.offers().size()))
                    player.sendMessage(UiText.warning(
                            "Fill all " + TRADES_PER_PAGE + " trades on this page before adding another page."));
                reopen(player, shop, target, holder.back());
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder))
            return;
        if (!(holder instanceof EditorHolder)) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesLockedSlot = event.getRawSlots().stream().anyMatch(slot -> slot < topSize && !isItemSlot(slot));
        if (touchesLockedSlot) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        EditorHolder editor = (EditorHolder) holder;
        Bukkit.getScheduler().runTask(plugin, () -> refreshArrows(top, null, editor));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EditorHolder holder)
                || !(event.getPlayer() instanceof Player player))
            return;
        if (explicitSaves.remove(player.getUniqueId()))
            return;
        persist(player, event.getInventory(), holder);
    }

    private void requestName(Player player, String key, EditorHolder holder) {
        Runnable reopen = () -> shops.find(key).ifPresentOrElse(
                current -> open(player, current, holder.page(), holder.back()), () -> holder.back().accept(player));
        input.request(player, "Type the shop name shown as the trading screen title, or 'cancel'.", value -> {
            shops.find(key).ifPresent(current -> {
                shops.save(current.withName(value));
                player.sendMessage(UiText.success("Shop renamed."));
            });
            reopen.run();
        }, reopen);
    }

    private void reopen(Player player, Shop shop, int page, Consumer<Player> back) {
        explicitSaves.add(player.getUniqueId());
        open(player, shop, page, back);
    }

    /**
     * Writes the visible page back into the shop. Returns the saved shop, or
     * {@code null} when the shop is gone or was changed elsewhere while this menu
     * was open (the stale copy is then discarded).
     */
    private Shop persist(Player player, Inventory inventory, EditorHolder holder) {
        SavedPage saved = persistPage(player, inventory, holder);
        return saved == null ? null : saved.shop();
    }

    /**
     * Saves the visible page like {@link #persist} and also returns the trade of
     * every row, so a row can be mapped to its trade id. Rows keep the settings and
     * id of the trade they showed when the page opened.
     */
    private SavedPage persistPage(Player player, Inventory inventory, EditorHolder holder) {
        Shop shop = shops.find(holder.key()).orElse(null);
        if (shop == null) {
            explicitSaves.add(player.getUniqueId());
            player.closeInventory();
            return null;
        }
        if (holder.fingerprint() != fingerprint(shop)) {
            player.sendMessage(UiText.warning(
                    "The shop changed while you were editing; your stale copy was not saved. Reopen it to continue."));
            explicitSaves.add(player.getUniqueId());
            player.closeInventory();
            return null;
        }
        List<ShopOffer> existing = shop.offers();
        List<ShopOffer> page = new ArrayList<>();
        for (int index = 0; index < TRADES_PER_PAGE; index++) {
            int base = tradeBaseSlot(index);
            int offerIndex = holder.page() * TRADES_PER_PAGE + index;
            ShopOffer previous = offerIndex < existing.size() ? existing.get(offerIndex) : null;
            ItemStack cost = inventory.getItem(base);
            ItemStack secondCost = inventory.getItem(base + 1);
            ItemStack result = inventory.getItem(base + 3);
            page.add(previous == null
                    ? new ShopOffer(cost, secondCost, result)
                    : previous.withItems(cost, secondCost, result));
        }
        Shop updated = shop.withOffers(replacePage(existing, holder.page(), page));
        shops.save(updated);
        return new SavedPage(updated, page);
    }

    /** Saves the page, then opens the settings of the trade in {@code row}. */
    private void openTradeSettings(Player player, Inventory top, EditorHolder holder, int row) {
        SavedPage saved = persistPage(player, top, holder);
        if (saved == null)
            return;
        ShopOffer offer = saved.rows().get(row);
        if (offer.isEmpty() || settings == null) {
            player.sendMessage(UiText.warning("Place the trade's items first, then click its arrow."));
            reopen(player, saved.shop(), holder.page(), holder.back());
            return;
        }
        explicitSaves.add(player.getUniqueId());
        String key = saved.shop().key();
        settings.openOffer(player, key, offer.id(), p -> editPage(p, key, holder.page(), holder.back()));
    }

    /**
     * The trade row whose arrow is at {@code slot}, or -1. Arrows sit between the
     * costs and the result, in columns 2 and 7.
     */
    static int arrowRow(int slot) {
        if (slot < 0 || slot >= TRADES_PER_PAGE / 2 * 9)
            return -1;
        int column = slot % 9;
        if (column != 2 && column != 7)
            return -1;
        return slot / 9 * 2 + (column == 7 ? 1 : 0);
    }

    /**
     * Replaces the trades shown on {@code page} with {@code edited}, keeping the
     * trades of every other page. Empty trades are dropped later by {@link Shop},
     * which compacts the list.
     */
    static <T> List<T> replacePage(List<T> offers, int page, List<T> edited) {
        int from = Math.min(offers.size(), page * TRADES_PER_PAGE);
        int to = Math.min(offers.size(), from + TRADES_PER_PAGE);
        List<T> result = new ArrayList<>(offers.subList(0, from));
        result.addAll(edited);
        result.addAll(offers.subList(to, offers.size()));
        return result;
    }

    /** Pages needed to show every trade plus room for a new one. */
    static int pageCount(int offerCount) {
        return Math.clamp(offerCount / TRADES_PER_PAGE + 1, 1, MAX_PAGES);
    }

    /**
     * First slot (cost) of the trade at {@code index} on a page: two trades per
     * row, five rows.
     */
    static int tradeBaseSlot(int index) {
        return (index / 2) * 9 + (index % 2) * 5;
    }

    /** Whether the slot holds a cost, second cost or result item. */
    static boolean isItemSlot(int slot) {
        if (slot < 0 || slot >= TRADES_PER_PAGE / 2 * 9)
            return false;
        int column = slot % 9;
        return column == 0 || column == 1 || column == 3 || column == 5 || column == 6 || column == 8;
    }

    static int listPage(int requested, int size) {
        return Math.clamp(requested, 0, Math.max(0, (size - 1) / LIST_PAGE_SIZE));
    }

    private static int fingerprint(Shop shop) {
        return shop.offers().hashCode();
    }

    private List<String> summary(Shop shop) {
        int users = usage(shop.key()).size();
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.DARK_GRAY + "Key: " + shop.key());
        lore.add("Offered trades: " + LegacyText.WHITE + shop.offeredTrades().size());
        if (shop.assortment().rotates())
            lore.add("Rotation: " + LegacyText.WHITE + shop.assortment().rotatingSlots() + " of "
                    + shop.rotationPool().size());
        if (shop.assortment().refreshes())
            lore.add("Restock: " + LegacyText.WHITE + "every " + Durations.minutes(shop.assortment().refreshMinutes()));
        if (shop.needsShopMenu())
            lore.add("Screen: " + LegacyText.WHITE + "shop menu (money or stock)");
        lore.add("Used by: " + LegacyText.WHITE + users + (users == 1 ? " NPC or route" : " NPCs or routes"));
        return lore;
    }

    /** The first trade's result represents the shop; empty shops use an emerald. */
    private static ItemStack shopIcon(Shop shop, List<String> lore) {
        ItemStack icon = shop.validOffers().stream().findFirst().map(ShopOffer::result)
                .orElseGet(() -> new ItemStack(Material.EMERALD));
        return GuiItems.item(icon, LegacyText.GOLD + shop.name(), gray(lore));
    }

    private static void listFooter(Inventory inventory, int page, int size) {
        int pages = Math.max(1, (size + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        if (page > 0)
            inventory.setItem(LIST_PREVIOUS_SLOT, GuiItems.previousPage(page, pages));
        inventory.setItem(LIST_BACK_SLOT, GuiItems.back(""));
        if ((page + 1) * LIST_PAGE_SIZE < size)
            inventory.setItem(LIST_NEXT_SLOT, GuiItems.nextPage(page, pages));
    }

    /**
     * Colors each trade's arrow by its state and lists its settings. {@code shop}
     * may be null while items are being moved; the row settings come from the
     * holder either way.
     */
    private void refreshArrows(Inventory inventory, Shop shop, EditorHolder holder) {
        Shop current = shop != null ? shop : shops.find(holder.key()).orElse(null);
        ShopEconomy economy = shopService.economy();
        boolean rotates = current != null && current.assortment().rotates();
        for (int index = 0; index < TRADES_PER_PAGE; index++) {
            int base = tradeBaseSlot(index);
            ShopOffer offer = new ShopOffer(null, inventory.getItem(base), inventory.getItem(base + 1),
                    inventory.getItem(base + 3), holder.rowTerms().get(index));
            inventory.setItem(base + 2, arrow(offer, economy, rotates));
        }
    }

    static Material arrowMaterial(ShopOffer offer) {
        if (offer.isEmpty())
            return Material.GRAY_STAINED_GLASS_PANE;
        if (!offer.isValid())
            return Material.RED_STAINED_GLASS_PANE;
        return switch (offer.terms().availability()) {
            case OFFERED -> Material.LIME_STAINED_GLASS_PANE;
            case ALWAYS -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case HIDDEN -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
        };
    }

    private static ItemStack arrow(ShopOffer offer, ShopEconomy economy, boolean rotates) {
        if (offer.isEmpty())
            return item(Material.GRAY_STAINED_GLASS_PANE, "→ Empty Trade",
                    List.of("Place a cost item on the left", "and the result on the right.", "",
                            LegacyText.DARK_GRAY + "Then click here for money, stock",
                            LegacyText.DARK_GRAY + "and availability."));
        OfferTerms terms = offer.terms();
        List<String> lore = new ArrayList<>();
        String name;
        if (!offer.isValid()) {
            name = "→ Incomplete Trade";
            lore.add(LegacyText.RED + "Ignored by the shop until the player");
            lore.add(LegacyText.RED + "gives something (an item or a price)");
            lore.add(LegacyText.RED + "and gets something (an item or a payout).");
        } else {
            name = switch (terms.availability()) {
                case OFFERED -> rotates ? "→ Offered, Rotates" : "→ Trade Ready";
                case ALWAYS -> "→ Always Offered";
                case HIDDEN -> "→ Not Offered";
            };
        }
        if (terms.price() > 0)
            lore.add("Price: " + LegacyText.WHITE + economy.format(terms.price()));
        if (terms.payout() > 0)
            lore.add("Payout: " + LegacyText.WHITE + economy.format(terms.payout()));
        if (terms.limited())
            lore.add("Stock: " + LegacyText.WHITE + terms.stockLimit() + " per restock"
                    + (terms.stockScope() == StockScope.PER_PLAYER ? ", per player" : ""));
        if (rotates && terms.availability() == OfferAvailability.OFFERED)
            lore.add("Draw weight: " + LegacyText.WHITE + terms.weight());
        lore.add("");
        lore.add(GuiItems.hint("Click", "money, stock and availability"));
        return item(arrowMaterial(offer), name, lore);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        return GuiItems.item(material, name, gray(lore));
    }

    /** Lore lines default to gray unless they start with their own color. */
    private static List<String> gray(List<String> lore) {
        return lore.stream().map(line -> line.isEmpty() || line.startsWith("\u00a7") ? line : LegacyText.GRAY + line)
                .toList();
    }
}
