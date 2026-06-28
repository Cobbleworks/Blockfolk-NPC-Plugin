package dev.easynpc.gui;

import dev.easynpc.input.ChatInputService;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.repository.NpcDefinitionRepository;
import dev.easynpc.runtime.NpcInstanceRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GuiService implements Listener {
    private final Plugin plugin;
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final ChatInputService chatInputService;
    private final Set<UUID> explicitInventorySaves = new HashSet<>();

    public GuiService(
        Plugin plugin,
        NpcDefinitionRepository definitionRepository,
        NpcInstanceRegistry instanceRegistry,
        ChatInputService chatInputService
    ) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.chatInputService = chatInputService;
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(new MainHolder(), 54, Component.text("EasyNPC"));
        int slot = 0;
        for (NpcDefinition definition : definitionRepository.findAll()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, item(Material.PLAYER_HEAD, definition.getDisplayName(), List.of(
                ChatColor.GRAY + "Key: " + definition.getKey(),
                ChatColor.YELLOW + "Click to edit"
            )));
        }
        inventory.setItem(53, item(Material.EMERALD, "Create NPC", List.of(ChatColor.GRAY + "Uses chat input")));
        player.openInventory(inventory);
    }

    public void openEditor(Player player, NpcDefinition definition) {
        Inventory inventory = Bukkit.createInventory(new EditorHolder(definition.getKey()), 27, Component.text("NPC: " + definition.getDisplayName()));
        inventory.setItem(10, item(Material.NAME_TAG, "Set Name", List.of(ChatColor.GRAY + definition.getDisplayName())));
        inventory.setItem(11, item(Material.PLAYER_HEAD, "Set Skin URL", List.of(ChatColor.GRAY + nullText(definition.getSkinUrl()))));
        inventory.setItem(12, item(Material.ENDER_PEARL, "Set Spawnpoint", List.of(ChatColor.GRAY + "Use your current location")));
        inventory.setItem(13, item(Material.CHEST, "Edit Inventory", List.of(ChatColor.GRAY + "Items, armor, and hands")));
        inventory.setItem(14, item(Material.WRITABLE_BOOK, "Edit Dialog", List.of(ChatColor.GRAY + String.valueOf(definition.getDialogLines().size()) + " lines")));
        inventory.setItem(15, item(Material.ARMOR_STAND, "Spawn Copy", List.of(ChatColor.GRAY + "Creates a persistent instance")));
        inventory.setItem(16, item(Material.REDSTONE_BLOCK, "Delete Instances", List.of(
            ChatColor.GRAY + "Removes spawned copies only",
            ChatColor.GRAY + "Preset stays saved"
        )));
        inventory.setItem(26, item(Material.BARRIER, "Back", List.of()));
        player.openInventory(inventory);
    }

    public void openInventoryEditor(Player player, NpcDefinition definition) {
        Inventory inventory = Bukkit.createInventory(new InventoryHolderImpl(definition.getKey()), 54, Component.text("Inventory: " + definition.getDisplayName()));
        ItemStack[] contents = definition.getInventoryContents();
        for (int index = 0; index < contents.length; index++) {
            inventory.setItem(index, contents[index]);
        }
        ItemStack[] armor = definition.getArmorContents();
        inventory.setItem(45, armor[3]);
        inventory.setItem(46, armor[2]);
        inventory.setItem(47, armor[1]);
        inventory.setItem(48, armor[0]);
        inventory.setItem(50, definition.getMainHand());
        inventory.setItem(51, definition.getOffHand());
        inventory.setItem(53, item(Material.LIME_DYE, "Save", List.of(ChatColor.GRAY + "Click to save and return")));
        player.openInventory(inventory);
    }

    public void openDialogEditor(Player player, NpcDefinition definition) {
        Inventory inventory = Bukkit.createInventory(new DialogHolder(definition.getKey()), 27, Component.text("Dialog: " + definition.getDisplayName()));
        inventory.setItem(10, item(Material.PAPER, "Set Lines", List.of(ChatColor.GRAY + "Use | between lines")));
        inventory.setItem(12, item(Material.CLOCK, "Set Seconds Per Line", List.of(ChatColor.GRAY + String.valueOf(definition.getSecondsPerDialogLine()))));
        inventory.setItem(14, item(Material.BOOK, "Preview", previewLines(definition)));
        inventory.setItem(26, item(Material.BARRIER, "Back", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MainHolder) {
            handleMainClick(event, player);
        } else if (holder instanceof EditorHolder editorHolder) {
            handleEditorClick(event, player, editorHolder.key());
        } else if (holder instanceof DialogHolder dialogHolder) {
            handleDialogClick(event, player, dialogHolder.key());
        } else if (holder instanceof InventoryHolderImpl inventoryHolder) {
            handleInventoryClick(event, player, inventoryHolder.key());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof InventoryHolderImpl holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player && explicitInventorySaves.remove(player.getUniqueId())) {
            return;
        }
        definitionRepository.find(holder.key()).ifPresent(definition -> saveInventoryEditor(event.getInventory(), definition));
    }

    private void handleMainClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        if (event.getRawSlot() == 53) {
            chatInputService.request(player, "Enter a new NPC name:", value -> {
                NpcDefinition definition = NpcDefinition.create(value);
                if (definitionRepository.find(definition.getKey()).isPresent()) {
                    player.sendMessage(Component.text("An NPC with that key already exists."));
                    return;
                }
                definition.setSpawnpoint(player.getLocation());
                definitionRepository.save(definition);
                openEditor(player, definition);
            });
            return;
        }
        List<NpcDefinition> definitions = new ArrayList<>(definitionRepository.findAll());
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < definitions.size()) {
            openEditor(player, definitions.get(slot));
        }
    }

    private void handleEditorClick(InventoryClickEvent event, Player player, String key) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(key).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> chatInputService.request(player, "Enter NPC display name:", value -> {
                definition.setDisplayName(value);
                saveRefresh(definition);
                openEditor(player, definition);
            });
            case 11 -> chatInputService.request(player, "Enter skin texture URL:", value -> {
                definition.setSkinUrl(value);
                saveRefresh(definition);
                openEditor(player, definition);
            });
            case 12 -> {
                definition.setSpawnpoint(player.getLocation());
                saveRefresh(definition);
                player.sendMessage(Component.text("Spawnpoint updated."));
                openEditor(player, definition);
            }
            case 13 -> openInventoryEditor(player, definition);
            case 14 -> openDialogEditor(player, definition);
            case 15 -> {
                if (definition.getSpawnpoint() == null) {
                    player.sendMessage(Component.text("Set a spawnpoint first."));
                } else {
                    instanceRegistry.spawnPersistent(definition, definition.getSpawnpoint());
                    player.sendMessage(Component.text("Spawned NPC copy."));
                }
                openEditor(player, definition);
            }
            case 16 -> {
                int removed = instanceRegistry.deleteInstances(definition);
                player.sendMessage(Component.text("Deleted " + removed + " NPC instance" + (removed == 1 ? "." : "s.")));
                openEditor(player, definition);
            }
            case 26 -> openMain(player);
            default -> {
            }
        }
    }

    private void handleDialogClick(InventoryClickEvent event, Player player, String key) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(key).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> chatInputService.request(player, "Enter dialog lines separated with |:", value -> {
                definition.setDialogLines(List.of(value.split("\\|")).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .toList());
                saveRefresh(definition);
                openDialogEditor(player, definition);
            });
            case 12 -> chatInputService.request(player, "Enter seconds per dialog line:", value -> {
                try {
                    definition.setSecondsPerDialogLine(Integer.parseInt(value.trim()));
                    saveRefresh(definition);
                } catch (NumberFormatException exception) {
                    player.sendMessage(Component.text("That was not a number."));
                }
                openDialogEditor(player, definition);
            });
            case 26 -> openEditor(player, definition);
            default -> {
            }
        }
    }

    private void handleInventoryClick(InventoryClickEvent event, Player player, String key) {
        if (!isTopInventoryClick(event)) {
            return;
        }
        if (event.getRawSlot() == 53) {
            event.setCancelled(true);
            definitionRepository.find(key).ifPresent(definition -> {
                saveInventoryEditor(event.getInventory(), definition);
                saveRefresh(definition);
                explicitInventorySaves.add(player.getUniqueId());
                openEditor(player, definition);
            });
        }
    }

    private void saveInventoryEditor(Inventory inventory, NpcDefinition definition) {
        ItemStack[] contents = new ItemStack[36];
        for (int index = 0; index < contents.length; index++) {
            contents[index] = inventory.getItem(index);
        }
        definition.setInventoryContents(contents);
        definition.setArmorContents(new ItemStack[]{
            inventory.getItem(48),
            inventory.getItem(47),
            inventory.getItem(46),
            inventory.getItem(45)
        });
        definition.setMainHand(inventory.getItem(50));
        definition.setOffHand(inventory.getItem(51));
        definitionRepository.save(definition);
    }

    private void saveRefresh(NpcDefinition definition) {
        definitionRepository.save(definition);
        instanceRegistry.refreshDefinition(definition);
    }

    private boolean isTopInventoryClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        return slot >= 0 && slot < event.getView().getTopInventory().getSize();
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> previewLines(NpcDefinition definition) {
        if (definition.getDialogLines().isEmpty()) {
            return List.of(ChatColor.GRAY + "No dialog lines set");
        }
        return definition.getDialogLines().stream()
            .limit(5)
            .map(line -> ChatColor.GRAY + line)
            .toList();
    }

    private String nullText(String value) {
        return value == null ? "Not set" : value;
    }

    private record MainHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record EditorHolder(String key) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record DialogHolder(String key) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record InventoryHolderImpl(String key) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
