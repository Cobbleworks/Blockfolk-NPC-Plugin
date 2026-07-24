package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.CustomEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.repository.CustomEventRepository;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;
import net.kyori.adventure.text.Component;

public final class CustomEventGuiService implements Listener {
    private static final int PAGE_SIZE = 45;
    private final CustomEventRepository events;
    private final NpcDefinitionRepository definitions;
    private final ChatInputService chatInput;
    private final Consumer<Player> mainGuiOpener;
    private final NamespacedKey reorderEventKey;

    public CustomEventGuiService(JavaPlugin plugin, CustomEventRepository events, NpcDefinitionRepository definitions,
            ChatInputService chatInput, Consumer<Player> mainGuiOpener) {
        this.events = events;
        this.definitions = definitions;
        this.chatInput = chatInput;
        this.mainGuiOpener = mainGuiOpener;
        this.reorderEventKey = new NamespacedKey(plugin, "reorder-custom-event");
    }

    public void open(Player player) { open(player, "", 0); }

    public void createEvent(Player player, String folder, Consumer<CustomEvent> onCreated, Runnable onFailure) {
        String normalizedFolder = folder == null ? "" : folder;
        chatInput.request(player, "Enter the full custom event name (use / for groups):", value -> {
            try {
                String name = normalizedFolder.isEmpty() || value.contains("/")
                        ? value : normalizedFolder + "/" + value;
                CustomEvent event = new CustomEvent(name);
                if (events.find(event.getName()).isPresent()) {
                    player.sendMessage(UiText.error("A custom event with that name already exists."));
                    onFailure.run();
                    return;
                }
                events.save(event);
                onCreated.accept(event);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(UiText.error(exception.getMessage()));
                onFailure.run();
            }
        });
    }

    private void open(Player player, String folder, int requestedPage) {
        List<Entry> entries = entries(folder);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        String title = folder.isEmpty() ? "Custom Events" : "Events: " + folder;
        Inventory inventory = Bukkit.createInventory(new EventsHolder(folder, page), 54, UiText.title(title));
        int from = page * PAGE_SIZE;
        for (int index = from; index < Math.min(from + PAGE_SIZE, entries.size()); index++) {
            Entry entry = entries.get(index);
            inventory.setItem(index - from, entry.folder()
                    ? item(Material.CHEST, entry.label(), List.of(
                            LegacyText.GRAY + "" + entry.childCount() + " item(s)",
                            LegacyText.DARK_GRAY + entry.path(),
                            LegacyText.YELLOW + "Click to open"))
                    : eventItem(entry.event()));
        }
        inventory.setItem(45, item(folder.isEmpty() ? Material.PLAYER_HEAD : Material.ARROW,
                folder.isEmpty() ? "Manage NPCs" : "Up One Group", List.of()));
        if (page > 0) inventory.setItem(47, item(Material.ARROW, "Previous Page", List.of()));
        inventory.setItem(49, item(Material.BELL, "Event Overview", List.of(
                LegacyText.GRAY + "Defined events: " + LegacyText.WHITE + events.findAll().size(),
                LegacyText.GRAY + "Group: " + LegacyText.WHITE + (folder.isEmpty() ? "Root" : folder),
                LegacyText.YELLOW + "Click to reorder custom events")));
        inventory.setItem(51, item(Material.EMERALD, "Create Event", List.of(
                LegacyText.GRAY + "Use / in the name to create groups",
                LegacyText.YELLOW + "Click, then enter the full event name")));
        if (page + 1 < pages) inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void openReorder(Player player, String returnFolder, int returnPage) {
        List<String> names = events.findAll().stream().map(CustomEvent::getName).toList();
        openReorder(player, new ReorderEventsHolder(new ArrayList<>(names), returnFolder, returnPage), 0);
    }

    private void openReorder(Player player, ReorderEventsHolder holder, int requestedPage) {
        int pages = Math.max(1, (holder.keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(holder, 54, UiText.title("Reorder Custom Events"));
        renderReorder(inventory, holder);
        player.openInventory(inventory);
        ReorderSupport.restoreCursor(player, holder, this::reorderItem);
    }

    private void renderReorder(Inventory inventory, ReorderEventsHolder holder) {
        inventory.clear();
        int pages = Math.max(1, (holder.keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = holder.page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, holder.keys.size());
        for (int index = from; index < to; index++) {
            String name = holder.keys.get(index);
            if (name.equals(holder.selectedKey)) continue;
            CustomEvent event = events.find(name).orElse(null);
            if (event != null) inventory.setItem(index - from, reorderItem(event, index));
        }
        if (holder.page > 0) inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        inventory.setItem(48, item(Material.LIME_CONCRETE, "Save Order", List.of(
                LegacyText.GRAY + "Apply this order to the custom event browser")));
        inventory.setItem(50, item(Material.RED_CONCRETE, "Cancel", List.of(
                LegacyText.GRAY + "Discard all ordering changes")));
        if (holder.page + 1 < pages) inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        GuiLayout.fillMainBar(inventory);
    }

    private ItemStack reorderItem(CustomEvent event, int index) {
        ItemStack template = event.getIcon();
        if (template == null) template = new ItemStack(Material.BELL);
        ItemStack icon = item(template, event.getName().substring(event.getName().lastIndexOf('/') + 1), List.of(
                LegacyText.DARK_GRAY + event.getName(),
                LegacyText.GRAY + "Position: " + LegacyText.WHITE + (index + 1),
                LegacyText.YELLOW + "Pick up and drop to move"));
        ItemMeta meta = icon.getItemMeta();
        meta.getPersistentDataContainer().set(reorderEventKey, PersistentDataType.STRING, event.getName());
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack reorderItem(String name, int index) {
        return events.find(name).map(event -> reorderItem(event, index)).orElse(null);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder rawHolder = event.getView().getTopInventory().getHolder();
        if (rawHolder instanceof EventsHolder holder) {
            event.setCancelled(true);
            if (top(event)) handleEventsClick(event, player, holder);
        } else if (rawHolder instanceof ReorderEventsHolder holder) {
            event.setCancelled(true);
            handleReorderClick(event, player, holder);
        } else if (rawHolder instanceof DeleteHolder holder) {
            event.setCancelled(true);
            if (top(event)) handleDeleteClick(event, player, holder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EventsHolder
                || event.getView().getTopInventory().getHolder() instanceof ReorderEventsHolder
                || event.getView().getTopInventory().getHolder() instanceof DeleteHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ReorderEventsHolder) {
            ReorderSupport.clearCursor(event.getPlayer(), reorderEventKey);
        }
    }

    private void handleEventsClick(InventoryClickEvent click, Player player, EventsHolder holder) {
        int slot = click.getRawSlot();
        if (slot == 45) {
            if (holder.folder().isEmpty()) mainGuiOpener.accept(player);
            else open(player, parent(holder.folder()), 0);
            return;
        }
        if (slot == 47) { open(player, holder.folder(), holder.page() - 1); return; }
        if (slot == 49) { openReorder(player, holder.folder(), holder.page()); return; }
        if (slot == 51) { requestName(player, holder); return; }
        if (slot == 53) { open(player, holder.folder(), holder.page() + 1); return; }
        List<Entry> entries = entries(holder.folder());
        int index = holder.page() * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index < 0 || index >= entries.size()) return;
        Entry entry = entries.get(index);
        if (entry.folder()) { open(player, entry.path(), 0); return; }
        CustomEvent customEvent = entry.event();
        if (click.getClick() == ClickType.MIDDLE) {
            customEvent.setIcon(player.getInventory().getItemInMainHand());
            events.save(customEvent);
            player.sendMessage(UiText.info(customEvent.getIcon() == null ? "Event icon cleared." : "Event icon updated."));
            open(player, holder.folder(), holder.page());
        } else if (click.isRightClick() && click.isShiftClick()) {
            openDelete(player, customEvent, holder);
        } else if (click.isRightClick()) {
            chatInput.request(player, "Enter the event description (or 'clear'):", value -> {
                customEvent.setDescription(value.equalsIgnoreCase("clear") ? "" : value);
                events.save(customEvent);
                open(player, holder.folder(), holder.page());
            });
        }
    }

    private void handleReorderClick(InventoryClickEvent click, Player player, ReorderEventsHolder holder) {
        click.setCancelled(true);
        if (!top(click)) return;
        int slot = click.getRawSlot();
        if (slot == 45 && holder.page > 0) {
            openReorder(player, holder, holder.page - 1);
            return;
        }
        if (slot == 53 && (holder.page + 1) * PAGE_SIZE < holder.keys.size()) {
            openReorder(player, holder, holder.page + 1);
            return;
        }
        if (slot == 48) {
            ReorderSupport.clearSelection(player, holder, reorderEventKey);
            try {
                events.reorder(holder.keys);
                player.sendMessage(UiText.success("Custom event order saved."));
                open(player, holder.returnFolder, holder.returnPage);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(UiText.info(
                        "The custom event list changed while you were editing. Please reorder it again."));
                openReorder(player, holder.returnFolder, holder.returnPage);
            }
            return;
        }
        if (slot == 50) {
            ReorderSupport.clearSelection(player, holder, reorderEventKey);
            open(player, holder.returnFolder, holder.returnPage);
            return;
        }
        ReorderSupport.selectOrMove(click, player, holder, PAGE_SIZE, reorderEventKey,
                this::reorderItem, inventory -> renderReorder(inventory, holder));
    }

    private void requestName(Player player, EventsHolder holder) {
        createEvent(player, "", event -> open(player, parent(event.getName()), 0),
                () -> open(player, holder.folder(), holder.page()));
    }

    private void openDelete(Player player, CustomEvent event, EventsHolder back) {
        Inventory inventory = Bukkit.createInventory(new DeleteHolder(event.getName(), back.folder(), back.page()), 27,
                UiText.title("Delete Event", event.getName()));
        inventory.setItem(11, item(Material.LIME_CONCRETE, "Confirm", List.of(
                LegacyText.RED + "Permanently delete this event",
                LegacyText.GRAY + "NPC reactions to it will also be removed")));
        inventory.setItem(15, item(Material.RED_CONCRETE, "Cancel", List.of()));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void handleDeleteClick(InventoryClickEvent click, Player player, DeleteHolder holder) {
        if (click.getRawSlot() == 15) { open(player, holder.folder(), holder.page()); return; }
        if (click.getRawSlot() != 11) return;
        CustomEvent event = events.find(holder.eventName()).orElse(null);
        if (event != null) {
            for (NpcDefinition definition : definitions.findAll()) {
                if (!definition.getCustomEventActions(event.getName()).isEmpty()) {
                    definition.removeCustomEvent(event.getName());
                    definitions.save(definition);
                }
            }
            events.delete(event);
            player.sendMessage(UiText.success("Deleted custom event '" + event.getName() + "'."));
        }
        open(player, holder.folder(), holder.page());
    }

    private List<Entry> entries(String folder) {
        String prefix = folder.isEmpty() ? "" : folder + "/";
        Map<String, Entry> result = new LinkedHashMap<>();
        for (CustomEvent event : events.findAll()) {
            if (!event.getName().startsWith(prefix)) continue;
            String rest = event.getName().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String label = rest.substring(0, slash);
                String path = prefix + label;
                Entry old = result.get(path);
                result.put(path, new Entry(true, path, label, old == null ? 1 : old.childCount() + 1, null));
            } else result.put(event.getName(), new Entry(false, event.getName(), rest, 0, event));
        }
        return new ArrayList<>(result.values());
    }

    private ItemStack eventItem(CustomEvent event) {
        ItemStack template = event.getIcon();
        if (template == null) template = new ItemStack(Material.BELL);
        return item(template, event.getName().substring(event.getName().lastIndexOf('/') + 1), List.of(
                LegacyText.DARK_GRAY + event.getName(),
                LegacyText.GRAY + (event.getDescription().isBlank() ? "No description" : event.getDescription()),
                LegacyText.AQUA + "Middle-click: set icon from main hand",
                LegacyText.YELLOW + "Right-click: edit description",
                LegacyText.RED + "Shift-right-click: delete"));
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
    private static boolean top(InventoryClickEvent event) {
        return event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
    }
    private ItemStack item(Material material, String name, List<String> lore) { return item(new ItemStack(material), name, lore); }
    private ItemStack item(ItemStack template, String name, List<String> lore) {
        ItemStack item = template.clone(); item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyText.component(LegacyText.GOLD + name));
        meta.lore(LegacyText.components(lore));
        item.setItemMeta(meta);
        return item;
    }
    private record Entry(boolean folder, String path, String label, int childCount, CustomEvent event) { }
    private record EventsHolder(String folder, int page) implements GuiHolder { }
    private static final class ReorderEventsHolder extends ReorderSupport.ReorderState {
        private final String returnFolder;
        private final int returnPage;

        private ReorderEventsHolder(List<String> names, String returnFolder, int returnPage) {
            super(names);
            this.returnFolder = returnFolder;
            this.returnPage = returnPage;
        }
    }
    private record DeleteHolder(String eventName, String folder, int page) implements GuiHolder { }
}
