package dev.easynpc.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.easynpc.dialog.DialogService;
import dev.easynpc.input.ChatInputService;
import dev.easynpc.model.AggressionLevel;
import dev.easynpc.model.CombatProfile;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.model.NpcRoute;
import dev.easynpc.model.WalkingSpeed;
import dev.easynpc.repository.NpcDefinitionRepository;
import dev.easynpc.repository.RouteRepository;
import dev.easynpc.runtime.NpcInstanceRegistry;
import dev.easynpc.util.SkinTextureUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GuiService implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final Set<Integer> INVENTORY_EDIT_SLOTS = Set.of(
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        45, 46, 47, 48, 50, 51
    );

    private final NpcDefinitionRepository definitionRepository;
    private final RouteRepository routeRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final ChatInputService chatInputService;
    private final DialogService dialogService;
    private final Set<UUID> explicitInventorySaves = new HashSet<>();

    public GuiService(
        NpcDefinitionRepository definitionRepository,
        RouteRepository routeRepository,
        NpcInstanceRegistry instanceRegistry,
        ChatInputService chatInputService,
        DialogService dialogService
    ) {
        this.definitionRepository = definitionRepository;
        this.routeRepository = routeRepository;
        this.instanceRegistry = instanceRegistry;
        this.chatInputService = chatInputService;
        this.dialogService = dialogService;
    }

    public void openMain(Player player) {
        openMain(player, 0);
    }

    public void openMain(Player player, int requestedPage) {
        List<NpcDefinition> definitions = new ArrayList<>(definitionRepository.findAll());
        int pages = Math.max(1, (definitions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new MainHolder(page), 54, Component.text("EasyNPC Presets"));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, definitions.size());
        for (int index = from; index < to; index++) {
            NpcDefinition definition = definitions.get(index);
            int instances = instanceRegistry.findByDefinition(definition).size();
            inventory.setItem(index - from, definitionIcon(definition, List.of(
                ChatColor.DARK_GRAY + "Key: " + definition.getKey(),
                ChatColor.GRAY + "Instances: " + ChatColor.WHITE + instances,
                statusLine(definition),
                ChatColor.YELLOW + "Click to manage"
            )));
        }
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of(ChatColor.GRAY + "Page " + page + " of " + pages)));
        }
        inventory.setItem(49, item(Material.NETHER_STAR, "EasyNPC Overview", List.of(
            ChatColor.GRAY + "Presets: " + ChatColor.WHITE + definitions.size(),
            ChatColor.GRAY + "Spawned instances: " + ChatColor.WHITE + instanceRegistry.findAll().size(),
            ChatColor.GRAY + "Page " + (page + 1) + " of " + pages
        )));
        inventory.setItem(51, item(Material.EMERALD, "Create NPC", List.of(
            ChatColor.GRAY + "Creates a new preset",
            ChatColor.YELLOW + "Click, then enter its name in chat"
        )));
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of(ChatColor.GRAY + "Page " + (page + 2) + " of " + pages)));
        }
        player.openInventory(inventory);
    }

    public void openEditor(Player player, NpcDefinition definition) {
        int instances = instanceRegistry.findByDefinition(definition).size();
        Inventory inventory = Bukkit.createInventory(new EditorHolder(definition.getKey()), 36,
            Component.text("Manage: " + definition.getDisplayName()));
        inventory.setItem(4, definitionIcon(definition, List.of(
            ChatColor.DARK_GRAY + "Key: " + definition.getKey(),
            ChatColor.GRAY + "Instances: " + ChatColor.WHITE + instances,
            ChatColor.GRAY + "Skin: " + ChatColor.WHITE + (definition.getSkinUrl() == null ? "Default" : "Custom"),
            ChatColor.GRAY + "Spawn: " + ChatColor.WHITE + formatLocation(definition.getSpawnpoint())
        )));
        inventory.setItem(10, item(Material.NAME_TAG, "Display Name", List.of(
            ChatColor.GRAY + definition.getDisplayName(),
            ChatColor.YELLOW + "Click to rename"
        )));
        inventory.setItem(11, item(Material.PLAYER_HEAD, "Skin", List.of(
            ChatColor.GRAY + abbreviatedSkin(definition.getSkinUrl()),
            ChatColor.YELLOW + "Click to set a URL or texture hash",
            ChatColor.DARK_GRAY + "Enter 'default' to clear it"
        )));
        inventory.setItem(12, item(Material.COMPASS, "Preset Spawnpoint", List.of(
            ChatColor.GRAY + formatLocation(definition.getSpawnpoint()),
            ChatColor.YELLOW + "Click to use your current location"
        )));
        inventory.setItem(13, item(Material.CHEST, "Equipment", List.of(
            ChatColor.GRAY + "Armor, hands, and stored inventory",
            ChatColor.YELLOW + "Click to edit"
        )));
        inventory.setItem(14, item(Material.WRITABLE_BOOK, "Dialog", List.of(
            ChatColor.GRAY + "" + definition.getDialogLines().size() + " configured line(s)",
            ChatColor.YELLOW + "Click to edit"
        )));
        inventory.setItem(15, item(Material.ARMOR_STAND, "Spawn Instance", List.of(
            ChatColor.GRAY + "Creates a visible persistent NPC",
            ChatColor.GRAY + "at the preset spawnpoint",
            ChatColor.YELLOW + "Click to spawn"
        )));
        inventory.setItem(16, item(Material.ENDER_EYE, "Manage Instances", List.of(
            ChatColor.GRAY + "" + instances + " spawned instance(s)",
            ChatColor.YELLOW + "Teleport to or remove copies"
        )));
        String assignedRouteKey = definition.getMovementProfile().routeKey();
        String routeName = assignedRouteKey == null ? "None" : routeRepository.find(assignedRouteKey)
            .map(NpcRoute::getDisplayName)
            .orElse("Missing route");
        inventory.setItem(20, item(Material.RAIL, "Walking Route", List.of(
            ChatColor.GRAY + "Assigned: " + ChatColor.WHITE + routeName,
            ChatColor.YELLOW + "Click to assign or clear a route"
        )));
        WalkingSpeed walkingSpeed = definition.getMovementProfile().walkingSpeed();
        inventory.setItem(21, item(Material.FEATHER, "Walking Speed", List.of(
            ChatColor.GRAY + "Current: " + ChatColor.WHITE + walkingSpeed.displayName(),
            ChatColor.GRAY + "" + walkingSpeed.blocksPerSecond() + " blocks/second",
            ChatColor.YELLOW + "Click to cycle to " + walkingSpeed.next().displayName()
        )));
        inventory.setItem(22, item(Material.SUNFLOWER, "Refresh Instances", List.of(
            ChatColor.GRAY + "Re-applies name, skin, and equipment",
            ChatColor.YELLOW + "Click to refresh all copies"
        )));
        CombatProfile combat = definition.getCombatProfile();
        inventory.setItem(23, item(Material.IRON_SWORD, "Fighting", List.of(
            ChatColor.GRAY + "Health: " + ChatColor.WHITE + healthLabel(combat),
            ChatColor.GRAY + "Aggression: " + ChatColor.WHITE + combat.aggressionLevel().displayName(),
            ChatColor.YELLOW + "Click to configure combat"
        )));
        inventory.setItem(24, item(Material.TNT, "Delete Preset", List.of(
            ChatColor.RED + "Deletes this preset and all its copies",
            ChatColor.YELLOW + "Click for confirmation"
        )));
        inventory.setItem(31, item(Material.ARROW, "Back to Presets", List.of()));
        player.openInventory(inventory);
    }

    public void openInventoryEditor(Player player, NpcDefinition definition) {
        Inventory inventory = Bukkit.createInventory(new EquipmentHolder(definition.getKey()), 54,
            Component.text("Equipment: " + definition.getDisplayName()));
        ItemStack[] contents = definition.getInventoryContents();
        for (int index = 0; index < contents.length; index++) {
            inventory.setItem(index, contents[index]);
        }
        inventory.setItem(36, label("Helmet", Material.CHAINMAIL_HELMET));
        inventory.setItem(37, label("Chestplate", Material.CHAINMAIL_CHESTPLATE));
        inventory.setItem(38, label("Leggings", Material.CHAINMAIL_LEGGINGS));
        inventory.setItem(39, label("Boots", Material.CHAINMAIL_BOOTS));
        inventory.setItem(41, label("Main Hand", Material.IRON_SWORD));
        inventory.setItem(42, label("Off Hand", Material.SHIELD));
        inventory.setItem(44, label("NPC inventory above", Material.CHEST));
        ItemStack[] armor = definition.getArmorContents();
        inventory.setItem(45, armor[3]);
        inventory.setItem(46, armor[2]);
        inventory.setItem(47, armor[1]);
        inventory.setItem(48, armor[0]);
        inventory.setItem(50, definition.getMainHand());
        inventory.setItem(51, definition.getOffHand());
        inventory.setItem(53, item(Material.LIME_DYE, "Save Equipment", List.of(
            ChatColor.GRAY + "Saves and refreshes every instance"
        )));
        player.openInventory(inventory);
    }

    public void openDialogEditor(Player player, NpcDefinition definition) {
        Inventory inventory = Bukkit.createInventory(new DialogHolder(definition.getKey()), 27,
            Component.text("Dialog: " + definition.getDisplayName()));
        inventory.setItem(10, item(Material.PAPER, "Set Lines", List.of(
            ChatColor.GRAY + "Use | between multiple lines",
            ChatColor.YELLOW + "Click to enter text"
        )));
        inventory.setItem(12, item(Material.CLOCK, "Seconds Per Line", List.of(
            ChatColor.GRAY + String.valueOf(definition.getSecondsPerDialogLine()),
            ChatColor.YELLOW + "Click to change"
        )));
        inventory.setItem(14, item(Material.BOOK, "Current Dialog", previewLines(definition)));
        inventory.setItem(22, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    public void openFightingEditor(Player player, NpcDefinition definition) {
        CombatProfile combat = definition.getCombatProfile();
        Inventory inventory = Bukkit.createInventory(new FightingHolder(definition.getKey()), 27,
            Component.text("Fighting: " + definition.getDisplayName()));
        inventory.setItem(4, item(Material.IRON_SWORD, "Combat Capabilities", List.of(
            ChatColor.GRAY + "Health: " + ChatColor.WHITE + healthLabel(combat),
            ChatColor.GRAY + "Aggression: " + ChatColor.WHITE + combat.aggressionLevel().displayName()
        )));
        inventory.setItem(10, item(Material.RED_DYE, "- " + CombatProfile.HEALTH_STEP + " Health", List.of(
            ChatColor.GRAY + "Current: " + ChatColor.WHITE + healthLabel(combat),
            ChatColor.YELLOW + "Click to decrease max health"
        )));
        inventory.setItem(11, item(combat.invulnerable() ? Material.TOTEM_OF_UNDYING : Material.GOLDEN_APPLE,
            "Max Health: " + healthLabel(combat), List.of(
                combat.invulnerable()
                    ? ChatColor.GREEN + "This NPC cannot be damaged"
                    : ChatColor.GRAY + "The NPC is removed when killed",
                ChatColor.DARK_GRAY + "Set health to 0 for invulnerability"
            )));
        inventory.setItem(12, item(Material.LIME_DYE, "+ " + CombatProfile.HEALTH_STEP + " Health", List.of(
            ChatColor.GRAY + "Current: " + ChatColor.WHITE + healthLabel(combat),
            ChatColor.YELLOW + "Click to increase max health"
        )));
        inventory.setItem(14, item(aggressionMaterial(combat.aggressionLevel()), "Aggression", List.of(
            ChatColor.GRAY + "Current: " + ChatColor.WHITE + combat.aggressionLevel().displayName(),
            aggressionDescription(combat.aggressionLevel()),
            ChatColor.YELLOW + "Click to cycle aggression level"
        )));
        inventory.setItem(16, item(Material.WRITABLE_BOOK, "Combat Shoutout", List.of(
            ChatColor.GRAY + (combat.shoutout() == null ? "No shoutout configured" : combat.shoutout()),
            ChatColor.YELLOW + "Click to enter a custom shoutout",
            ChatColor.DARK_GRAY + "Enter 'clear' to remove it"
        )));
        inventory.setItem(22, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    public void openRouteAssignment(Player player, NpcDefinition definition, int requestedPage) {
        List<NpcRoute> routes = new ArrayList<>(routeRepository.findAll());
        int pages = Math.max(1, (routes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new RouteAssignmentHolder(definition.getKey(), page), 54,
            Component.text("Route: " + definition.getDisplayName()));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, routes.size());
        for (int index = from; index < to; index++) {
            NpcRoute route = routes.get(index);
            boolean selected = route.getKey().equals(definition.getMovementProfile().routeKey());
            inventory.setItem(index - from, item(selected ? Material.POWERED_RAIL : Material.RAIL, route.getDisplayName(), List.of(
                ChatColor.DARK_GRAY + "Key: " + route.getKey(),
                ChatColor.GRAY + "Key points: " + ChatColor.WHITE + route.getPoints().size(),
                selected ? ChatColor.GREEN + "Currently assigned" : ChatColor.YELLOW + "Click to assign"
            )));
        }
        if (routes.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, "No Routes", List.of(
                ChatColor.GRAY + "Create one with /eznpc routes"
            )));
        }
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        }
        inventory.setItem(48, item(Material.ARROW, "Back to Preset", List.of()));
        inventory.setItem(49, item(Material.BARRIER, "Clear Route", List.of(
            ChatColor.GRAY + "Stops this NPC preset from walking"
        )));
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        player.openInventory(inventory);
    }

    public void openInstances(Player player, NpcDefinition definition, int requestedPage) {
        List<NpcInstance> instances = new ArrayList<>(instanceRegistry.findByDefinition(definition));
        int pages = Math.max(1, (instances.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new InstancesHolder(definition.getKey(), page), 54,
            Component.text("Instances: " + definition.getDisplayName()));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, instances.size());
        for (int index = from; index < to; index++) {
            NpcInstance instance = instances.get(index);
            inventory.setItem(index - from, item(Material.ARMOR_STAND, "NPC Instance", List.of(
                ChatColor.DARK_GRAY + instance.getId().toString(),
                ChatColor.GRAY + formatLocation(instance.getLocation()),
                ChatColor.YELLOW + "Left-click: teleport to instance",
                ChatColor.RED + "Right-click: remove instance"
            )));
        }
        if (instances.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, "No Instances", List.of(
                ChatColor.GRAY + "Return to the preset editor to spawn one."
            )));
        }
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        }
        if (!instances.isEmpty()) {
            inventory.setItem(47, item(Material.REDSTONE_BLOCK, "Remove All Instances", List.of(
                ChatColor.RED + "Removes every spawned copy",
                ChatColor.YELLOW + "Click for confirmation"
            )));
        }
        inventory.setItem(49, item(Material.ARROW, "Back to Preset", List.of()));
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MainHolder mainHolder) {
            handleMainClick(event, player, mainHolder.page());
        } else if (holder instanceof EditorHolder editorHolder) {
            handleEditorClick(event, player, editorHolder.key());
        } else if (holder instanceof DialogHolder dialogHolder) {
            handleDialogClick(event, player, dialogHolder.key());
        } else if (holder instanceof FightingHolder fightingHolder) {
            handleFightingClick(event, player, fightingHolder.key());
        } else if (holder instanceof EquipmentHolder equipmentHolder) {
            handleEquipmentClick(event, player, equipmentHolder.key());
        } else if (holder instanceof InstancesHolder instancesHolder) {
            handleInstancesClick(event, player, instancesHolder);
        } else if (holder instanceof RouteAssignmentHolder routeHolder) {
            handleRouteAssignmentClick(event, player, routeHolder);
        } else if (holder instanceof ConfirmationHolder confirmationHolder) {
            handleConfirmationClick(event, player, confirmationHolder);
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        NpcInstance instance = instanceRegistry.findByEntityId(event.getRightClicked().getEntityId()).orElse(null);
        if (instance == null) {
            return;
        }
        event.setCancelled(true);
        NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            return;
        }
        if (event.getPlayer().isSneaking() && event.getPlayer().hasPermission("eznpc.admin")) {
            openEditor(event.getPlayer(), definition);
            return;
        }
        dialogService.startChat(event.getPlayer(), instance, definition);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof EquipmentHolder) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize && !INVENTORY_EDIT_SLOTS.contains(slot))) {
                event.setCancelled(true);
            }
        } else if (isManagedHolder(holder)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EquipmentHolder holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player && explicitInventorySaves.remove(player.getUniqueId())) {
            return;
        }
        definitionRepository.find(holder.key()).ifPresent(definition -> {
            readEquipmentEditor(event.getInventory(), definition);
            saveRefresh(definition);
        });
    }

    private void handleMainClick(InventoryClickEvent event, Player player, int page) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        if (event.getRawSlot() == 45) {
            openMain(player, page - 1);
            return;
        }
        if (event.getRawSlot() == 51) {
            chatInputService.request(player, "Enter a new NPC name:", value -> {
                NpcDefinition definition = NpcDefinition.create(value);
                if (definitionRepository.find(definition.getKey()).isPresent()) {
                    player.sendMessage(Component.text("An NPC with that key already exists."));
                    openMain(player, page);
                    return;
                }
                definition.setSpawnpoint(player.getLocation());
                definitionRepository.save(definition);
                openEditor(player, definition);
            });
            return;
        }
        if (event.getRawSlot() == 53) {
            openMain(player, page + 1);
            return;
        }
        List<NpcDefinition> definitions = new ArrayList<>(definitionRepository.findAll());
        int index = page * PAGE_SIZE + event.getRawSlot();
        if (event.getRawSlot() < PAGE_SIZE && index >= 0 && index < definitions.size()) {
            openEditor(player, definitions.get(index));
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
                definition.setDisplayName(value.trim());
                saveRefresh(definition);
                openEditor(player, definition);
            });
            case 11 -> chatInputService.request(player, "Enter a Minecraft texture URL/hash, or 'default':", value -> {
                try {
                    definition.setSkinUrl(SkinTextureUtil.normalizeTextureUrl(value));
                    saveRefresh(definition);
                    player.sendMessage(Component.text(definition.getSkinUrl() == null ? "Using the default skin." : "Skin updated."));
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(Component.text(exception.getMessage()));
                }
                openEditor(player, definition);
            });
            case 12 -> {
                definition.setSpawnpoint(player.getLocation());
                definitionRepository.save(definition);
                player.sendMessage(Component.text("Preset spawnpoint updated. Existing instances were not moved."));
                openEditor(player, definition);
            }
            case 13 -> openInventoryEditor(player, definition);
            case 14 -> openDialogEditor(player, definition);
            case 15 -> {
                if (definition.getSpawnpoint() == null) {
                    player.sendMessage(Component.text("Set a spawnpoint first."));
                } else {
                    instanceRegistry.spawnPersistent(definition, definition.getSpawnpoint());
                    player.sendMessage(Component.text("Spawned a visible NPC instance."));
                }
                openEditor(player, definition);
            }
            case 16 -> openInstances(player, definition, 0);
            case 20 -> openRouteAssignment(player, definition, 0);
            case 21 -> {
                WalkingSpeed speed = definition.getMovementProfile().walkingSpeed().next();
                definition.setMovementProfile(definition.getMovementProfile().withWalkingSpeed(speed));
                definitionRepository.save(definition);
                player.sendMessage(Component.text("Walking speed set to " + speed.displayName() + "."));
                openEditor(player, definition);
            }
            case 22 -> {
                instanceRegistry.refreshDefinition(definition);
                player.sendMessage(Component.text("Refreshed " + instanceRegistry.findByDefinition(definition).size() + " instance(s)."));
                openEditor(player, definition);
            }
            case 23 -> openFightingEditor(player, definition);
            case 24 -> openConfirmation(player, definition, ConfirmationAction.DELETE_DEFINITION);
            case 31 -> openMain(player);
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
                    player.sendMessage(Component.text("Enter a whole number of seconds."));
                }
                openDialogEditor(player, definition);
            });
            case 22 -> openEditor(player, definition);
            default -> {
            }
        }
    }

    private void handleFightingClick(InventoryClickEvent event, Player player, String key) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(key).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        CombatProfile combat = definition.getCombatProfile();
        switch (event.getRawSlot()) {
            case 10 -> {
                definition.setCombatProfile(combat.withMaxHealth(combat.maxHealth() - CombatProfile.HEALTH_STEP));
                saveRefresh(definition);
                openFightingEditor(player, definition);
            }
            case 12 -> {
                definition.setCombatProfile(combat.withMaxHealth(combat.maxHealth() + CombatProfile.HEALTH_STEP));
                saveRefresh(definition);
                openFightingEditor(player, definition);
            }
            case 14 -> {
                AggressionLevel aggression = combat.aggressionLevel().next();
                definition.setCombatProfile(combat.withAggressionLevel(aggression));
                definitionRepository.save(definition);
                player.sendMessage(Component.text("Aggression set to " + aggression.displayName() + "."));
                openFightingEditor(player, definition);
            }
            case 16 -> chatInputService.request(player, "Enter a combat shoutout, or 'clear':", value -> {
                String shoutout = value.trim().equalsIgnoreCase("clear") ? null : value;
                definition.setCombatProfile(definition.getCombatProfile().withShoutout(shoutout));
                definitionRepository.save(definition);
                openFightingEditor(player, definition);
            });
            case 22 -> openEditor(player, definition);
            default -> {
            }
        }
    }

    private void handleEquipmentClick(InventoryClickEvent event, Player player, String key) {
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
            || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (!isTopInventoryClick(event)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 53) {
            event.setCancelled(true);
            definitionRepository.find(key).ifPresent(definition -> {
                readEquipmentEditor(event.getView().getTopInventory(), definition);
                saveRefresh(definition);
                explicitInventorySaves.add(player.getUniqueId());
                openEditor(player, definition);
            });
            return;
        }
        if (!INVENTORY_EDIT_SLOTS.contains(slot)) {
            event.setCancelled(true);
        }
    }

    private void handleInstancesClick(InventoryClickEvent event, Player player, InstancesHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(holder.key()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case 45 -> openInstances(player, definition, holder.page() - 1);
            case 47 -> openConfirmation(player, definition, ConfirmationAction.DELETE_INSTANCES);
            case 49 -> openEditor(player, definition);
            case 53 -> openInstances(player, definition, holder.page() + 1);
            default -> {
                List<NpcInstance> instances = new ArrayList<>(instanceRegistry.findByDefinition(definition));
                int index = holder.page() * PAGE_SIZE + event.getRawSlot();
                if (event.getRawSlot() >= PAGE_SIZE || index < 0 || index >= instances.size()) {
                    return;
                }
                NpcInstance instance = instances.get(index);
                if (event.isRightClick()) {
                    instanceRegistry.deleteInstance(instance.getId());
                    player.sendMessage(Component.text("Removed NPC instance."));
                    openInstances(player, definition, holder.page());
                } else {
                    player.closeInventory();
                    player.teleport(instance.getLocation());
                    player.sendMessage(Component.text("Teleported to NPC instance."));
                }
            }
        }
    }

    private void handleRouteAssignmentClick(InventoryClickEvent event, Player player, RouteAssignmentHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(holder.definitionKey()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case 45 -> openRouteAssignment(player, definition, holder.page() - 1);
            case 48 -> openEditor(player, definition);
            case 49 -> {
                definition.setMovementProfile(definition.getMovementProfile().withoutRoute());
                saveRefresh(definition);
                player.sendMessage(Component.text("Walking route cleared."));
                openEditor(player, definition);
            }
            case 53 -> openRouteAssignment(player, definition, holder.page() + 1);
            default -> {
                List<NpcRoute> routes = new ArrayList<>(routeRepository.findAll());
                int index = holder.page() * PAGE_SIZE + event.getRawSlot();
                if (event.getRawSlot() >= PAGE_SIZE || index < 0 || index >= routes.size()) {
                    return;
                }
                NpcRoute route = routes.get(index);
                definition.setMovementProfile(definition.getMovementProfile().withRoute(route.getKey()));
                saveRefresh(definition);
                player.sendMessage(Component.text("Assigned route '" + route.getDisplayName() + "'."));
                openEditor(player, definition);
            }
        }
    }

    private void handleConfirmationClick(InventoryClickEvent event, Player player, ConfirmationHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(holder.key()).orElse(null);
        if (definition == null) {
            openMain(player);
            return;
        }
        if (event.getRawSlot() == 15) {
            if (holder.action() == ConfirmationAction.DELETE_DEFINITION) {
                openEditor(player, definition);
            } else {
                openInstances(player, definition, 0);
            }
            return;
        }
        if (event.getRawSlot() != 11) {
            return;
        }
        int removed = instanceRegistry.deleteInstances(definition);
        if (holder.action() == ConfirmationAction.DELETE_DEFINITION) {
            definitionRepository.delete(definition);
            player.sendMessage(Component.text("Deleted preset and " + removed + " instance(s)."));
            openMain(player);
        } else {
            player.sendMessage(Component.text("Removed " + removed + " instance(s)."));
            openInstances(player, definition, 0);
        }
    }

    private void openConfirmation(Player player, NpcDefinition definition, ConfirmationAction action) {
        Inventory inventory = Bukkit.createInventory(new ConfirmationHolder(definition.getKey(), action), 27,
            Component.text("Confirm deletion"));
        String target = action == ConfirmationAction.DELETE_DEFINITION ? "preset and all instances" : "all instances";
        inventory.setItem(11, item(Material.LIME_CONCRETE, "Confirm", List.of(
            ChatColor.RED + "Permanently delete " + target
        )));
        inventory.setItem(15, item(Material.RED_CONCRETE, "Cancel", List.of(ChatColor.GRAY + "Nothing will be changed")));
        player.openInventory(inventory);
    }

    private void readEquipmentEditor(Inventory inventory, NpcDefinition definition) {
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
    }

    private void saveRefresh(NpcDefinition definition) {
        definitionRepository.save(definition);
        instanceRegistry.refreshDefinition(definition);
    }

    private boolean isTopInventoryClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        return slot >= 0 && slot < event.getView().getTopInventory().getSize();
    }

    private boolean isManagedHolder(InventoryHolder holder) {
        return holder instanceof MainHolder
            || holder instanceof EditorHolder
            || holder instanceof DialogHolder
            || holder instanceof FightingHolder
            || holder instanceof InstancesHolder
            || holder instanceof RouteAssignmentHolder
            || holder instanceof ConfirmationHolder;
    }

    private ItemStack definitionIcon(NpcDefinition definition, List<String> lore) {
        ItemStack head = item(Material.PLAYER_HEAD, definition.getDisplayName(), lore);
        if (definition.getSkinUrl() == null || !(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        try {
            UUID uuid = UUID.nameUUIDFromBytes(definition.getKey().getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfileExact(uuid, "EasyNPC");
            profile.setProperty(new ProfileProperty("textures", SkinTextureUtil.toTextureProperty(definition.getSkinUrl())));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        } catch (RuntimeException ignored) {
            // A broken legacy skin value must never prevent the management GUI opening.
        }
        return head;
    }

    private ItemStack label(String name, Material material) {
        return item(material, name, List.of(ChatColor.DARK_GRAY + "Place the item in the slot below"));
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

    private String statusLine(NpcDefinition definition) {
        if (definition.getSpawnpoint() == null) {
            return ChatColor.RED + "Spawnpoint not set";
        }
        return ChatColor.GRAY + "Spawn: " + ChatColor.WHITE + formatLocation(definition.getSpawnpoint());
    }

    private String abbreviatedSkin(String value) {
        if (value == null) {
            return "Default skin";
        }
        String prefix = "https://textures.minecraft.net/texture/";
        String hash = value.startsWith(prefix) ? value.substring(prefix.length()) : value;
        return hash.length() <= 24 ? hash : hash.substring(0, 24) + "...";
    }

    private String healthLabel(CombatProfile combat) {
        return combat.invulnerable() ? "Invulnerable (0 HP)" : combat.maxHealth() + " HP";
    }

    private Material aggressionMaterial(AggressionLevel aggressionLevel) {
        return switch (aggressionLevel) {
            case NONE -> Material.GRAY_DYE;
            case FLEE -> Material.RABBIT_FOOT;
            case FIGHT_BACK -> Material.SHIELD;
            case FIGHTS_ON_SIGHT -> Material.DIAMOND_SWORD;
        };
    }

    private String aggressionDescription(AggressionLevel aggressionLevel) {
        return switch (aggressionLevel) {
            case NONE -> ChatColor.GRAY + "Never reacts to nearby entities";
            case FLEE -> ChatColor.GRAY + "Runs away after taking entity damage";
            case FIGHT_BACK -> ChatColor.GRAY + "Attacks an entity that damages it";
            case FIGHTS_ON_SIGHT -> ChatColor.GRAY + "Attacks nearby players, mobs, and NPCs";
        };
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Not set";
        }
        return location.getWorld().getName() + " "
            + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private record MainHolder(int page) implements InventoryHolder {
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

    private record FightingHolder(String key) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record EquipmentHolder(String key) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record InstancesHolder(String key, int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RouteAssignmentHolder(String definitionKey, int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record ConfirmationHolder(String key, ConfirmationAction action) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private enum ConfirmationAction {
        DELETE_DEFINITION,
        DELETE_INSTANCES
    }
}
