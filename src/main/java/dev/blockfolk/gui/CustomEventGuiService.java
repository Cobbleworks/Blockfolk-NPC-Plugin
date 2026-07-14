package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
                    player.sendMessage(Component.text("A custom event with that name already exists."));
                    onFailure.run();
                    return;
                }
                events.save(event);
                onCreated.accept(event);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(Component.text(exception.getMessage()));
                onFailure.run();
            }
        });
    }

    private void open(Player player, String folder, int requestedPage) {
        List<Entry> entries = entries(folder);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        String title = folder.isEmpty() ? "Custom Events" : "Events: " + folder;
        Inventory inventory = Bukkit.createInventory(new EventsHolder(folder, page), 54, Component.text(title));
        int from = page * PAGE_SIZE;
        for (int index = from; index < Math.min(from + PAGE_SIZE, entries.size()); index++) {
            Entry entry = entries.get(index);
            inventory.setItem(index - from, entry.folder()
                    ? item(Material.CHEST, entry.label(), List.of(
                            ChatColor.GRAY + "" + entry.childCount() + " item(s)",
                            ChatColor.DARK_GRAY + entry.path(),
                            ChatColor.YELLOW + "Click to open"))
                    : eventItem(entry.event()));
        }
        inventory.setItem(45, item(folder.isEmpty() ? Material.PLAYER_HEAD : Material.ARROW,
                folder.isEmpty() ? "Manage NPCs" : "Up One Group", List.of()));
        if (page > 0) inventory.setItem(47, item(Material.ARROW, "Previous Page", List.of()));
        inventory.setItem(49, item(Material.BELL, "Event Overview", List.of(
                ChatColor.GRAY + "Defined events: " + ChatColor.WHITE + events.findAll().size(),
                ChatColor.GRAY + "Group: " + ChatColor.WHITE + (folder.isEmpty() ? "Root" : folder),
                ChatColor.YELLOW + "Click to reorder custom events")));
        inventory.setItem(51, item(Material.EMERALD, "Create Event", List.of(
                ChatColor.GRAY + "Use / in the name to create groups",
                ChatColor.YELLOW + "Click, then enter the full event name")));
        if (page + 1 < pages) inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void openReorder(Player player, String returnFolder, int returnPage) {
        List<String> names = events.findAll().stream().map(CustomEvent::getName).toList();
        openReorder(player, new ReorderEventsHolder(new ArrayList<>(names), returnFolder, returnPage), 0);
    }

    private void openReorder(Player player, ReorderEventsHolder holder, int requestedPage) {
        int pages = Math.max(1, (holder.names.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Reorder Custom Events"));
        renderReorder(inventory, holder);
        player.openInventory(inventory);
        restoreReorderCursor(player, holder);
    }

    private void renderReorder(Inventory inventory, ReorderEventsHolder holder) {
        inventory.clear();
        int pages = Math.max(1, (holder.names.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = holder.page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, holder.names.size());
        for (int index = from; index < to; index++) {
            String name = holder.names.get(index);
            if (name.equals(holder.selectedName)) continue;
            CustomEvent event = events.find(name).orElse(null);
            if (event != null) inventory.setItem(index - from, reorderItem(event, index));
        }
        if (holder.page > 0) inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        inventory.setItem(48, item(Material.LIME_CONCRETE, "Save Order", List.of(
                ChatColor.GRAY + "Apply this order to the custom event browser")));
        inventory.setItem(50, item(Material.RED_CONCRETE, "Cancel", List.of(
                ChatColor.GRAY + "Discard all ordering changes")));
        if (holder.page + 1 < pages) inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        GuiLayout.fillMainBar(inventory);
    }

    private ItemStack reorderItem(CustomEvent event, int index) {
        ItemStack template = event.getIcon();
        if (template == null) template = new ItemStack(Material.BELL);
        ItemStack icon = item(template, event.getName().substring(event.getName().lastIndexOf('/') + 1), List.of(
                ChatColor.DARK_GRAY + event.getName(),
                ChatColor.GRAY + "Position: " + ChatColor.WHITE + (index + 1),
                ChatColor.YELLOW + "Pick up and drop to move"));
        ItemMeta meta = icon.getItemMeta();
        meta.getPersistentDataContainer().set(reorderEventKey, PersistentDataType.STRING, event.getName());
        icon.setItemMeta(meta);
        return icon;
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
            clearReorderCursor(event.getPlayer());
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
            player.sendMessage(Component.text(customEvent.getIcon() == null ? "Event icon cleared." : "Event icon updated."));
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
        if (slot == 53 && (holder.page + 1) * PAGE_SIZE < holder.names.size()) {
            openReorder(player, holder, holder.page + 1);
            return;
        }
        if (slot == 48) {
            clearReorderSelection(player, holder);
            try {
                events.reorder(holder.names);
                player.sendMessage(Component.text("Custom event order saved."));
                open(player, holder.returnFolder, holder.returnPage);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(Component.text(
                        "The custom event list changed while you were editing. Please reorder it again."));
                openReorder(player, holder.returnFolder, holder.returnPage);
            }
            return;
        }
        if (slot == 50) {
            clearReorderSelection(player, holder);
            open(player, holder.returnFolder, holder.returnPage);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) return;
        int targetIndex = Math.min(holder.page * PAGE_SIZE + slot, holder.names.size() - 1);
        if (targetIndex < 0) return;
        if (holder.selectedName == null) {
            int sourceIndex = holder.page * PAGE_SIZE + slot;
            if (sourceIndex >= holder.names.size() || !isEmpty(player.getItemOnCursor())) return;
            holder.selectedName = holder.names.get(sourceIndex);
            click.getView().getTopInventory().setItem(slot, null);
            restoreReorderCursor(player, holder);
            return;
        }
        int sourceIndex = holder.names.indexOf(holder.selectedName);
        if (sourceIndex >= 0 && sourceIndex != targetIndex) {
            String moved = holder.names.remove(sourceIndex);
            holder.names.add(targetIndex, moved);
        }
        clearReorderSelection(player, holder);
        renderReorder(click.getView().getTopInventory(), holder);
    }

    private void restoreReorderCursor(Player player, ReorderEventsHolder holder) {
        if (holder.selectedName == null) return;
        events.find(holder.selectedName).ifPresent(event ->
                player.setItemOnCursor(reorderItem(event, holder.names.indexOf(holder.selectedName))));
    }

    private void clearReorderSelection(Player player, ReorderEventsHolder holder) {
        holder.selectedName = null;
        clearReorderCursor(player);
    }

    private void clearReorderCursor(org.bukkit.entity.HumanEntity player) {
        ItemStack cursor = player.getItemOnCursor();
        if (!isEmpty(cursor) && cursor.hasItemMeta()
                && cursor.getItemMeta().getPersistentDataContainer()
                        .has(reorderEventKey, PersistentDataType.STRING)) {
            player.setItemOnCursor(null);
        }
    }

    private boolean isEmpty(ItemStack item) { return item == null || item.getType().isAir(); }

    private void requestName(Player player, EventsHolder holder) {
        createEvent(player, "", event -> open(player, parent(event.getName()), 0),
                () -> open(player, holder.folder(), holder.page()));
    }

    private void openDelete(Player player, CustomEvent event, EventsHolder back) {
        Inventory inventory = Bukkit.createInventory(new DeleteHolder(event.getName(), back.folder(), back.page()), 27,
                Component.text("Delete event: " + event.getName()));
        inventory.setItem(11, item(Material.LIME_CONCRETE, "Confirm", List.of(
                ChatColor.RED + "Permanently delete this event",
                ChatColor.GRAY + "NPC reactions to it will also be removed")));
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
            player.sendMessage(Component.text("Deleted custom event '" + event.getName() + "'."));
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
                ChatColor.DARK_GRAY + event.getName(),
                ChatColor.GRAY + (event.getDescription().isBlank() ? "No description" : event.getDescription()),
                ChatColor.AQUA + "Middle-click: set icon from main hand",
                ChatColor.YELLOW + "Right-click: edit description",
                ChatColor.RED + "Shift-right-click: delete"));
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
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(ChatColor.GOLD + name); meta.setLore(lore); item.setItemMeta(meta);
        return item;
    }
    private record Entry(boolean folder, String path, String label, int childCount, CustomEvent event) { }
    private record EventsHolder(String folder, int page) implements InventoryHolder { public Inventory getInventory() { return null; } }
    private static final class ReorderEventsHolder implements InventoryHolder {
        private final List<String> names;
        private final String returnFolder;
        private final int returnPage;
        private int page;
        private String selectedName;

        private ReorderEventsHolder(List<String> names, String returnFolder, int returnPage) {
            this.names = names;
            this.returnFolder = returnFolder;
            this.returnPage = returnPage;
        }

        public Inventory getInventory() { return null; }
    }
    private record DeleteHolder(String eventName, String folder, int page) implements InventoryHolder { public Inventory getInventory() { return null; } }
}
