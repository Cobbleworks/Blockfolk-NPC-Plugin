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
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.ShopProfile;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.runtime.NpcShopService;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;

/**
 * Admin editor for an NPC preset's shop. Trades are edited with real items:
 * each trade occupies a cost, an optional second cost and a result slot.
 * Changes are saved on close, page change and every button.
 */
public final class ShopGuiService implements Listener {
    static final int TRADES_PER_PAGE = 10;
    static final int MAX_PAGES = 5;
    private static final int BACK_SLOT = 45;
    private static final int HELP_SLOT = 46;
    private static final int ENABLED_SLOT = 47;
    private static final int TITLE_SLOT = 48;
    private static final int SAVE_SLOT = 49;
    private static final int PREVIEW_SLOT = 50;
    private static final int PREVIOUS_SLOT = 51;
    private static final int NEXT_SLOT = 53;

    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcShopService shopService;
    private final ChatInputService input;
    private final Set<UUID> explicitSaves = new HashSet<>();

    private interface Holder extends InventoryHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private record EditorHolder(String key, int page, int fingerprint, Consumer<Player> back) implements Holder {
    }

    public ShopGuiService(Plugin plugin, NpcDefinitionRepository definitions, NpcShopService shopService,
            ChatInputService input) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.shopService = shopService;
        this.input = input;
    }

    public void open(Player player, NpcDefinition definition, Consumer<Player> back) {
        open(player, definition, 0, back);
    }

    private void open(Player player, NpcDefinition definition, int requestedPage, Consumer<Player> back) {
        ShopProfile shop = definition.getShopProfile();
        int page = Math.clamp(requestedPage, 0, pageCount(shop.offers().size()) - 1);
        EditorHolder holder = new EditorHolder(definition.getKey(), page, fingerprint(definition), back);
        Inventory inventory = Bukkit.createInventory(holder, 54, UiText.title("Shop", definition.getDisplayName()));
        List<ShopOffer> offers = shop.offers();
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
        refreshArrows(inventory);
        inventory.setItem(BACK_SLOT, item(Material.BARRIER, "Back", List.of()));
        inventory.setItem(HELP_SLOT, item(Material.BOOK, "How Shops Work",
                List.of("Place items into a trade row:", LegacyText.WHITE + "cost, second cost (optional), result",
                        "Trades have unlimited stock.", "Players open the shop through the",
                        LegacyText.WHITE + "Open Shop" + LegacyText.GRAY + " behaviour action, e.g. on right-click.")));
        inventory.setItem(ENABLED_SLOT,
                item(shop.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, "Shop: " + (shop.enabled() ? "On" : "Off"),
                        List.of("Complete trades: " + LegacyText.WHITE + shop.validOffers().size(),
                                LegacyText.YELLOW + "Click to toggle")));
        inventory.setItem(TITLE_SLOT,
                item(Material.NAME_TAG, "Shop Title",
                        List.of("Current: " + LegacyText.WHITE
                                + (shop.title() == null ? definition.getDisplayName() + " (NPC name)" : shop.title()),
                                LegacyText.YELLOW + "Click to rename")));
        inventory.setItem(SAVE_SLOT, item(Material.WRITABLE_BOOK, "Save", List.of("Closing the menu also saves.")));
        inventory.setItem(PREVIEW_SLOT,
                item(Material.EMERALD, "Preview Shop", List.of("Opens the trading screen as players see it.")));
        if (page > 0)
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "Previous Page", List.of()));
        if (page + 1 < MAX_PAGES)
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "Next Page",
                    List.of("Page " + (page + 2), "Opens once this page holds " + TRADES_PER_PAGE + " trades.")));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof EditorHolder holder))
            return;
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
            Bukkit.getScheduler().runTask(plugin, () -> refreshArrows(top));
            return;
        }
        event.setCancelled(true);
        switch (slot) {
            case BACK_SLOT -> {
                if (persist(player, top, holder) != null) {
                    explicitSaves.add(player.getUniqueId());
                    holder.back().accept(player);
                }
            }
            case ENABLED_SLOT -> {
                NpcDefinition definition = persist(player, top, holder);
                if (definition != null) {
                    ShopProfile shop = definition.getShopProfile();
                    definition.setShopProfile(shop.withEnabled(!shop.enabled()));
                    definitions.save(definition);
                    reopen(player, definition, holder.page(), holder.back());
                }
            }
            case TITLE_SLOT -> {
                NpcDefinition definition = persist(player, top, holder);
                if (definition != null) {
                    explicitSaves.add(player.getUniqueId());
                    requestTitle(player, definition.getKey(), holder);
                }
            }
            case SAVE_SLOT -> {
                NpcDefinition definition = persist(player, top, holder);
                if (definition != null) {
                    player.sendMessage(UiText.success("Shop of " + definition.getDisplayName() + " saved."));
                    reopen(player, definition, holder.page(), holder.back());
                }
            }
            case PREVIEW_SLOT -> {
                NpcDefinition definition = persist(player, top, holder);
                if (definition == null)
                    return;
                if (definition.getShopProfile().validOffers().isEmpty()) {
                    player.sendMessage(UiText.warning("Add at least one trade with a cost and a result first."));
                    reopen(player, definition, holder.page(), holder.back());
                    return;
                }
                explicitSaves.add(player.getUniqueId());
                shopService.open(player, definition, true);
            }
            case PREVIOUS_SLOT, NEXT_SLOT -> {
                int target = holder.page() + (slot == NEXT_SLOT ? 1 : -1);
                if (target < 0 || target >= MAX_PAGES)
                    return;
                NpcDefinition definition = persist(player, top, holder);
                if (definition == null)
                    return;
                if (target >= pageCount(definition.getShopProfile().offers().size()))
                    player.sendMessage(UiText.warning(
                            "Fill all " + TRADES_PER_PAGE + " trades on this page before adding another page."));
                reopen(player, definition, target, holder.back());
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorHolder))
            return;
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesLockedSlot = event.getRawSlots().stream().anyMatch(slot -> slot < topSize && !isItemSlot(slot));
        if (touchesLockedSlot) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> refreshArrows(top));
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

    private void requestTitle(Player player, String key, EditorHolder holder) {
        Runnable reopen = () -> definitions.find(key)
                .ifPresentOrElse(current -> open(player, current, holder.page(), holder.back()), () -> {
                });
        input.request(player, "Type the shop title, 'reset' to use the NPC name, or 'cancel'.", value -> {
            definitions.find(key).ifPresent(current -> {
                String title = value.equalsIgnoreCase("reset") ? null : value;
                current.setShopProfile(current.getShopProfile().withTitle(title));
                definitions.save(current);
                player.sendMessage(UiText.success("Shop title updated."));
            });
            reopen.run();
        }, reopen);
    }

    private void reopen(Player player, NpcDefinition definition, int page, Consumer<Player> back) {
        explicitSaves.add(player.getUniqueId());
        open(player, definition, page, back);
    }

    /**
     * Writes the visible page back into the preset. Returns the saved definition,
     * or {@code null} when the preset is gone or was changed elsewhere while this
     * menu was open (the stale copy is then discarded).
     */
    private NpcDefinition persist(Player player, Inventory inventory, EditorHolder holder) {
        NpcDefinition definition = definitions.find(holder.key()).orElse(null);
        if (definition == null) {
            explicitSaves.add(player.getUniqueId());
            player.closeInventory();
            return null;
        }
        if (holder.fingerprint() != fingerprint(definition)) {
            player.sendMessage(UiText.warning(
                    "The shop changed while you were editing; your stale copy was not saved. Reopen it to continue."));
            explicitSaves.add(player.getUniqueId());
            player.closeInventory();
            return null;
        }
        List<ShopOffer> page = new ArrayList<>();
        for (int index = 0; index < TRADES_PER_PAGE; index++) {
            int base = tradeBaseSlot(index);
            page.add(new ShopOffer(inventory.getItem(base), inventory.getItem(base + 1), inventory.getItem(base + 3)));
        }
        ShopProfile shop = definition.getShopProfile();
        definition.setShopProfile(shop.withOffers(replacePage(shop.offers(), holder.page(), page)));
        definitions.save(definition);
        return definition;
    }

    /**
     * Replaces the trades shown on {@code page} with {@code edited}, keeping the
     * trades of every other page. Empty trades are dropped later by
     * {@link ShopProfile}, which compacts the list.
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

    private static int fingerprint(NpcDefinition definition) {
        return definition.getShopProfile().offers().hashCode();
    }

    private static void refreshArrows(Inventory inventory) {
        for (int index = 0; index < TRADES_PER_PAGE; index++) {
            int base = tradeBaseSlot(index);
            ShopOffer offer = new ShopOffer(inventory.getItem(base), inventory.getItem(base + 1),
                    inventory.getItem(base + 3));
            ItemStack arrow;
            if (offer.isEmpty()) {
                arrow = item(Material.GRAY_STAINED_GLASS_PANE, "→ Empty Trade",
                        List.of("Place a cost item on the left", "and the result on the right."));
            } else if (offer.isValid()) {
                arrow = item(Material.LIME_STAINED_GLASS_PANE, "→ Trade Ready", List.of());
            } else {
                arrow = item(Material.RED_STAINED_GLASS_PANE, "→ Incomplete Trade",
                        List.of(LegacyText.RED + "Ignored by the shop until it has",
                                LegacyText.RED + "a first cost and a result."));
            }
            inventory.setItem(base + 2, arrow);
        }
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(LegacyText.component(LegacyText.WHITE + name));
        meta.lore(lore.stream().map(line -> LegacyText.component(LegacyText.GRAY + line)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
