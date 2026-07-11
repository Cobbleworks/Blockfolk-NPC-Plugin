package dev.easynpc.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import dev.easynpc.dialog.DialogService;
import dev.easynpc.input.ChatInputService;
import dev.easynpc.model.AggressionLevel;
import dev.easynpc.model.BehaviourAction;
import dev.easynpc.model.BehaviourActionType;
import dev.easynpc.model.BehaviourEvent;
import dev.easynpc.model.CombatProfile;
import dev.easynpc.model.LootTier;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.model.NpcRoute;
import dev.easynpc.model.WalkingSpeed;
import dev.easynpc.repository.NpcDefinitionRepository;
import dev.easynpc.repository.RouteRepository;
import dev.easynpc.runtime.NpcBehaviourService;
import dev.easynpc.runtime.NpcInstanceRegistry;
import dev.easynpc.util.ResolvedSkin;
import dev.easynpc.util.SkinResolver;
import dev.easynpc.util.SkinTextureUtil;
import net.kyori.adventure.text.Component;

public final class GuiService implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final Set<Integer> INVENTORY_EDIT_SLOTS = Set.of(
            1, 2, 3, 4, 5, 6, 7, 8,
            10, 11, 12, 13, 14, 15, 16, 17,
            19, 20, 21, 22, 23, 24, 25, 26,
            28, 29, 30, 31, 32, 33, 34, 35,
            45, 46, 47, 48, 50, 51
    );

    private final Plugin plugin;
    private final NpcDefinitionRepository definitionRepository;
    private final RouteRepository routeRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final ChatInputService chatInputService;
    private final DialogService dialogService;
    private final SkinResolver skinResolver;
    private final Consumer<Player> routeGuiOpener;
    private NpcBehaviourService behaviourService;
    private final Set<UUID> explicitInventorySaves = new HashSet<>();
    private final Map<String, String> pendingSkinUrls = new HashMap<>();

    public GuiService(
            Plugin plugin,
            NpcDefinitionRepository definitionRepository,
            RouteRepository routeRepository,
            NpcInstanceRegistry instanceRegistry,
            ChatInputService chatInputService,
            DialogService dialogService,
            SkinResolver skinResolver,
            Consumer<Player> routeGuiOpener
    ) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.routeRepository = routeRepository;
        this.instanceRegistry = instanceRegistry;
        this.chatInputService = chatInputService;
        this.dialogService = dialogService;
        this.skinResolver = skinResolver;
        this.routeGuiOpener = routeGuiOpener;
    }

    public void setBehaviourService(NpcBehaviourService behaviourService) {
        this.behaviourService = behaviourService;
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
        inventory.setItem(45, item(Material.MAP, "Manage Routes", List.of(
                ChatColor.GRAY + "Create and edit NPC walking routes",
                ChatColor.YELLOW + "Click to open route setup"
        )));
        if (page > 0) {
            inventory.setItem(47, item(Material.ARROW, "Previous Page", List.of(ChatColor.GRAY + "Page " + page + " of " + pages)));
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
        inventory.setItem(12, item(Material.RED_BED, "Preset Spawnpoint", List.of(
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
        int behaviourCount = java.util.Arrays.stream(BehaviourEvent.values())
                .mapToInt(event -> definition.getBehaviourActions(event).size()).sum();
        inventory.setItem(20, item(Material.COMPARATOR, "Behaviour", List.of(
                ChatColor.GRAY + "" + behaviourCount + " configured action(s)",
                ChatColor.GRAY + "Build event-to-action sequences",
                ChatColor.YELLOW + "Click to configure"
        )));
        BehaviourAction spawnRoute = definition.getBehaviourActions(BehaviourEvent.SPAWN).stream()
                .filter(action -> action.type() == BehaviourActionType.SET_ROUTE)
                .findFirst()
                .orElse(null);
        String spawnRouteName = spawnRoute == null ? "Not configured" : routeRepository.find(spawnRoute.value())
                .map(NpcRoute::getDisplayName)
                .orElse(spawnRoute.value());
        inventory.setItem(21, item(Material.POWERED_RAIL, "Start Route", List.of(
                ChatColor.GRAY + "Spawn → Set Route: " + ChatColor.WHITE + spawnRouteName,
                ChatColor.YELLOW + "Click to select a route"
        )));
        CombatProfile combat = definition.getCombatProfile();
        inventory.setItem(23, item(Material.IRON_SWORD, "Fighting", List.of(
                ChatColor.GRAY + "Health: " + ChatColor.WHITE + healthLabel(combat),
                ChatColor.GRAY + "Respawn: " + ChatColor.WHITE + respawnLabel(combat),
                ChatColor.GRAY + "Aggression: " + ChatColor.WHITE + combat.aggressionLevel().displayName(),
                ChatColor.YELLOW + "Click to configure combat"
        )));
        inventory.setItem(24, item(Material.TNT, "Delete Preset", List.of(
                ChatColor.RED + "Deletes this preset and all its copies",
                ChatColor.YELLOW + "Shift-click for confirmation"
        )));
        inventory.setItem(31, item(Material.BARRIER, "Back to Presets", List.of()));
        player.openInventory(inventory);
    }

    public void openInventoryEditor(Player player, NpcDefinition definition) {
        Inventory inventory = Bukkit.createInventory(new EquipmentHolder(definition.getKey()), 54,
                Component.text("Equipment: " + definition.getDisplayName()));
        ItemStack[] contents = definition.getInventoryContents();
        for (int index = 0; index < contents.length; index++) {
            if (!LootTier.isRowStarterSlot(index)) {
                inventory.setItem(index, contents[index]);
            }
        }
        for (LootTier tier : LootTier.values()) {
            inventory.setItem(tier.rowStarterSlot(), item(tier.icon(), tier.displayName(), List.of(
                    ChatColor.GRAY + "" + tier.dropChancePercent() + "% chance per item slot"
            )));
        }
        inventory.setItem(36, label("Helmet", Material.CHAINMAIL_HELMET));
        inventory.setItem(37, label("Chestplate", Material.CHAINMAIL_CHESTPLATE));
        inventory.setItem(38, label("Leggings", Material.CHAINMAIL_LEGGINGS));
        inventory.setItem(39, label("Boots", Material.CHAINMAIL_BOOTS));
        inventory.setItem(41, label("Main Hand", Material.IRON_SWORD));
        inventory.setItem(42, label("Off Hand", Material.SHIELD));
        inventory.setItem(44, item(Material.CHEST, "NPC loot above", List.of(
                ChatColor.GRAY + "Each filled slot rolls independently",
                ChatColor.GRAY + "Equipment is stored below"
        )));
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
        inventory.setItem(22, item(Material.BARRIER, "Back", List.of()));
        player.openInventory(inventory);
    }

    public void openFightingEditor(Player player, NpcDefinition definition) {
        CombatProfile combat = definition.getCombatProfile();
        Inventory inventory = Bukkit.createInventory(new FightingHolder(definition.getKey()), 27,
                Component.text("Fighting: " + definition.getDisplayName()));
        inventory.setItem(0, item(Material.LIME_DYE, "+ " + CombatProfile.HEALTH_STEP + " Health", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + healthLabel(combat),
                ChatColor.YELLOW + "Click to increase max health"
        )));
        inventory.setItem(9, combat.invulnerable()
                ? item(Material.TOTEM_OF_UNDYING, "Max Health: " + healthLabel(combat), List.of(
                        ChatColor.GREEN + "This NPC cannot be damaged",
                        ChatColor.DARK_GRAY + "Set health to 0 for invulnerability"
                ))
                : potionItem(PotionType.HEALING, "Max Health: " + healthLabel(combat), List.of(
                        ChatColor.GRAY + "The NPC is removed when killed",
                        ChatColor.DARK_GRAY + "Set health to 0 for invulnerability"
                )));
        inventory.setItem(18, item(Material.RED_DYE, "- " + CombatProfile.HEALTH_STEP + " Health", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + healthLabel(combat),
                ChatColor.YELLOW + "Click to decrease max health"
        )));
        inventory.setItem(1, item(Material.LIME_DYE, "+ " + CombatProfile.RESPAWN_STEP_SECONDS + " Seconds", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + respawnLabel(combat),
                ChatColor.YELLOW + "Click to increase respawn time"
        )));
        inventory.setItem(10, item(combat.respawnSeconds() == 0 ? Material.BARRIER : Material.CLOCK,
                "Respawn Time: " + respawnLabel(combat), List.of(
                combat.respawnSeconds() == 0
                ? ChatColor.GRAY + "Killed NPCs will not respawn"
                : ChatColor.GREEN + "Respawns at the preset spawn point",
                definition.getSpawnpoint() == null
                ? ChatColor.RED + "A preset spawn point is required"
                : ChatColor.DARK_GRAY + "Preset spawn point is configured"
        )));
        inventory.setItem(19, item(Material.RED_DYE, "- " + CombatProfile.RESPAWN_STEP_SECONDS + " Seconds", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + respawnLabel(combat),
                ChatColor.YELLOW + "Click to decrease respawn time"
        )));
        inventory.setItem(5, item(aggressionMaterial(combat.aggressionLevel()), "Aggression", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + combat.aggressionLevel().displayName(),
                aggressionDescription(combat.aggressionLevel()),
                ChatColor.YELLOW + "Click to cycle aggression level"
        )));
        inventory.setItem(23, item(Material.BARRIER, "Back", List.of()));
        player.openInventory(inventory);
    }

    public void openBehaviours(Player player, NpcDefinition definition, int requestedPage) {
        BehaviourEvent[] events = BehaviourEvent.values();
        int pages = Math.max(1, (events.length + 4) / 5);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new BehaviourHolder(definition.getKey(), page), 54,
                Component.text("Behaviour: " + definition.getDisplayName()));
        for (int row = 0; row < 5; row++) {
            int eventIndex = page * 5 + row;
            if (eventIndex >= events.length) {
                break;
            }
            BehaviourEvent behaviourEvent = events[eventIndex];
            inventory.setItem(row * 9, item(eventMaterial(behaviourEvent), behaviourEvent.displayName(), List.of(
                    ChatColor.GRAY + "Actions run from left to right"
            )));
            List<BehaviourAction> actions = definition.getBehaviourActions(behaviourEvent);
            for (int column = 0; column < 7; column++) {
                int slot = row * 9 + column + 2;
                if (column < actions.size()) {
                    BehaviourAction action = actions.get(column);
                    inventory.setItem(slot, item(actionMaterial(action.type()), (column + 1) + ". " + action.type().displayName(), List.of(
                            ChatColor.GRAY + (!action.type().requiresValue() || action.value() == null
                            ? "No setting required"
                            : action.value()),
                            ChatColor.YELLOW + "Left-click to replace",
                            ChatColor.RED + "Right-click to remove"
                    )));
                } else if (column == actions.size()) {
                    inventory.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, "Add Action", List.of(ChatColor.YELLOW + "Click to append")));
                }
            }
        }
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        }
        inventory.setItem(49, item(Material.BARRIER, "Back", List.of()));
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        player.openInventory(inventory);
    }

    private void openActionPicker(Player player, NpcDefinition definition, BehaviourEvent event, int actionIndex, int page) {
        Inventory inventory = Bukkit.createInventory(new ActionPickerHolder(definition.getKey(), event, actionIndex, page), 27,
                Component.text("Choose Action"));
        BehaviourActionType[] types = BehaviourActionType.values();
        for (int index = 0; index < types.length; index++) {
            BehaviourActionType type = types[index];
            inventory.setItem(10 + index, item(actionMaterial(type), type.displayName(), List.of(ChatColor.YELLOW + "Click to configure")));
        }
        inventory.setItem(22, item(Material.BARRIER, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void openBehaviourValuePicker(
            Player player,
            NpcDefinition definition,
            ActionPickerHolder action,
            BehaviourValuePickerType pickerType,
            int requestedValuePage
    ) {
        List<BehaviourPickerOption> options = pickerOptions(pickerType);
        int pages = Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int valuePage = Math.max(0, Math.min(requestedValuePage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new BehaviourValuePickerHolder(
                action.key(), action.event(), action.actionIndex(), action.page(), pickerType, valuePage), 54,
                Component.text(pickerType.title()));
        int from = valuePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, options.size());
        for (int index = from; index < to; index++) {
            BehaviourPickerOption option = options.get(index);
            List<String> lore = new ArrayList<>(option.lore());
            lore.add(ChatColor.YELLOW + "Click to select");
            inventory.setItem(index - from, item(option.material(), option.label(), lore));
        }
        if (options.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, "No Values Available", List.of(
                    ChatColor.GRAY + pickerType.emptyMessage()
            )));
        }
        if (valuePage > 0) {
            inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        }
        inventory.setItem(49, item(Material.BARRIER, "Back", List.of()));
        if (valuePage + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        player.openInventory(inventory);
    }

    private List<BehaviourPickerOption> pickerOptions(BehaviourValuePickerType pickerType) {
        return switch (pickerType) {
            case ROUTE ->
                routeRepository.findAll().stream()
                .map(route -> new BehaviourPickerOption(route.getKey(), route.getDisplayName(), Material.RAIL, List.of(
                ChatColor.DARK_GRAY + "Key: " + route.getKey(),
                ChatColor.GRAY + "" + route.getPoints().size() + " route point(s)"
                )))
                .toList();
            case WALK_SPEED ->
                java.util.Arrays.stream(WalkingSpeed.values())
                .map(speed -> new BehaviourPickerOption(speed.name().toLowerCase(java.util.Locale.ROOT),
                speed.displayName(), Material.FEATHER, List.of(
                ChatColor.GRAY + "" + speed.blocksPerSecond() + " blocks/second"
                )))
                .toList();
        };
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
        inventory.setItem(48, item(Material.BARRIER, "Back to Preset", List.of()));
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
                    ChatColor.AQUA + "Middle-click: move instance to you",
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
            inventory.setItem(51, item(Material.SUNFLOWER, "Refresh Instances", List.of(
                    ChatColor.GRAY + "Re-applies name, skin, and equipment",
                    ChatColor.YELLOW + "Click to refresh all copies"
            )));
        }
        inventory.setItem(49, item(Material.BARRIER, "Back to Preset", List.of()));
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
        } else if (holder instanceof BehaviourHolder behaviourHolder) {
            handleBehaviourClick(event, player, behaviourHolder);
        } else if (holder instanceof ActionPickerHolder pickerHolder) {
            handleActionPickerClick(event, player, pickerHolder);
        } else if (holder instanceof BehaviourValuePickerHolder valuePickerHolder) {
            handleBehaviourValuePickerClick(event, player, valuePickerHolder);
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
        if (behaviourService != null) {
            behaviourService.trigger(BehaviourEvent.RIGHT_CLICK, instance, event.getPlayer());
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
            routeGuiOpener.accept(player);
            return;
        }
        if (event.getRawSlot() == 47) {
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
            case 10 ->
                chatInputService.request(player, "Enter NPC display name:", value -> {
                    definition.setDisplayName(value.trim());
                    saveRefresh(definition);
                    openEditor(player, definition);
                });
            case 11 ->
                chatInputService.request(player,
                        "Enter an HTTPS skin image URL, texture hash, or 'default':",
                        value -> updateSkin(player, definition, value));
            case 12 -> {
                definition.setSpawnpoint(player.getLocation());
                definitionRepository.save(definition);
                player.sendMessage(Component.text("Preset spawnpoint updated. Existing instances were not moved."));
                openEditor(player, definition);
            }
            case 13 ->
                openInventoryEditor(player, definition);
            case 14 ->
                openDialogEditor(player, definition);
            case 15 -> {
                if (definition.getSpawnpoint() == null) {
                    player.sendMessage(Component.text("Set a spawnpoint first."));
                } else {
                    instanceRegistry.spawnPersistent(definition, definition.getSpawnpoint());
                    player.sendMessage(Component.text("Spawned a visible NPC instance."));
                }
                openEditor(player, definition);
            }
            case 16 ->
                openInstances(player, definition, 0);
            case 20 ->
                openBehaviours(player, definition, 0);
            case 21 -> {
                List<BehaviourAction> spawnActions = definition.getBehaviourActions(BehaviourEvent.SPAWN);
                int routeIndex = -1;
                for (int index = 0; index < spawnActions.size(); index++) {
                    if (spawnActions.get(index).type() == BehaviourActionType.SET_ROUTE) {
                        routeIndex = index;
                        break;
                    }
                }
                if (routeIndex < 0 && spawnActions.size() >= 7) {
                    player.sendMessage(Component.text("The Spawn event already has the maximum of 7 actions."));
                    return;
                }
                ActionPickerHolder action = new ActionPickerHolder(
                        definition.getKey(), BehaviourEvent.SPAWN,
                        routeIndex < 0 ? spawnActions.size() : routeIndex, 0
                );
                openBehaviourValuePicker(player, definition, action, BehaviourValuePickerType.ROUTE, 0);
            }
            case 23 ->
                openFightingEditor(player, definition);
            case 24 -> {
                if (event.isShiftClick()) {
                    openConfirmation(player, definition, ConfirmationAction.DELETE_DEFINITION);
                }
            }
            case 31 ->
                openMain(player);
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
            case 10 ->
                chatInputService.request(player, "Enter dialog lines separated with |:", value -> {
                    definition.setDialogLines(List.of(value.split("\\|")).stream()
                            .map(String::trim)
                            .filter(line -> !line.isBlank())
                            .toList());
                    saveRefresh(definition);
                    openDialogEditor(player, definition);
                });
            case 12 ->
                chatInputService.request(player, "Enter seconds per dialog line:", value -> {
                    try {
                        definition.setSecondsPerDialogLine(Integer.parseInt(value.trim()));
                        saveRefresh(definition);
                    } catch (NumberFormatException exception) {
                        player.sendMessage(Component.text("Enter a whole number of seconds."));
                    }
                    openDialogEditor(player, definition);
                });
            case 22 ->
                openEditor(player, definition);
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
            case 18 -> {
                definition.setCombatProfile(combat.withMaxHealth(combat.maxHealth() - CombatProfile.HEALTH_STEP));
                saveRefresh(definition);
                openFightingEditor(player, definition);
            }
            case 0 -> {
                definition.setCombatProfile(combat.withMaxHealth(combat.maxHealth() + CombatProfile.HEALTH_STEP));
                saveRefresh(definition);
                openFightingEditor(player, definition);
            }
            case 19 -> {
                definition.setCombatProfile(combat.withRespawnSeconds(
                        combat.respawnSeconds() - CombatProfile.RESPAWN_STEP_SECONDS
                ));
                definitionRepository.save(definition);
                openFightingEditor(player, definition);
            }
            case 1 -> {
                int respawnSeconds = (int) Math.min(
                        Integer.MAX_VALUE,
                        (long) combat.respawnSeconds() + CombatProfile.RESPAWN_STEP_SECONDS
                );
                definition.setCombatProfile(combat.withRespawnSeconds(respawnSeconds));
                definitionRepository.save(definition);
                openFightingEditor(player, definition);
            }
            case 5 -> {
                AggressionLevel aggression = combat.aggressionLevel().next();
                definition.setCombatProfile(combat.withAggressionLevel(aggression));
                definitionRepository.save(definition);
                player.sendMessage(Component.text("Aggression set to " + aggression.displayName() + "."));
                openFightingEditor(player, definition);
            }
            case 23 ->
                openEditor(player, definition);
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
            case 45 ->
                openInstances(player, definition, holder.page() - 1);
            case 47 ->
                openConfirmation(player, definition, ConfirmationAction.DELETE_INSTANCES);
            case 49 ->
                openEditor(player, definition);
            case 51 -> {
                instanceRegistry.refreshDefinition(definition);
                player.sendMessage(Component.text("Refreshed " + instanceRegistry.findByDefinition(definition).size() + " instance(s)."));
                openInstances(player, definition, holder.page());
            }
            case 53 ->
                openInstances(player, definition, holder.page() + 1);
            default -> {
                List<NpcInstance> instances = new ArrayList<>(instanceRegistry.findByDefinition(definition));
                int index = holder.page() * PAGE_SIZE + event.getRawSlot();
                if (event.getRawSlot() >= PAGE_SIZE || index < 0 || index >= instances.size()) {
                    return;
                }
                NpcInstance instance = instances.get(index);
                if (event.getClick() == ClickType.MIDDLE) {
                    Location destination = player.getLocation();
                    if (instanceRegistry.move(instance, destination)) {
                        player.sendMessage(Component.text("Moved NPC instance to your location."));
                    } else {
                        player.sendMessage(Component.text("Could not move the NPC instance."));
                    }
                    openInstances(player, definition, holder.page());
                } else if (event.isRightClick()) {
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
            case 45 ->
                openRouteAssignment(player, definition, holder.page() - 1);
            case 48 ->
                openEditor(player, definition);
            case 49 -> {
                definition.setMovementProfile(definition.getMovementProfile().withoutRoute());
                saveRefresh(definition);
                player.sendMessage(Component.text("Walking route cleared."));
                openEditor(player, definition);
            }
            case 53 ->
                openRouteAssignment(player, definition, holder.page() + 1);
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

    private void handleBehaviourClick(InventoryClickEvent event, Player player, BehaviourHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(holder.key()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 45) {
            openBehaviours(player, definition, holder.page() - 1);
            return;
        }
        if (slot == 49) {
            openEditor(player, definition);
            return;
        }
        if (slot == 53) {
            openBehaviours(player, definition, holder.page() + 1);
            return;
        }
        int row = slot / 9;
        int column = slot % 9 - 2;
        int eventIndex = holder.page() * 5 + row;
        if (row >= 5 || column < 0 || column >= 7 || eventIndex >= BehaviourEvent.values().length) {
            return;
        }
        BehaviourEvent behaviourEvent = BehaviourEvent.values()[eventIndex];
        List<BehaviourAction> actions = definition.getBehaviourActions(behaviourEvent);
        if (column < actions.size() && event.isRightClick()) {
            definition.removeBehaviourAction(behaviourEvent, column);
            definitionRepository.save(definition);
            openBehaviours(player, definition, holder.page());
        } else if (column <= actions.size()) {
            openActionPicker(player, definition, behaviourEvent, column, holder.page());
        }
    }

    private void handleActionPickerClick(InventoryClickEvent event, Player player, ActionPickerHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(holder.key()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 22) {
            openBehaviours(player, definition, holder.page());
            return;
        }
        int typeIndex = event.getRawSlot() - 10;
        if (typeIndex < 0 || typeIndex >= BehaviourActionType.values().length) {
            return;
        }
        BehaviourActionType type = BehaviourActionType.values()[typeIndex];
        if (type == BehaviourActionType.SET_ROUTE) {
            openBehaviourValuePicker(player, definition, holder, BehaviourValuePickerType.ROUTE, 0);
        } else if (type == BehaviourActionType.SET_WALK_SPEED) {
            openBehaviourValuePicker(player, definition, holder, BehaviourValuePickerType.WALK_SPEED, 0);
        } else if (!type.requiresValue()) {
            setAction(definition, holder, type, null);
            openBehaviours(player, definition, holder.page());
        } else {
            String prompt = switch (type) {
                case SEND_DIALOG ->
                    "Enter the dialog line:";
                case SHOW_HOLO_DIALOG ->
                    "Enter the hologram dialog line:";
                case RUN_CONSOLE_COMMAND ->
                    "Enter the command without a leading slash:";
                default ->
                    "Enter the action value:";
            };
            chatInputService.request(player, prompt, value -> {
                setAction(definition, holder, type, value);
                openBehaviours(player, definition, holder.page());
            });
        }
    }

    private void handleBehaviourValuePickerClick(
            InventoryClickEvent event,
            Player player,
            BehaviourValuePickerHolder holder
    ) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(holder.key()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        ActionPickerHolder action = new ActionPickerHolder(holder.key(), holder.event(), holder.actionIndex(), holder.behaviourPage());
        int slot = event.getRawSlot();
        if (slot == 45) {
            openBehaviourValuePicker(player, definition, action, holder.pickerType(), holder.valuePage() - 1);
            return;
        }
        if (slot == 49) {
            openActionPicker(player, definition, holder.event(), holder.actionIndex(), holder.behaviourPage());
            return;
        }
        if (slot == 53) {
            openBehaviourValuePicker(player, definition, action, holder.pickerType(), holder.valuePage() + 1);
            return;
        }
        List<BehaviourPickerOption> options = pickerOptions(holder.pickerType());
        int index = holder.valuePage() * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index < 0 || index >= options.size()) {
            return;
        }
        BehaviourPickerOption option = options.get(index);
        setAction(definition, action, holder.pickerType().actionType(), option.value());
        player.sendMessage(Component.text("Selected '" + option.label() + "'."));
        openBehaviours(player, definition, holder.behaviourPage());
    }

    private void setAction(NpcDefinition definition, ActionPickerHolder holder, BehaviourActionType type, String value) {
        List<BehaviourAction> actions = definition.getBehaviourActions(holder.event());
        BehaviourAction action = new BehaviourAction(type, value);
        if (holder.actionIndex() < actions.size()) {
            actions.set(holder.actionIndex(), action); 
        }else if (actions.size() < 7) {
            actions.add(action);
        }
        definition.setBehaviourActions(holder.event(), actions);
        definitionRepository.save(definition);
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
            if (!LootTier.isRowStarterSlot(index)) {
                contents[index] = inventory.getItem(index);
            }
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

    private void updateSkin(Player player, NpcDefinition definition, String input) {
        String normalized;
        try {
            normalized = SkinTextureUtil.normalizeTextureUrl(input);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(exception.getMessage()));
            openEditor(player, definition);
            return;
        }

        if (normalized == null || SkinTextureUtil.isMinecraftTextureUrl(normalized)) {
            pendingSkinUrls.remove(definition.getKey());
            definition.setSkinUrl(normalized);
            saveRefresh(definition);
            player.sendMessage(Component.text(normalized == null ? "Using the default skin." : "Skin updated."));
            openEditor(player, definition);
            return;
        }

        player.sendMessage(Component.text("Processing the skin image. This can take a few seconds..."));
        pendingSkinUrls.put(definition.getKey(), normalized);
        skinResolver.resolve(normalized).whenComplete((resolved, error)
                -> Bukkit.getScheduler().runTask(plugin,
                        () -> finishSkinUpdate(player, definition.getKey(), normalized, resolved, error))
        );
    }

    private void finishSkinUpdate(
            Player player,
            String definitionKey,
            String requestedUrl,
            ResolvedSkin resolved,
            Throwable error
    ) {
        if (!requestedUrl.equals(pendingSkinUrls.get(definitionKey))) {
            return;
        }
        pendingSkinUrls.remove(definitionKey);
        NpcDefinition current = definitionRepository.find(definitionKey).orElse(null);
        if (error != null) {
            if (player.isOnline()) {
                player.sendMessage(Component.text(rootMessage(error)));
                if (current != null) {
                    openEditor(player, current);
                }
            }
            return;
        }
        if (current == null) {
            return;
        }
        current.setResolvedSkin(resolved.url(), resolved.textureValue(), resolved.textureSignature());
        saveRefresh(current);
        if (player.isOnline()) {
            player.sendMessage(Component.text("Skin processed and updated."));
            openEditor(player, current);
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Could not process that skin image." : current.getMessage();
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
                || holder instanceof BehaviourHolder
                || holder instanceof ActionPickerHolder
                || holder instanceof BehaviourValuePickerHolder
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
            String texture = definition.getSkinTextureValue();
            if (texture == null) {
                texture = SkinTextureUtil.toTextureProperty(definition.getSkinUrl());
            }
            String signature = definition.getSkinTextureSignature();
            profile.setProperty(signature == null
                    ? new ProfileProperty("textures", texture)
                    : new ProfileProperty("textures", texture, signature));
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

    private ItemStack potionItem(PotionType potionType, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(potionType);
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

    private String respawnLabel(CombatProfile combat) {
        return combat.respawnSeconds() == 0 ? "Disabled (0 seconds)" : combat.respawnSeconds() + " seconds";
    }

    private Material aggressionMaterial(AggressionLevel aggressionLevel) {
        return switch (aggressionLevel) {
            case NONE ->
                Material.GRAY_DYE;
            case FLEE ->
                Material.RABBIT_FOOT;
            case FIGHT_BACK ->
                Material.SHIELD;
            case FIGHTS_ON_SIGHT ->
                Material.DIAMOND_SWORD;
        };
    }

    private Material eventMaterial(BehaviourEvent event) {
        return switch (event) {
            case COMBAT_ENTERED ->
                Material.IRON_SWORD;
            case COMBAT_EXITED ->
                Material.IRON_CHESTPLATE;
            case PLAYER_APPROACH ->
                Material.SPYGLASS;
            case PLAYER_LEAVES ->
                Material.ENDER_PEARL;
            case LEFT_CLICK ->
                Material.WOODEN_SWORD;
            case RIGHT_CLICK ->
                Material.LEVER;
            case DEATH ->
                Material.SKELETON_SKULL;
            case SPAWN ->
                Material.NETHER_STAR;
            case DAMAGE_TAKEN ->
                Material.RED_DYE;
            case HEAL ->
                Material.SPLASH_POTION;
            case LOW_HEALTH ->
                Material.GLISTERING_MELON_SLICE;
        };
    }

    private Material actionMaterial(BehaviourActionType type) {
        return switch (type) {
            case SEND_DIALOG ->
                Material.WRITABLE_BOOK;
            case SHOW_HOLO_DIALOG ->
                Material.NAME_TAG;
            case SET_ROUTE ->
                Material.RAIL;
            case RUN_CONSOLE_COMMAND ->
                Material.COMMAND_BLOCK;
            case START_COMBAT ->
                Material.DIAMOND_SWORD;
            case START_NAVIGATION ->
                Material.COMPASS;
            case STOP_NAVIGATION ->
                Material.BARRIER;
            case SET_WALK_SPEED ->
                Material.FEATHER;
        };
    }

    private String aggressionDescription(AggressionLevel aggressionLevel) {
        return switch (aggressionLevel) {
            case NONE ->
                ChatColor.GRAY + "Never reacts to nearby entities";
            case FLEE ->
                ChatColor.GRAY + "Runs away after taking entity damage";
            case FIGHT_BACK ->
                ChatColor.GRAY + "Attacks an entity that damages it";
            case FIGHTS_ON_SIGHT ->
                ChatColor.GRAY + "Attacks nearby players, mobs, and NPCs";
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

    private record BehaviourHolder(String key, int page) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record ActionPickerHolder(String key, BehaviourEvent event, int actionIndex, int page)
            implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BehaviourValuePickerHolder(
            String key,
            BehaviourEvent event,
            int actionIndex,
            int behaviourPage,
            BehaviourValuePickerType pickerType,
            int valuePage
            ) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BehaviourPickerOption(
            String value,
            String label,
            Material material,
            List<String> lore
            ) {

    }

    private enum BehaviourValuePickerType {
        ROUTE(BehaviourActionType.SET_ROUTE, "Select Route", "Create a route from the main preset menu first"),
        WALK_SPEED(BehaviourActionType.SET_WALK_SPEED, "Select Walk Speed", "No walking speeds are available");

        private final BehaviourActionType actionType;
        private final String title;
        private final String emptyMessage;

        BehaviourValuePickerType(BehaviourActionType actionType, String title, String emptyMessage) {
            this.actionType = actionType;
            this.title = title;
            this.emptyMessage = emptyMessage;
        }

        BehaviourActionType actionType() {
            return actionType;
        }

        String title() {
            return title;
        }

        String emptyMessage() {
            return emptyMessage;
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
