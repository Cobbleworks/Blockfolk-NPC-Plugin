package dev.blockfolk.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.ActionLocation;
import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.FightOptions;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.CustomEvent;
import dev.blockfolk.model.LootTier;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.NpcQuestion;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.QuestionOption;
import dev.blockfolk.model.RoutePoint;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.CustomEventRepository;
import dev.blockfolk.repository.RouteRepository;
import dev.blockfolk.runtime.NpcBehaviourService;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.runtime.NpcBehaviourService.NpcInventoryHolder;
import dev.blockfolk.util.ResolvedSkin;
import dev.blockfolk.util.SkinResolver;
import dev.blockfolk.util.SkinTextureUtil;
import net.kyori.adventure.text.Component;

public final class GuiService implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final List<BehaviourActionType> ANIMATION_ACTIONS = List.of(
            BehaviourActionType.SLEEP,
            BehaviourActionType.SWIM,
            BehaviourActionType.FALL_FLY,
            BehaviourActionType.STAND,
            BehaviourActionType.SNEAK,
            BehaviourActionType.WAVE,
            BehaviourActionType.JUMP
    );
    private static final Map<Integer, BehaviourActionType> ACTION_PICKER_ACTIONS = Map.ofEntries(
            // Dialogue and scripting
            Map.entry(1, BehaviourActionType.SEND_DIALOG),
            Map.entry(2, BehaviourActionType.SHOW_HOLO_DIALOG),
            Map.entry(3, BehaviourActionType.ASK_QUESTION),
            Map.entry(4, BehaviourActionType.EMIT_EVENT),
            Map.entry(5, BehaviourActionType.RUN_CONSOLE_COMMAND),
            Map.entry(7, BehaviourActionType.WAIT),
            // Movement and navigation
            Map.entry(10, BehaviourActionType.SET_ROUTE),
            Map.entry(11, BehaviourActionType.START_NAVIGATION),
            Map.entry(12, BehaviourActionType.STOP_NAVIGATION),
            Map.entry(13, BehaviourActionType.SET_WALK_SPEED),
            Map.entry(14, BehaviourActionType.MOVE_TO),
            Map.entry(15, BehaviourActionType.TELEPORT_TO),
            Map.entry(16, BehaviourActionType.FOLLOW),
            Map.entry(17, BehaviourActionType.UNFOLLOW),
            // World and inventory interaction
            Map.entry(19, BehaviourActionType.INTERACT),
            Map.entry(20, BehaviourActionType.MINE_BLOCKS),
            Map.entry(21, BehaviourActionType.TAKE_ITEM),
            Map.entry(22, BehaviourActionType.SHOW_INVENTORY),
            Map.entry(23, BehaviourActionType.DROP_INVENTORY),
            Map.entry(24, BehaviourActionType.HARVEST),
            // Combat
            Map.entry(28, BehaviourActionType.START_COMBAT),
            Map.entry(29, BehaviourActionType.CHANGE_FIGHT_OPTIONS)
    );
    private static final int ACTION_PICKER_ANIMATIONS_SLOT = 32;
    private static final int ACTION_PICKER_BACK_SLOT = 49;
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
    private final SkinResolver skinResolver;
    private final Consumer<Player> routeGuiOpener;
    private final RouteCreator routeCreator;
    private final CustomEventRepository customEventRepository;
    private final Consumer<Player> customEventGuiOpener;
    private final CustomEventCreator customEventCreator;
    private final NamespacedKey waypointActionKey;
    private final NamespacedKey waypointTokenKey;
    private NpcBehaviourService behaviourService;
    private final Set<UUID> explicitInventorySaves = new HashSet<>();
    private final Map<String, String> pendingSkinUrls = new HashMap<>();
    private final Map<UUID, WaypointSession> waypointSessions = new HashMap<>();
    private final Map<UUID, RouteActionWaypointSession> routeWaypointSessions = new HashMap<>();

    public GuiService(
            Plugin plugin,
            NpcDefinitionRepository definitionRepository,
            RouteRepository routeRepository,
            NpcInstanceRegistry instanceRegistry,
            ChatInputService chatInputService,
            SkinResolver skinResolver,
            Consumer<Player> routeGuiOpener,
            RouteCreator routeCreator,
            CustomEventRepository customEventRepository,
            Consumer<Player> customEventGuiOpener,
            CustomEventCreator customEventCreator
    ) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.routeRepository = routeRepository;
        this.instanceRegistry = instanceRegistry;
        this.chatInputService = chatInputService;
        this.skinResolver = skinResolver;
        this.routeGuiOpener = routeGuiOpener;
        this.routeCreator = routeCreator;
        this.customEventRepository = customEventRepository;
        this.customEventGuiOpener = customEventGuiOpener;
        this.customEventCreator = customEventCreator;
        this.waypointActionKey = new NamespacedKey(plugin, "behaviour-waypoint-action");
        this.waypointTokenKey = new NamespacedKey(plugin, "behaviour-waypoint-token");
    }

    public void setBehaviourService(NpcBehaviourService behaviourService) {
        this.behaviourService = behaviourService;
    }

    public void stop() {
        for (UUID playerId : List.copyOf(waypointSessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                finishWaypointSelection(player);
            }
        }
        waypointSessions.clear();
        for (UUID playerId : List.copyOf(routeWaypointSessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                finishRouteWaypointSelection(player);
            }
        }
        routeWaypointSessions.clear();
    }

    public void openWaypointActions(Player player, String routeKey, RoutePoint point) {
        NpcRoute route = routeRepository.find(routeKey).orElse(null);
        RoutePoint current = route == null ? null : route.findPoint(point).orElse(null);
        if (current == null) {
            player.sendMessage(Component.text("That route point no longer exists."));
            player.closeInventory();
            return;
        }
        Inventory inventory = Bukkit.createInventory(new RoutePointActionsHolder(route.getKey(), current), 27,
                Component.text("Waypoint Actions"));
        List<BehaviourAction> actions = current.actions();
        for (int index = 0; index < 7; index++) {
            int slot = 10 + index;
            if (index < actions.size()) {
                BehaviourAction action = actions.get(index);
                inventory.setItem(slot, item(actionMaterial(action.type()), (index + 1) + ". " + action.type().displayName(), List.of(
                        ChatColor.GRAY + actionValueDisplay(action),
                        ChatColor.YELLOW + "Left-click to replace",
                        ChatColor.RED + "Right-click to remove"
                )));
            } else if (index == actions.size()) {
                inventory.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, "Add Action", List.of(
                        ChatColor.YELLOW + "Click to append"
                )));
            }
        }
        inventory.setItem(22, item(Material.BARRIER, "Back to Route Editing", List.of(
                ChatColor.GRAY + "Close this menu and keep editing points"
        )));
        openInventory(player, inventory);
    }

    private void openRoutePointActionPicker(Player player, RoutePointActionPickerHolder holder) {
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Choose Waypoint Action"));
        for (Map.Entry<Integer, BehaviourActionType> entry : ACTION_PICKER_ACTIONS.entrySet()) {
            BehaviourActionType type = entry.getValue();
            inventory.setItem(entry.getKey(), item(actionMaterial(type), type.displayName(), List.of(
                    ChatColor.YELLOW + "Click to configure"
            )));
        }
        inventory.setItem(ACTION_PICKER_ANIMATIONS_SLOT, item(Material.ARMOR_STAND, "Animations", List.of(
                ChatColor.GRAY + "Poses, waving, and jumping",
                ChatColor.YELLOW + "Click to choose an animation"
        )));
        inventory.setItem(ACTION_PICKER_BACK_SLOT, item(Material.BARRIER, "Back", List.of()));
        openInventory(player, inventory);
    }

    private void openRoutePointAnimationPicker(Player player, RoutePointActionPickerHolder action) {
        Inventory inventory = Bukkit.createInventory(new RoutePointAnimationPickerHolder(
                action.routeKey(), action.point(), action.actionIndex()), 27, Component.text("Choose Animation"));
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int index = 0; index < ANIMATION_ACTIONS.size(); index++) {
            BehaviourActionType type = ANIMATION_ACTIONS.get(index);
            inventory.setItem(slots[index], item(actionMaterial(type), type.displayName(), List.of(
                    ChatColor.YELLOW + "Click to select"
            )));
        }
        inventory.setItem(22, item(Material.BARRIER, "Back", List.of()));
        openInventory(player, inventory);
    }

    private void openRoutePointValuePicker(Player player, RoutePointActionPickerHolder action,
            BehaviourValuePickerType pickerType, int requestedPage) {
        openRoutePointValuePicker(player, action, pickerType, "", requestedPage);
    }

    private void openRoutePointValuePicker(Player player, RoutePointActionPickerHolder action,
            BehaviourValuePickerType pickerType, String folder, int requestedPage) {
        List<BehaviourPickerOption> options = pickerOptions(pickerType, folder);
        int pages = Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new RoutePointValuePickerHolder(
                action.routeKey(), action.point(), action.actionIndex(), pickerType, folder, page), 54,
                Component.text(pickerType.title()));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, options.size());
        for (int index = from; index < to; index++) {
            BehaviourPickerOption option = options.get(index);
            List<String> lore = new ArrayList<>(option.lore());
            lore.add(ChatColor.YELLOW + (option.folder() ? "Click to open" : "Click to select"));
            inventory.setItem(index - from, item(option.icon(), option.label(), lore));
        }
        if (options.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, "No Values Available", List.of(
                    ChatColor.GRAY + pickerType.emptyMessage()
            )));
        }
        if (page > 0) {
            inventory.setItem(47, item(Material.ARROW, "Previous Page", List.of()));
        }
        if (pickerType == BehaviourValuePickerType.ROUTE && !folder.isEmpty()) {
            inventory.setItem(45, item(Material.ARROW, "Up One Group", List.of()));
        }
        inventory.setItem(49, item(Material.BARRIER, "Back", List.of()));
        if (pickerType == BehaviourValuePickerType.ROUTE) {
            inventory.setItem(51, item(Material.EMERALD, "Create Route", List.of(
                    ChatColor.GRAY + "New route in " + (folder.isEmpty() ? "the root group" : folder),
                    ChatColor.YELLOW + "Click, then enter its name")));
        } else if (pickerType == BehaviourValuePickerType.CUSTOM_EVENT) {
            inventory.setItem(51, item(Material.EMERALD, "Create New Event", List.of(
                    ChatColor.GRAY + "Creates and selects a custom event",
                    ChatColor.YELLOW + "Click, then enter its name")));
        }
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        openInventory(player, inventory);
    }

    public void openMain(Player player) {
        openMain(player, 0);
    }

    public void beginCreate(Player player) {
        beginCreate(player, 0);
    }

    public void openMain(Player player, int requestedPage) {
        List<NpcDefinition> definitions = new ArrayList<>(definitionRepository.findAll());
        int pages = Math.max(1, (definitions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new MainHolder(page), 54, Component.text("Blockfolk Presets"));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, definitions.size());
        for (int index = from; index < to; index++) {
            NpcDefinition definition = definitions.get(index);
            int instances = instanceRegistry.findByDefinition(definition).size();
            inventory.setItem(index - from, definitionIcon(definition, List.of(
                    ChatColor.DARK_GRAY + "Key: " + definition.getKey(),
                    ChatColor.GRAY + "Instances: " + ChatColor.WHITE + instances,
                    statusLine(definition),
                    ChatColor.YELLOW + "Click to manage",
                    ChatColor.RED + "Shift + right-click to delete"
            )));
        }
        inventory.setItem(45, item(Material.MAP, "Manage Routes", List.of(
                ChatColor.GRAY + "Create and edit NPC walking routes",
                ChatColor.YELLOW + "Click to open route setup"
        )));
        inventory.setItem(46, item(Material.BELL, "Custom Events", List.of(
                ChatColor.GRAY + "Define events NPCs can emit and react to",
                ChatColor.YELLOW + "Click to manage custom events"
        )));
        if (page > 0) {
            inventory.setItem(47, item(Material.ARROW, "Previous Page", List.of(ChatColor.GRAY + "Page " + page + " of " + pages)));
        }
        inventory.setItem(49, item(Material.NETHER_STAR, "Blockfolk Overview", List.of(
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
        openInventory(player, inventory);
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
        inventory.setItem(14, item(Material.CHEST, "Equipment", List.of(
                ChatColor.GRAY + "Armor, hands, and stored inventory",
                ChatColor.YELLOW + "Click to edit"
        )));
        if (instances == 0) {
            inventory.setItem(16, item(Material.ARMOR_STAND, "Spawn NPC", List.of(
                    ChatColor.GRAY + "Creates the first visible NPC",
                    ChatColor.GRAY + "at the preset spawnpoint",
                    ChatColor.YELLOW + "Click to spawn"
            )));
        } else {
            inventory.setItem(16, item(Material.ENDER_EYE, "Manage Instances", List.of(
                    ChatColor.GRAY + "" + instances + " spawned instance(s)",
                    ChatColor.YELLOW + "Teleport, move, remove, or spawn copies"
            )));
        }
        int behaviourCount = java.util.Arrays.stream(BehaviourEvent.values())
                .mapToInt(event -> definition.getBehaviourActions(event).size()).sum();
        inventory.setItem(13, item(Material.COMMAND_BLOCK, "Event Behaviour", List.of(
                ChatColor.GRAY + "" + behaviourCount + " configured action(s)",
                ChatColor.GRAY + "Build event-to-action sequences",
                ChatColor.YELLOW + "Click to configure"
        )));
        inventory.setItem(22, item(Material.BELL, "Custom Event Behaviour", List.of(
                ChatColor.GRAY + "" + definition.customEventActionCount() + " configured action(s)",
                ChatColor.GRAY + "React to globally emitted custom events",
                ChatColor.YELLOW + "Click to configure"
        )));
        CombatProfile combat = definition.getCombatProfile();
        inventory.setItem(15, item(Material.IRON_SWORD, "Fighting", List.of(
                ChatColor.GRAY + "Health: " + ChatColor.WHITE + healthLabel(combat),
                ChatColor.GRAY + "Respawn: " + ChatColor.WHITE + respawnLabel(combat),
                ChatColor.GRAY + "Aggression: " + ChatColor.WHITE + combat.attackReaction().displayName(),
                ChatColor.GRAY + "Attack targets: " + ChatColor.WHITE + enabledTargetCount(combat) + "/4",
                ChatColor.GRAY + "Alliance: " + ChatColor.WHITE + allianceLabel(combat),
                ChatColor.GRAY + "Boss bar: " + ChatColor.WHITE + (combat.showBossBar() ? "Shown nearby" : "Hidden"),
                ChatColor.YELLOW + "Click to configure combat"
        )));
        inventory.setItem(31, item(Material.BARRIER, "Back to Presets", List.of()));
        openInventory(player, inventory);
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
        openInventory(player, inventory);
    }

    public void openFightingEditor(Player player, NpcDefinition definition) {
        CombatProfile combat = definition.getCombatProfile();
        Inventory inventory = Bukkit.createInventory(new FightingHolder(definition.getKey()), 36,
                Component.text("Fighting: " + definition.getDisplayName()));
        inventory.setItem(1, item(Material.LIME_DYE, "+ " + CombatProfile.HEALTH_STEP + " Health", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + healthLabel(combat),
                ChatColor.YELLOW + "Click to increase max health",
                ChatColor.DARK_GRAY + "Shift-click for x10"
        )));
        inventory.setItem(10, combat.invulnerable()
                ? item(Material.TOTEM_OF_UNDYING, "Max Health: " + healthLabel(combat), List.of(
                        ChatColor.GREEN + "This NPC cannot be damaged",
                        ChatColor.DARK_GRAY + "Set health to 0 for invulnerability"
                ))
                : potionItem(PotionType.HEALING, "Max Health: " + healthLabel(combat), List.of(
                        ChatColor.GRAY + "The NPC is removed when killed",
                        ChatColor.DARK_GRAY + "Set health to 0 for invulnerability"
                )));
        inventory.setItem(19, item(Material.RED_DYE, "- " + CombatProfile.HEALTH_STEP + " Health", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + healthLabel(combat),
                ChatColor.YELLOW + "Click to decrease max health",
                ChatColor.DARK_GRAY + "Shift-click for x10"
        )));
        inventory.setItem(3, item(Material.LIME_DYE, "+ " + CombatProfile.RESPAWN_STEP_SECONDS + " Seconds", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + respawnLabel(combat),
                ChatColor.YELLOW + "Click to increase respawn time",
                ChatColor.DARK_GRAY + "Shift-click for x10"
        )));
        inventory.setItem(12, item(combat.respawnSeconds() == 0 ? Material.BARRIER : Material.CLOCK,
                "Respawn Time: " + respawnLabel(combat), List.of(
                combat.respawnSeconds() == 0
                ? ChatColor.GRAY + "Killed NPCs will not respawn"
                : ChatColor.GREEN + "Respawns at the preset spawn point",
                definition.getSpawnpoint() == null
                ? ChatColor.RED + "A preset spawn point is required"
                : ChatColor.DARK_GRAY + "Preset spawn point is configured"
        )));
        inventory.setItem(21, item(Material.RED_DYE, "- " + CombatProfile.RESPAWN_STEP_SECONDS + " Seconds", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + respawnLabel(combat),
                ChatColor.YELLOW + "Click to decrease respawn time",
                ChatColor.DARK_GRAY + "Shift-click for x10"
        )));
        inventory.setItem(15, toggleItem(Material.WITHER_SKELETON_SKULL, "Show Boss Bar", combat.showBossBar(),
                "Shows current HP to players within 16 blocks"));
        inventory.setItem(14, item(Material.TARGET, "Targets & Behaviour", List.of(
                ChatColor.GRAY + "Aggression: " + ChatColor.WHITE + combat.attackReaction().displayName(),
                ChatColor.GRAY + "Attack targets enabled: " + ChatColor.WHITE + enabledTargetCount(combat) + "/4",
                ChatColor.YELLOW + "Click to configure"
        )));
        inventory.setItem(16, item(Material.NAME_TAG, "Alliance", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + allianceLabel(combat),
                ChatColor.GRAY + "NPCs with the same alliance will not fight",
                ChatColor.YELLOW + "Click to enter text"
        )));
        inventory.setItem(31, item(Material.BARRIER, "Back", List.of()));
        openInventory(player, inventory);
    }

    public void openTargetsAndBehaviour(Player player, NpcDefinition definition) {
        CombatProfile combat = definition.getCombatProfile();
        Inventory inventory = Bukkit.createInventory(new TargetsHolder(definition.getKey()), 27,
                Component.text("Targets & Behaviour: " + definition.getDisplayName()));
        inventory.setItem(10, item(reactionMaterial(combat.attackReaction()), "Aggression Level", List.of(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + combat.attackReaction().displayName(),
                reactionDescription(combat.attackReaction()),
                ChatColor.YELLOW + "Click to cycle"
        )));
        inventory.setItem(12, toggleItem(Material.ZOMBIE_HEAD, "Target Mobs", combat.targetMobs(),
                "Allows attacks against non-animal mobs"));
        inventory.setItem(13, toggleItem(Material.PORKCHOP, "Target Animals", combat.targetAnimals(),
                "Allows attacks against animals"));
        inventory.setItem(14, toggleItem(Material.PLAYER_HEAD, "Target Players", combat.targetPlayers(),
                "Allows attacks against survival and adventure players"));
        inventory.setItem(15, toggleItem(Material.ARMOR_STAND, "Target Other NPCs", combat.targetNpcs(),
                "Allows attacks against vulnerable NPCs"));
        inventory.setItem(22, item(Material.BARRIER, "Back", List.of()));
        openInventory(player, inventory);
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
            List<BehaviourAction> actions = definition.getBehaviourActions(behaviourEvent);
            inventory.setItem(row * 9, item(eventMaterial(behaviourEvent), behaviourEvent.displayName(),
                    actionSummaryLore(List.of(ChatColor.GRAY + "Actions run from left to right"), actions)));
            inventory.setItem(row * 9 + 1, item(Material.LIME_STAINED_GLASS_PANE, "Add Action", List.of(
                    ChatColor.YELLOW + "Click to append"
            )));
            for (int column = 0; column < 7; column++) {
                int slot = row * 9 + column + 2;
                if (column < actions.size()) {
                    BehaviourAction action = actions.get(column);
                    inventory.setItem(slot, item(actionMaterial(action.type()), (column + 1) + ". " + action.type().displayName(), List.of(
                            ChatColor.GRAY + actionValueDisplay(action),
                            ChatColor.YELLOW + "Left-click to replace",
                            ChatColor.RED + "Right-click to remove"
                    )));
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
        openInventory(player, inventory);
    }

    public void openCustomBehaviours(Player player, NpcDefinition definition, int requestedPage) {
        List<CustomEvent> events = new ArrayList<>(customEventRepository.findAll());
        int pages = Math.max(1, (events.size() + 4) / 5);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new CustomBehaviourHolder(definition.getKey(), page), 54,
                Component.text("Custom Behaviour: " + definition.getDisplayName()));
        for (int row = 0; row < 5; row++) {
            int eventIndex = page * 5 + row;
            if (eventIndex >= events.size()) break;
            CustomEvent customEvent = events.get(eventIndex);
            List<BehaviourAction> actions = definition.getCustomEventActions(customEvent.getName());
            inventory.setItem(row * 9, item(customEventIcon(customEvent), customEvent.getName(), actionSummaryLore(List.of(
                    ChatColor.GRAY + (customEvent.getDescription().isBlank() ? "No description" : customEvent.getDescription()),
                    ChatColor.GRAY + "Actions run from left to right"), actions)));
            inventory.setItem(row * 9 + 1, item(Material.LIME_STAINED_GLASS_PANE, "Add Action", List.of(
                    ChatColor.YELLOW + "Click to append")));
            for (int column = 0; column < Math.min(7, actions.size()); column++) {
                BehaviourAction action = actions.get(column);
                inventory.setItem(row * 9 + column + 2,
                        item(actionMaterial(action.type()), (column + 1) + ". " + action.type().displayName(), List.of(
                                ChatColor.GRAY + actionValueDisplay(action),
                                ChatColor.YELLOW + "Left-click to replace",
                                ChatColor.RED + "Right-click to remove")));
            }
        }
        if (events.isEmpty()) inventory.setItem(22, item(Material.GRAY_DYE, "No Custom Events", List.of(
                ChatColor.GRAY + "Create one from the Custom Events main menu")));
        if (page > 0) inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        inventory.setItem(49, item(Material.BARRIER, "Back", List.of()));
        if (page + 1 < pages) inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        openInventory(player, inventory);
    }

    private void openActionPicker(Player player, NpcDefinition definition, BehaviourEvent event, int actionIndex, int page) {
        openActionPicker(player, definition, event, null, actionIndex, page);
    }

    private void openActionPicker(Player player, NpcDefinition definition, BehaviourEvent event, String customEvent,
            int actionIndex, int page) {
        Inventory inventory = Bukkit.createInventory(new ActionPickerHolder(definition.getKey(), event, customEvent, actionIndex, page), 54,
                Component.text("Choose Action"));
        for (Map.Entry<Integer, BehaviourActionType> entry : ACTION_PICKER_ACTIONS.entrySet()) {
            BehaviourActionType type = entry.getValue();
            inventory.setItem(entry.getKey(), item(actionMaterial(type), type.displayName(), List.of(ChatColor.YELLOW + "Click to configure")));
        }
        inventory.setItem(ACTION_PICKER_ANIMATIONS_SLOT, item(Material.ARMOR_STAND, "Animations", List.of(
                ChatColor.GRAY + "Poses, waving, and jumping",
                ChatColor.YELLOW + "Click to choose an animation"
        )));
        inventory.setItem(ACTION_PICKER_BACK_SLOT, item(Material.BARRIER, "Back", List.of()));
        openInventory(player, inventory);
    }

    private void openAnimationPicker(Player player, ActionPickerHolder action) {
        Inventory inventory = Bukkit.createInventory(new AnimationPickerHolder(
                action.key(), action.event(), action.customEvent(), action.actionIndex(), action.page()), 27,
                Component.text("Choose Animation"));
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int index = 0; index < ANIMATION_ACTIONS.size(); index++) {
            BehaviourActionType type = ANIMATION_ACTIONS.get(index);
            inventory.setItem(slots[index], item(actionMaterial(type), type.displayName(), List.of(
                    ChatColor.YELLOW + "Click to select"
            )));
        }
        inventory.setItem(22, item(Material.BARRIER, "Back", List.of()));
        openInventory(player, inventory);
    }

    private void openBehaviourValuePicker(
            Player player,
            NpcDefinition definition,
            ActionPickerHolder action,
            BehaviourValuePickerType pickerType,
            int requestedValuePage
    ) {
        openBehaviourValuePicker(player, definition, action, pickerType, "", requestedValuePage);
    }

    private void openBehaviourValuePicker(Player player, NpcDefinition definition, ActionPickerHolder action,
            BehaviourValuePickerType pickerType, String folder, int requestedValuePage) {
        List<BehaviourPickerOption> options = pickerOptions(pickerType, folder);
        int pages = Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int valuePage = Math.max(0, Math.min(requestedValuePage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new BehaviourValuePickerHolder(
                action.key(), action.event(), action.customEvent(), action.actionIndex(), action.page(), pickerType, folder, valuePage), 54,
                Component.text(pickerType.title()));
        int from = valuePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, options.size());
        for (int index = from; index < to; index++) {
            BehaviourPickerOption option = options.get(index);
            List<String> lore = new ArrayList<>(option.lore());
            lore.add(ChatColor.YELLOW + (option.folder() ? "Click to open" : "Click to select"));
            inventory.setItem(index - from, item(option.icon(), option.label(), lore));
        }
        if (options.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, "No Values Available", List.of(
                    ChatColor.GRAY + pickerType.emptyMessage()
            )));
        }
        if (valuePage > 0) {
            inventory.setItem(47, item(Material.ARROW, "Previous Page", List.of()));
        }
        if (pickerType == BehaviourValuePickerType.ROUTE && !folder.isEmpty()) {
            inventory.setItem(45, item(Material.ARROW, "Up One Group", List.of()));
        }
        inventory.setItem(49, item(Material.BARRIER, "Back", List.of()));
        if (pickerType == BehaviourValuePickerType.ROUTE) {
            inventory.setItem(51, item(Material.EMERALD, "Create Route", List.of(
                    ChatColor.GRAY + "New route in " + (folder.isEmpty() ? "the root group" : folder),
                    ChatColor.YELLOW + "Click, then enter its name")));
        } else if (pickerType == BehaviourValuePickerType.CUSTOM_EVENT) {
            inventory.setItem(51, item(Material.EMERALD, "Create New Event", List.of(
                    ChatColor.GRAY + "Creates and selects a custom event",
                    ChatColor.YELLOW + "Click, then enter its name")));
        }
        if (valuePage + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        openInventory(player, inventory);
    }

    private List<BehaviourPickerOption> pickerOptions(BehaviourValuePickerType pickerType) {
        return pickerOptions(pickerType, "");
    }

    private List<BehaviourPickerOption> pickerOptions(BehaviourValuePickerType pickerType, String folder) {
        return switch (pickerType) {
            case ROUTE -> routePickerOptions(folder);
            case WALK_SPEED ->
                java.util.Arrays.stream(WalkingSpeed.values())
                .map(speed -> new BehaviourPickerOption(speed.name().toLowerCase(java.util.Locale.ROOT),
                speed.displayName(), new ItemStack(Material.FEATHER), List.of(
                ChatColor.GRAY + "" + speed.blocksPerSecond() + " blocks/second"
                ), false))
                .toList();
            case CUSTOM_EVENT ->
                customEventRepository.findAll().stream()
                .map(event -> new BehaviourPickerOption(event.getName(), event.getName(), customEventIcon(event), List.of(
                        ChatColor.GRAY + (event.getDescription().isBlank() ? "No description" : event.getDescription())), false))
                .toList();
        };
    }

    private List<BehaviourPickerOption> routePickerOptions(String folder) {
        String prefix = folder.isEmpty() ? "" : folder + "/";
        Map<String, BehaviourPickerOption> options = new LinkedHashMap<>();
        for (NpcRoute route : routeRepository.findAll()) {
            if (!route.getKey().startsWith(prefix)) continue;
            String rest = route.getKey().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String label = rest.substring(0, slash);
                String path = prefix + label;
                BehaviourPickerOption old = options.get(path);
                int count = old == null ? 1 : Integer.parseInt(old.lore().getFirst().split(" ")[0]) + 1;
                options.put(path, new BehaviourPickerOption(path, label, new ItemStack(Material.CHEST),
                        List.of(count + " route(s)", ChatColor.DARK_GRAY + path), true));
            } else {
                options.put(route.getKey(), new BehaviourPickerOption(route.getKey(), route.getDisplayName(), routeIcon(route), List.of(
                        ChatColor.DARK_GRAY + "Key: " + route.getKey(),
                        ChatColor.GRAY + "" + route.getPoints().size() + " route point(s)"), false));
            }
        }
        return new ArrayList<>(options.values());
    }

    private static String parentFolder(String folder) {
        int slash = folder.lastIndexOf('/');
        return slash < 0 ? "" : folder.substring(0, slash);
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
            inventory.setItem(index - from, item(routeIcon(route), route.getDisplayName(), List.of(
                    ChatColor.DARK_GRAY + "Key: " + route.getKey(),
                    ChatColor.GRAY + "Key points: " + ChatColor.WHITE + route.getPoints().size(),
                    selected ? ChatColor.GREEN + "Currently assigned" : ChatColor.YELLOW + "Click to assign"
            )));
        }
        if (routes.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, "No Routes", List.of(
                    ChatColor.GRAY + "Create one with /bf routes"
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
        openInventory(player, inventory);
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
                    ChatColor.GRAY + "Use Spawn Another Here below to create one."
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
        inventory.setItem(50, item(Material.ARMOR_STAND, "Spawn Another Here", List.of(
                ChatColor.GRAY + "Creates another visible persistent NPC",
                ChatColor.GRAY + "at your current location",
                ChatColor.YELLOW + "Click to spawn"
        )));
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        openInventory(player, inventory);
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
        } else if (holder instanceof FightingHolder fightingHolder) {
            handleFightingClick(event, player, fightingHolder.key());
        } else if (holder instanceof TargetsHolder targetsHolder) {
            handleTargetsClick(event, player, targetsHolder.key());
        } else if (holder instanceof EquipmentHolder equipmentHolder) {
            handleEquipmentClick(event, player, equipmentHolder.key());
        } else if (holder instanceof InstancesHolder instancesHolder) {
            handleInstancesClick(event, player, instancesHolder);
        } else if (holder instanceof RouteAssignmentHolder routeHolder) {
            handleRouteAssignmentClick(event, player, routeHolder);
        } else if (holder instanceof BehaviourHolder behaviourHolder) {
            handleBehaviourClick(event, player, behaviourHolder);
        } else if (holder instanceof CustomBehaviourHolder customBehaviourHolder) {
            handleCustomBehaviourClick(event, player, customBehaviourHolder);
        } else if (holder instanceof ActionPickerHolder pickerHolder) {
            handleActionPickerClick(event, player, pickerHolder);
        } else if (holder instanceof AnimationPickerHolder animationHolder) {
            handleAnimationPickerClick(event, player, animationHolder);
        } else if (holder instanceof BehaviourValuePickerHolder valuePickerHolder) {
            handleBehaviourValuePickerClick(event, player, valuePickerHolder);
        } else if (holder instanceof RoutePointActionsHolder routePointHolder) {
            handleRoutePointActionsClick(event, player, routePointHolder);
        } else if (holder instanceof RoutePointActionPickerHolder routePointPicker) {
            handleRoutePointActionPickerClick(event, player, routePointPicker);
        } else if (holder instanceof RoutePointAnimationPickerHolder routePointAnimation) {
            handleRoutePointAnimationPickerClick(event, player, routePointAnimation);
        } else if (holder instanceof RoutePointValuePickerHolder routePointValuePicker) {
            handleRoutePointValuePickerClick(event, player, routePointValuePicker);
        } else if (holder instanceof QuestionEditorHolder questionEditor) {
            handleQuestionEditorClick(event, player, questionEditor);
        } else if (holder instanceof QuestionBranchHolder questionBranch) {
            handleQuestionBranchClick(event, player, questionBranch);
        } else if (holder instanceof QuestionBranchPickerHolder branchPicker) {
            handleQuestionBranchPickerClick(event, player, branchPicker);
        } else if (holder instanceof ConfirmationHolder confirmationHolder) {
            handleConfirmationClick(event, player, confirmationHolder);
        } else if (holder instanceof NpcInventoryHolder) {
            // This is a real, editable container. Bukkit handles normal transfers.
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
        if (event.getPlayer().isSneaking() && event.getPlayer().hasPermission("blockfolk.admin")) {
            openEditor(event.getPlayer(), definition);
            return;
        }
        if (behaviourService != null) {
            behaviourService.trigger(BehaviourEvent.RIGHT_CLICK, instance, event.getPlayer());
        }
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
        if (event.getInventory().getHolder() instanceof NpcInventoryHolder holder) {
            instanceRegistry.findById(holder.instanceId())
                    .ifPresent(instance -> {
                        if (behaviourService != null) {
                            behaviourService.updateTemporaryInventory(instance,
                                    event.getInventory().getContents(), event.getPlayer());
                        } else {
                            instance.setTemporaryInventoryContents(event.getInventory().getContents());
                        }
                    });
            return;
        }
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
        if (event.getRawSlot() == 46) {
            customEventGuiOpener.accept(player);
            return;
        }
        if (event.getRawSlot() == 47) {
            openMain(player, page - 1);
            return;
        }
        if (event.getRawSlot() == 51) {
            beginCreate(player, page);
            return;
        }
        if (event.getRawSlot() == 53) {
            openMain(player, page + 1);
            return;
        }
        List<NpcDefinition> definitions = new ArrayList<>(definitionRepository.findAll());
        int index = page * PAGE_SIZE + event.getRawSlot();
        if (event.getRawSlot() < PAGE_SIZE && index >= 0 && index < definitions.size()) {
            NpcDefinition definition = definitions.get(index);
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                openConfirmation(player, definition, ConfirmationAction.DELETE_DEFINITION, page);
            } else {
                openEditor(player, definition);
            }
        }
    }

    private void beginCreate(Player player, int returnPage) {
        chatInputService.request(player, "Enter a new NPC name:", value -> {
            NpcDefinition definition = NpcDefinition.create(value);
            if (definitionRepository.find(definition.getKey()).isPresent()) {
                player.sendMessage(Component.text("An NPC with that key already exists."));
                openMain(player, returnPage);
                return;
            }
            definition.setSpawnpoint(player.getLocation());
            definitionRepository.save(definition);
            instanceRegistry.spawnPersistent(definition, definition.getSpawnpoint());
            openEditor(player, definition);
        });
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
            case 14 ->
                openInventoryEditor(player, definition);
            case 16 -> {
                if (instanceRegistry.findByDefinition(definition).isEmpty()) {
                    if (definition.getSpawnpoint() == null) {
                        player.sendMessage(Component.text("Set a spawnpoint first."));
                    } else {
                        instanceRegistry.spawnPersistent(definition, definition.getSpawnpoint());
                        player.sendMessage(Component.text("Spawned a visible NPC instance."));
                    }
                    openEditor(player, definition);
                } else {
                    openInstances(player, definition, 0);
                }
            }
            case 13 ->
                openBehaviours(player, definition, 0);
            case 22 ->
                openCustomBehaviours(player, definition, 0);
            case 15 ->
                openFightingEditor(player, definition);
            case 31 ->
                openMain(player);
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
        int multiplier = event.isShiftClick() ? 10 : 1;
        switch (event.getRawSlot()) {
            case 19 -> {
                definition.setCombatProfile(combat.withMaxHealth(
                        combat.maxHealth() - CombatProfile.HEALTH_STEP * multiplier));
                saveRefresh(definition);
                openFightingEditor(player, definition);
            }
            case 1 -> {
                definition.setCombatProfile(combat.withMaxHealth(
                        combat.maxHealth() + CombatProfile.HEALTH_STEP * multiplier));
                saveRefresh(definition);
                openFightingEditor(player, definition);
            }
            case 21 -> {
                definition.setCombatProfile(combat.withRespawnSeconds(
                        combat.respawnSeconds() - CombatProfile.RESPAWN_STEP_SECONDS * multiplier
                ));
                definitionRepository.save(definition);
                openFightingEditor(player, definition);
            }
            case 3 -> {
                int respawnSeconds = (int) Math.min(
                        Integer.MAX_VALUE,
                        (long) combat.respawnSeconds() + CombatProfile.RESPAWN_STEP_SECONDS * multiplier
                );
                definition.setCombatProfile(combat.withRespawnSeconds(respawnSeconds));
                definitionRepository.save(definition);
                openFightingEditor(player, definition);
            }
            case 14 -> {
                openTargetsAndBehaviour(player, definition);
            }
            case 15 -> {
                definition.setCombatProfile(combat.withShowBossBar(!combat.showBossBar()));
                definitionRepository.save(definition);
                openFightingEditor(player, definition);
            }
            case 16 ->
                chatInputService.request(player, "Enter an alliance, or type clear to remove it:", value -> {
                    String alliance = value.equalsIgnoreCase("clear") ? null : value;
                    definition.setCombatProfile(definition.getCombatProfile().withAlliance(alliance));
                    saveRefresh(definition);
                    openFightingEditor(player, definition);
                });
            case 31 ->
                openEditor(player, definition);
            default -> {
            }
        }
    }

    private void handleTargetsClick(InventoryClickEvent event, Player player, String key) {
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
        CombatProfile updated = switch (event.getRawSlot()) {
            case 10 ->
                combat.withAttackReaction(combat.attackReaction().next());
            case 12 ->
                combat.withTargetMobs(!combat.targetMobs());
            case 13 ->
                combat.withTargetAnimals(!combat.targetAnimals());
            case 14 ->
                combat.withTargetPlayers(!combat.targetPlayers());
            case 15 ->
                combat.withTargetNpcs(!combat.targetNpcs());
            default ->
                null;
        };
        if (updated != null) {
            definition.setCombatProfile(updated);
            definitionRepository.save(definition);
            openTargetsAndBehaviour(player, definition);
        } else if (event.getRawSlot() == 22) {
            openFightingEditor(player, definition);
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
                openConfirmation(player, definition, ConfirmationAction.DELETE_INSTANCES, holder.page());
            case 49 ->
                openEditor(player, definition);
            case 50 -> {
                instanceRegistry.spawnPersistent(definition, player.getLocation());
                player.sendMessage(Component.text("Spawned another NPC instance at your location."));
                openInstances(player, definition, holder.page());
            }
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
                    if (instanceRegistry.relocate(instance, destination)) {
                        player.sendMessage(Component.text("Moved NPC instance and its spawn location to you."));
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
        if (row >= 5 || eventIndex >= BehaviourEvent.values().length) {
            return;
        }
        BehaviourEvent behaviourEvent = BehaviourEvent.values()[eventIndex];
        List<BehaviourAction> actions = definition.getBehaviourActions(behaviourEvent);
        if (slot % 9 == 1) {
            // "Add Action" button in column 1 — always appends at the end
            openActionPicker(player, definition, behaviourEvent, actions.size(), holder.page());
        } else if (column < 0 || column >= 7) {
            return;
        } else if (column < actions.size() && event.isRightClick()) {
            definition.removeBehaviourAction(behaviourEvent, column);
            definitionRepository.save(definition);
            openBehaviours(player, definition, holder.page());
        } else if (column < actions.size() && actions.get(column).type() == BehaviourActionType.ASK_QUESTION
                && !event.isShiftClick()) {
            openQuestionEditor(player, QuestionTarget.definition(definition.getKey(), behaviourEvent,
                    null, column, holder.page()));
        } else if (column <= actions.size()) {
            openActionPicker(player, definition, behaviourEvent, column, holder.page());
        }
    }

    private void handleCustomBehaviourClick(InventoryClickEvent event, Player player, CustomBehaviourHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) return;
        NpcDefinition definition = definitionRepository.find(holder.key()).orElse(null);
        if (definition == null) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot == 45) { openCustomBehaviours(player, definition, holder.page() - 1); return; }
        if (slot == 49) { openEditor(player, definition); return; }
        if (slot == 53) { openCustomBehaviours(player, definition, holder.page() + 1); return; }
        int row = slot / 9;
        List<CustomEvent> customEvents = new ArrayList<>(customEventRepository.findAll());
        int eventIndex = holder.page() * 5 + row;
        if (row >= 5 || eventIndex < 0 || eventIndex >= customEvents.size()) return;
        String eventName = customEvents.get(eventIndex).getName();
        List<BehaviourAction> actions = definition.getCustomEventActions(eventName);
        int column = slot % 9 - 2;
        if (slot % 9 == 1) {
            openActionPicker(player, definition, null, eventName, actions.size(), holder.page());
        } else if (column < 0 || column >= 7) {
            return;
        } else if (column < actions.size() && event.isRightClick()) {
            definition.removeCustomEventAction(eventName, column);
            definitionRepository.save(definition);
            openCustomBehaviours(player, definition, holder.page());
        } else if (column < actions.size() && actions.get(column).type() == BehaviourActionType.ASK_QUESTION
                && !event.isShiftClick()) {
            openQuestionEditor(player, QuestionTarget.definition(definition.getKey(), null,
                    eventName, column, holder.page()));
        } else if (column <= actions.size()) {
            openActionPicker(player, definition, null, eventName, column, holder.page());
        }
    }

    private void handleRoutePointActionsClick(InventoryClickEvent event, Player player, RoutePointActionsHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        RoutePoint current = currentRoutePoint(holder.routeKey(), holder.point());
        if (current == null) {
            player.closeInventory();
            player.sendMessage(Component.text("That route point no longer exists."));
            return;
        }
        if (event.getRawSlot() == 22) {
            player.closeInventory();
            return;
        }
        int index = event.getRawSlot() - 10;
        if (index < 0 || index >= 7) {
            return;
        }
        if (index < current.actions().size() && event.isRightClick()) {
            List<BehaviourAction> actions = new ArrayList<>(current.actions());
            actions.remove(index);
            RoutePoint updated = saveRoutePointActions(holder.routeKey(), current, actions);
            if (updated != null) {
                openWaypointActions(player, holder.routeKey(), updated);
            }
        } else if (index < current.actions().size()
                && current.actions().get(index).type() == BehaviourActionType.ASK_QUESTION
                && !event.isShiftClick()) {
            openQuestionEditor(player, QuestionTarget.route(holder.routeKey(), current, index));
        } else if (index <= current.actions().size()) {
            openRoutePointActionPicker(player,
                    new RoutePointActionPickerHolder(holder.routeKey(), current, index));
        }
    }

    private void handleRoutePointActionPickerClick(InventoryClickEvent event, Player player,
            RoutePointActionPickerHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        RoutePoint current = currentRoutePoint(holder.routeKey(), holder.point());
        if (current == null) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == ACTION_PICKER_BACK_SLOT) {
            openWaypointActions(player, holder.routeKey(), current);
            return;
        }
        if (event.getRawSlot() == ACTION_PICKER_ANIMATIONS_SLOT) {
            openRoutePointAnimationPicker(player, new RoutePointActionPickerHolder(
                    holder.routeKey(), current, holder.actionIndex()));
            return;
        }
        BehaviourActionType type = ACTION_PICKER_ACTIONS.get(event.getRawSlot());
        if (type == null) {
            return;
        }
        RoutePointActionPickerHolder action = new RoutePointActionPickerHolder(
                holder.routeKey(), current, holder.actionIndex());
        if (type == BehaviourActionType.ASK_QUESTION) {
            requestRouteQuestion(player, action);
        } else if (type == BehaviourActionType.SET_ROUTE) {
            openRoutePointValuePicker(player, action, BehaviourValuePickerType.ROUTE, 0);
        } else if (type == BehaviourActionType.SET_WALK_SPEED) {
            openRoutePointValuePicker(player, action, BehaviourValuePickerType.WALK_SPEED, 0);
        } else if (type == BehaviourActionType.EMIT_EVENT) {
            openRoutePointValuePicker(player, action, BehaviourValuePickerType.CUSTOM_EVENT, 0);
        } else if (type == BehaviourActionType.MOVE_TO || type == BehaviourActionType.TELEPORT_TO) {
            beginRouteWaypointSelection(player, action, type);
        } else if (type == BehaviourActionType.WAIT) {
            requestRouteWaitAction(player, action);
        } else if (type == BehaviourActionType.CHANGE_FIGHT_OPTIONS) {
            requestRouteFightOptionsAction(player, action);
        } else if (!type.requiresValue()) {
            RoutePoint updated = setRoutePointAction(action, type, null);
            if (updated != null) {
                openWaypointActions(player, holder.routeKey(), updated);
            }
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
                RoutePoint updated = setRoutePointAction(action, type, value);
                if (updated != null) {
                    openWaypointActions(player, action.routeKey(), updated);
                }
            });
        }
    }

    private void handleRoutePointAnimationPickerClick(InventoryClickEvent event, Player player,
            RoutePointAnimationPickerHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        RoutePoint current = currentRoutePoint(holder.routeKey(), holder.point());
        if (current == null) {
            player.closeInventory();
            return;
        }
        RoutePointActionPickerHolder action = new RoutePointActionPickerHolder(
                holder.routeKey(), current, holder.actionIndex());
        if (event.getRawSlot() == 22) {
            openRoutePointActionPicker(player, action);
            return;
        }
        int index = event.getRawSlot() - 10;
        if (index < 0 || index >= ANIMATION_ACTIONS.size()) {
            return;
        }
        RoutePoint updated = setRoutePointAction(action, ANIMATION_ACTIONS.get(index), null);
        if (updated != null) {
            openWaypointActions(player, holder.routeKey(), updated);
        }
    }

    private void handleRoutePointValuePickerClick(InventoryClickEvent event, Player player,
            RoutePointValuePickerHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }
        RoutePoint current = currentRoutePoint(holder.routeKey(), holder.point());
        if (current == null) {
            player.closeInventory();
            return;
        }
        RoutePointActionPickerHolder action = new RoutePointActionPickerHolder(
                holder.routeKey(), current, holder.actionIndex());
        int slot = event.getRawSlot();
        if (slot == 45) {
            if (holder.pickerType() == BehaviourValuePickerType.ROUTE && !holder.folder().isEmpty())
                openRoutePointValuePicker(player, action, holder.pickerType(), parentFolder(holder.folder()), 0);
            return;
        }
        if (slot == 47) {
            openRoutePointValuePicker(player, action, holder.pickerType(), holder.folder(), holder.page() - 1);
            return;
        }
        if (slot == 49) {
            openRoutePointActionPicker(player, action);
            return;
        }
        if (slot == 53) {
            openRoutePointValuePicker(player, action, holder.pickerType(), holder.folder(), holder.page() + 1);
            return;
        }
        if (slot == 51 && holder.pickerType() == BehaviourValuePickerType.ROUTE) {
            routeCreator.create(player, holder.folder(), route -> {
                RoutePoint updated = setRoutePointAction(action, BehaviourActionType.SET_ROUTE, route.getKey());
                if (updated != null) player.sendMessage(Component.text("Created and selected '" + route.getDisplayName() + "'."));
            });
            return;
        }
        if (slot == 51 && holder.pickerType() == BehaviourValuePickerType.CUSTOM_EVENT) {
            customEventCreator.create(player, holder.folder(), customEvent -> {
                RoutePoint updated = setRoutePointAction(action, BehaviourActionType.EMIT_EVENT, customEvent.getName());
                if (updated != null) {
                    player.sendMessage(Component.text("Created and selected '" + customEvent.getName() + "'."));
                    openWaypointActions(player, holder.routeKey(), updated);
                }
            }, () -> openRoutePointValuePicker(player, action, holder.pickerType(), holder.folder(), holder.page()));
            return;
        }
        List<BehaviourPickerOption> options = pickerOptions(holder.pickerType(), holder.folder());
        int index = holder.page() * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index < 0 || index >= options.size()) {
            return;
        }
        BehaviourPickerOption option = options.get(index);
        if (option.folder()) {
            openRoutePointValuePicker(player, action, holder.pickerType(), option.value(), 0);
            return;
        }
        RoutePoint updated = setRoutePointAction(action, holder.pickerType().actionType(), option.value());
        if (updated != null) {
            player.sendMessage(Component.text("Selected '" + option.label() + "'."));
            openWaypointActions(player, holder.routeKey(), updated);
        }
    }

    private void requestRouteWaitAction(Player player, RoutePointActionPickerHolder holder) {
        chatInputService.request(player, "Enter the number of seconds to wait (decimals are allowed):", value -> {
            String normalized;
            try {
                double seconds = Double.parseDouble(value.trim());
                if (!Double.isFinite(seconds) || seconds <= 0.0 || seconds > Long.MAX_VALUE / 20.0) {
                    throw new NumberFormatException();
                }
                normalized = Double.toString(seconds);
            } catch (NullPointerException | NumberFormatException exception) {
                player.sendMessage(Component.text("Enter a positive number of seconds, for example 5 or 1.5."));
                requestRouteWaitAction(player, holder);
                return;
            }
            RoutePoint updated = setRoutePointAction(holder, BehaviourActionType.WAIT, normalized);
            if (updated != null) {
                openWaypointActions(player, holder.routeKey(), updated);
            }
        });
    }

    private RoutePoint setRoutePointAction(RoutePointActionPickerHolder holder,
            BehaviourActionType type, String value) {
        return setRoutePointAction(holder, new BehaviourAction(type, value));
    }

    private RoutePoint setRoutePointAction(RoutePointActionPickerHolder holder, BehaviourAction action) {
        RoutePoint current = currentRoutePoint(holder.routeKey(), holder.point());
        if (current == null) {
            return null;
        }
        List<BehaviourAction> actions = new ArrayList<>(current.actions());
        if (holder.actionIndex() < actions.size()) {
            actions.set(holder.actionIndex(), action);
        } else if (actions.size() < 7) {
            actions.add(action);
        }
        return saveRoutePointActions(holder.routeKey(), current, actions);
    }

    private RoutePoint saveRoutePointActions(String routeKey, RoutePoint current, List<BehaviourAction> actions) {
        NpcRoute route = routeRepository.find(routeKey).orElse(null);
        RoutePoint latest = route == null ? null : route.findPoint(current).orElse(null);
        if (latest == null) {
            return null;
        }
        RoutePoint updated = latest.withActions(actions);
        if (!route.replacePoint(latest, updated)) {
            return null;
        }
        routeRepository.save(route);
        return updated;
    }

    private RoutePoint currentRoutePoint(String routeKey, RoutePoint point) {
        return routeRepository.find(routeKey).flatMap(route -> route.findPoint(point)).orElse(null);
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
        if (event.getRawSlot() == ACTION_PICKER_BACK_SLOT) {
            openBehaviourHome(player, definition, holder);
            return;
        }
        if (event.getRawSlot() == ACTION_PICKER_ANIMATIONS_SLOT) {
            openAnimationPicker(player, holder);
            return;
        }
        BehaviourActionType type = ACTION_PICKER_ACTIONS.get(event.getRawSlot());
        if (type == null) {
            return;
        }
        if (type == BehaviourActionType.ASK_QUESTION) {
            requestQuestion(player, definition, holder);
        } else if (type == BehaviourActionType.SET_ROUTE) {
            openBehaviourValuePicker(player, definition, holder, BehaviourValuePickerType.ROUTE, 0);
        } else if (type == BehaviourActionType.SET_WALK_SPEED) {
            openBehaviourValuePicker(player, definition, holder, BehaviourValuePickerType.WALK_SPEED, 0);
        } else if (type == BehaviourActionType.EMIT_EVENT) {
            openBehaviourValuePicker(player, definition, holder, BehaviourValuePickerType.CUSTOM_EVENT, 0);
        } else if (type == BehaviourActionType.MOVE_TO || type == BehaviourActionType.TELEPORT_TO) {
            beginWaypointSelection(player, holder, type);
        } else if (type == BehaviourActionType.WAIT) {
            requestWaitAction(player, definition, holder);
        } else if (type == BehaviourActionType.CHANGE_FIGHT_OPTIONS) {
            requestFightOptionsAction(player, definition, holder);
        } else if (!type.requiresValue()) {
            setAction(definition, holder, type, null);
            openBehaviourHome(player, definition, holder);
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
                openBehaviourHome(player, definition, holder);
            });
        }
    }

    private void requestWaitAction(Player player, NpcDefinition definition, ActionPickerHolder holder) {
        chatInputService.request(player, "Enter the number of seconds to wait (decimals are allowed):", value -> {
            String normalized;
            try {
                double seconds = Double.parseDouble(value.trim());
                if (!Double.isFinite(seconds) || seconds <= 0.0 || seconds > Long.MAX_VALUE / 20.0) {
                    throw new NumberFormatException();
                }
                normalized = Double.toString(seconds);
            } catch (NullPointerException | NumberFormatException exception) {
                player.sendMessage(Component.text("Enter a positive number of seconds, for example 5 or 1.5."));
                requestWaitAction(player, definition, holder);
                return;
            }
            setAction(definition, holder, BehaviourActionType.WAIT, normalized);
            openBehaviourHome(player, definition, holder);
        });
    }

    private void requestQuestion(Player player, NpcDefinition definition, ActionPickerHolder holder) {
        chatInputService.request(player, "Enter the question shown to the player:", prompt -> {
            BehaviourAction action = BehaviourAction.ask(NpcQuestion.create(prompt));
            setAction(definition, holder, action);
            openQuestionEditor(player, QuestionTarget.definition(definition.getKey(), holder.event(),
                    holder.customEvent(), holder.actionIndex(), holder.page()));
        });
    }

    private void requestRouteQuestion(Player player, RoutePointActionPickerHolder holder) {
        chatInputService.request(player, "Enter the question shown to the player:", prompt -> {
            RoutePoint updated = setRoutePointAction(holder, BehaviourAction.ask(NpcQuestion.create(prompt)));
            if (updated != null) openQuestionEditor(player,
                    QuestionTarget.route(holder.routeKey(), updated, holder.actionIndex()));
        });
    }

    private void requestFightOptionsAction(Player player, NpcDefinition definition, ActionPickerHolder holder) {
        chatInputService.request(player,
                "Enter enabled targets separated by commas (mobs, animals, players, npcs), or 'none':", value -> {
            FightOptions options = FightOptions.fromStored(value.equalsIgnoreCase("none") ? "" : value);
            setAction(definition, holder, BehaviourActionType.CHANGE_FIGHT_OPTIONS, options.storedValue());
            openBehaviourHome(player, definition, holder);
        });
    }

    private void requestRouteFightOptionsAction(Player player, RoutePointActionPickerHolder holder) {
        chatInputService.request(player,
                "Enter enabled targets separated by commas (mobs, animals, players, npcs), or 'none':", value -> {
            FightOptions options = FightOptions.fromStored(value.equalsIgnoreCase("none") ? "" : value);
            RoutePoint updated = setRoutePointAction(holder, BehaviourActionType.CHANGE_FIGHT_OPTIONS,
                    options.storedValue());
            if (updated != null) openWaypointActions(player, holder.routeKey(), updated);
        });
    }

    private void beginWaypointSelection(Player player, ActionPickerHolder holder, BehaviourActionType type) {
        finishWaypointSelection(player);
        finishRouteWaypointSelection(player);
        UUID token = UUID.randomUUID();
        WaypointSession session = new WaypointSession(holder, type, token);
        waypointSessions.put(player.getUniqueId(), session);
        ItemStack held = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(createWaypointTool(session));
        if (!held.getType().isAir()) {
            player.getInventory().addItem(held).values().forEach(leftover
                    -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.closeInventory();
        player.sendMessage(Component.text("Right-click the block the NPC should "
                + (type == BehaviourActionType.MOVE_TO ? "walk to" : "teleport onto") + ". Drop the compass to cancel."));
    }

    private ItemStack createWaypointTool(WaypointSession session) {
        ItemStack tool = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + session.type().displayName() + " Waypoint Selector");
        meta.setLore(List.of(
                ChatColor.YELLOW + "Right-click a block to select it",
                ChatColor.GRAY + "The NPC will stand on top of that block",
                ChatColor.RED + "Drop this compass to cancel"
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(waypointActionKey, PersistentDataType.STRING, session.type().name());
        meta.getPersistentDataContainer().set(waypointTokenKey, PersistentDataType.STRING, session.token().toString());
        tool.setItemMeta(meta);
        return tool;
    }

    @EventHandler
    public void onWaypointClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        WaypointSession session = validWaypointSession(player, event.getItem());
        if (session == null) {
            RouteActionWaypointSession routeSession = validRouteWaypointSession(player, event.getItem());
            if (routeSession == null) {
                return;
            }
            event.setCancelled(true);
            ActionLocation location = ActionLocation.above(event.getClickedBlock());
            finishRouteWaypointSelection(player);
            RoutePoint updated = setRoutePointAction(routeSession.action(), routeSession.type(), location.serialize());
            if (updated == null) {
                player.sendMessage(Component.text("That route point no longer exists."));
                return;
            }
            player.sendMessage(Component.text(routeSession.type().displayName() + " set to " + location.display() + "."));
            openWaypointActions(player, routeSession.action().routeKey(), updated);
            return;
        }
        event.setCancelled(true);
        NpcDefinition definition = definitionRepository.find(session.action().key()).orElse(null);
        ActionLocation location = ActionLocation.above(event.getClickedBlock());
        finishWaypointSelection(player);
        if (definition == null) {
            player.sendMessage(Component.text("That NPC preset no longer exists."));
            return;
        }
        setAction(definition, session.action(), session.type(), location.serialize());
        player.sendMessage(Component.text(session.type().displayName() + " set to " + location.display() + "."));
        openBehaviourHome(player, definition, session.action());
    }

    @EventHandler
    public void onWaypointToolDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        WaypointSession session = validWaypointSession(player, event.getItemDrop().getItemStack());
        if (session == null) {
            RouteActionWaypointSession routeSession = validRouteWaypointSession(player,
                    event.getItemDrop().getItemStack());
            if (routeSession == null) {
                return;
            }
            event.getItemDrop().remove();
            routeWaypointSessions.remove(player.getUniqueId());
            player.sendMessage(Component.text("Waypoint selection cancelled."));
            Bukkit.getScheduler().runTask(plugin, () -> {
                RoutePoint current = currentRoutePoint(routeSession.action().routeKey(), routeSession.action().point());
                if (current != null) {
                    openRoutePointActionPicker(player, new RoutePointActionPickerHolder(
                            routeSession.action().routeKey(), current, routeSession.action().actionIndex()));
                }
            });
            return;
        }
        event.getItemDrop().remove();
        waypointSessions.remove(player.getUniqueId());
        NpcDefinition definition = definitionRepository.find(session.action().key()).orElse(null);
        player.sendMessage(Component.text("Waypoint selection cancelled."));
        if (definition != null) {
            Bukkit.getScheduler().runTask(plugin, () -> openActionPicker(player, definition,
                    session.action().event(), session.action().customEvent(),
                    session.action().actionIndex(), session.action().page()));
        }
    }

    @EventHandler
    public void onWaypointPlayerQuit(PlayerQuitEvent event) {
        finishWaypointSelection(event.getPlayer());
        finishRouteWaypointSelection(event.getPlayer());
    }

    private WaypointSession validWaypointSession(Player player, ItemStack item) {
        WaypointSession session = waypointSessions.get(player.getUniqueId());
        if (session == null || item == null || item.getType() != Material.RECOVERY_COMPASS || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String type = meta.getPersistentDataContainer().get(waypointActionKey, PersistentDataType.STRING);
        String token = meta.getPersistentDataContainer().get(waypointTokenKey, PersistentDataType.STRING);
        return session.type().name().equals(type) && session.token().toString().equals(token) ? session : null;
    }

    private void finishWaypointSelection(Player player) {
        WaypointSession session = waypointSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (matchesWaypointTool(contents[slot], session)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private boolean matchesWaypointTool(ItemStack item, WaypointSession session) {
        if (item == null || item.getType() != Material.RECOVERY_COMPASS || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return session.type().name().equals(meta.getPersistentDataContainer()
                .get(waypointActionKey, PersistentDataType.STRING))
                && session.token().toString().equals(meta.getPersistentDataContainer()
                        .get(waypointTokenKey, PersistentDataType.STRING));
    }

    private void beginRouteWaypointSelection(Player player, RoutePointActionPickerHolder holder,
            BehaviourActionType type) {
        finishWaypointSelection(player);
        finishRouteWaypointSelection(player);
        UUID token = UUID.randomUUID();
        RouteActionWaypointSession session = new RouteActionWaypointSession(holder, type, token);
        routeWaypointSessions.put(player.getUniqueId(), session);
        ItemStack held = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(createRouteWaypointTool(session));
        if (!held.getType().isAir()) {
            player.getInventory().addItem(held).values().forEach(leftover
                    -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.closeInventory();
        player.sendMessage(Component.text("Right-click the block the NPC should "
                + (type == BehaviourActionType.MOVE_TO ? "walk to" : "teleport onto")
                + ". Drop the compass to cancel."));
    }

    private ItemStack createRouteWaypointTool(RouteActionWaypointSession session) {
        ItemStack tool = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + session.type().displayName() + " Waypoint Selector");
        meta.setLore(List.of(
                ChatColor.YELLOW + "Right-click a block to select it",
                ChatColor.GRAY + "The NPC will stand on top of that block",
                ChatColor.RED + "Drop this compass to cancel"
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(waypointActionKey, PersistentDataType.STRING, session.type().name());
        meta.getPersistentDataContainer().set(waypointTokenKey, PersistentDataType.STRING, session.token().toString());
        tool.setItemMeta(meta);
        return tool;
    }

    private RouteActionWaypointSession validRouteWaypointSession(Player player, ItemStack item) {
        RouteActionWaypointSession session = routeWaypointSessions.get(player.getUniqueId());
        if (session == null || item == null || item.getType() != Material.RECOVERY_COMPASS || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String type = meta.getPersistentDataContainer().get(waypointActionKey, PersistentDataType.STRING);
        String token = meta.getPersistentDataContainer().get(waypointTokenKey, PersistentDataType.STRING);
        return session.type().name().equals(type) && session.token().toString().equals(token) ? session : null;
    }

    private void finishRouteWaypointSelection(Player player) {
        RouteActionWaypointSession session = routeWaypointSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && item.getType() == Material.RECOVERY_COMPASS && item.hasItemMeta()
                    && session.token().toString().equals(item.getItemMeta().getPersistentDataContainer()
                            .get(waypointTokenKey, PersistentDataType.STRING))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private void handleAnimationPickerClick(
            InventoryClickEvent event,
            Player player,
            AnimationPickerHolder holder
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
        ActionPickerHolder action = new ActionPickerHolder(
                holder.key(), holder.event(), holder.customEvent(), holder.actionIndex(), holder.page());
        if (event.getRawSlot() == 22) {
            openActionPicker(player, definition, holder.event(), holder.customEvent(), holder.actionIndex(), holder.page());
            return;
        }
        int animationIndex = event.getRawSlot() - 10;
        if (animationIndex < 0 || animationIndex >= ANIMATION_ACTIONS.size()) {
            return;
        }
        setAction(definition, action, ANIMATION_ACTIONS.get(animationIndex), null);
        openBehaviourHome(player, definition, action);
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
        ActionPickerHolder action = new ActionPickerHolder(holder.key(), holder.event(), holder.customEvent(), holder.actionIndex(), holder.behaviourPage());
        int slot = event.getRawSlot();
        if (slot == 45) {
            if (holder.pickerType() == BehaviourValuePickerType.ROUTE && !holder.folder().isEmpty())
                openBehaviourValuePicker(player, definition, action, holder.pickerType(), parentFolder(holder.folder()), 0);
            return;
        }
        if (slot == 47) {
            openBehaviourValuePicker(player, definition, action, holder.pickerType(), holder.folder(), holder.valuePage() - 1);
            return;
        }
        if (slot == 49) {
            openActionPicker(player, definition, holder.event(), holder.customEvent(), holder.actionIndex(), holder.behaviourPage());
            return;
        }
        if (slot == 53) {
            openBehaviourValuePicker(player, definition, action, holder.pickerType(), holder.folder(), holder.valuePage() + 1);
            return;
        }
        if (slot == 51 && holder.pickerType() == BehaviourValuePickerType.ROUTE) {
            routeCreator.create(player, holder.folder(), route -> {
                setAction(definition, action, BehaviourActionType.SET_ROUTE, route.getKey());
                player.sendMessage(Component.text("Created and selected '" + route.getDisplayName() + "'."));
            });
            return;
        }
        if (slot == 51 && holder.pickerType() == BehaviourValuePickerType.CUSTOM_EVENT) {
            customEventCreator.create(player, holder.folder(), customEvent -> {
                setAction(definition, action, BehaviourActionType.EMIT_EVENT, customEvent.getName());
                player.sendMessage(Component.text("Created and selected '" + customEvent.getName() + "'."));
                openBehaviourHome(player, definition, action);
            }, () -> openBehaviourValuePicker(player, definition, action, holder.pickerType(), holder.folder(), holder.valuePage()));
            return;
        }
        List<BehaviourPickerOption> options = pickerOptions(holder.pickerType(), holder.folder());
        int index = holder.valuePage() * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index < 0 || index >= options.size()) {
            return;
        }
        BehaviourPickerOption option = options.get(index);
        if (option.folder()) {
            openBehaviourValuePicker(player, definition, action, holder.pickerType(), option.value(), 0);
            return;
        }
        setAction(definition, action, holder.pickerType().actionType(), option.value());
        player.sendMessage(Component.text("Selected '" + option.label() + "'."));
        openBehaviourHome(player, definition, action);
    }

    private void setAction(NpcDefinition definition, ActionPickerHolder holder, BehaviourActionType type, String value) {
        setAction(definition, holder, new BehaviourAction(type, value));
    }

    private void setAction(NpcDefinition definition, ActionPickerHolder holder, BehaviourAction action) {
        List<BehaviourAction> actions = holder.customEvent() == null
                ? definition.getBehaviourActions(holder.event())
                : definition.getCustomEventActions(holder.customEvent());
        boolean actionSet = false;
        if (holder.actionIndex() < actions.size()) {
            actions.set(holder.actionIndex(), action);
            actionSet = true;
        } else if (actions.size() < 7) {
            actions.add(action);
            actionSet = true;
        }
        if (holder.customEvent() == null) definition.setBehaviourActions(holder.event(), actions);
        else definition.setCustomEventActions(holder.customEvent(), actions);
        definitionRepository.save(definition);
        if (actionSet && action.type() == BehaviourActionType.EMIT_EVENT) {
            setDefaultEventIconFromNpc(definition, action.value());
        }
    }

    private void setDefaultEventIconFromNpc(NpcDefinition definition, String eventName) {
        if (eventName == null) return;
        customEventRepository.find(eventName).ifPresent(customEvent -> {
            if (customEvent.getIcon() != null) return;
            customEvent.setIcon(definitionIcon(definition, List.of()));
            customEventRepository.save(customEvent);
        });
    }

    private void openBehaviourHome(Player player, NpcDefinition definition, ActionPickerHolder holder) {
        if (holder.customEvent() == null) openBehaviours(player, definition, holder.page());
        else openCustomBehaviours(player, definition, holder.page());
    }

    private void openQuestionEditor(Player player, QuestionTarget target) {
        BehaviourAction action = questionAction(target);
        if (action == null) { openQuestionParent(player, target); return; }
        NpcQuestion question = action.question();
        Inventory inventory = Bukkit.createInventory(new QuestionEditorHolder(target), 54,
                Component.text("Question Editor"));
        inventory.setItem(4, item(Material.WRITABLE_BOOK, "Prompt", List.of(
                ChatColor.WHITE + question.prompt(), ChatColor.YELLOW + "Click to edit")));
        for (int index = 0; index < NpcQuestion.MAX_OPTIONS; index++) {
            int slot = 10 + index;
            if (index < question.options().size()) {
                QuestionOption option = question.options().get(index);
                inventory.setItem(slot, item(Material.LIME_DYE, (index + 1) + ". " + option.label(), List.of(
                        ChatColor.GRAY + "Branch actions: " + ChatColor.WHITE + option.actions().size(),
                        ChatColor.YELLOW + "Click to edit this answer")));
            } else if (index == question.options().size()) {
                inventory.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, "Add Answer", List.of(
                        ChatColor.YELLOW + "Click, then enter its label")));
            }
        }
        inventory.setItem(31, item(Material.BARRIER, "Cancel / Timeout", List.of(
                ChatColor.GRAY + "Branch actions: " + ChatColor.WHITE + question.cancelActions().size(),
                ChatColor.YELLOW + "Click to edit the cancellation branch")));
        inventory.setItem(45, item(Material.ARROW, "Back", List.of()));
        inventory.setItem(49, item(question.options().isEmpty() ? Material.REDSTONE_TORCH : Material.OAK_SIGN,
                "Question Summary", List.of(
                        ChatColor.GRAY + "Answers: " + ChatColor.WHITE + question.options().size(),
                        question.options().isEmpty() ? ChatColor.RED + "No answers: cancellation branch will run"
                                : ChatColor.GREEN + "Ready")));
        openInventory(player, inventory);
    }

    private void handleQuestionEditorClick(InventoryClickEvent event, Player player, QuestionEditorHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) return;
        BehaviourAction action = questionAction(holder.target());
        if (action == null) { openQuestionParent(player, holder.target()); return; }
        NpcQuestion question = action.question();
        int slot = event.getRawSlot();
        if (slot == 45) { openQuestionParent(player, holder.target()); return; }
        if (slot == 4) {
            chatInputService.request(player, "Enter the question shown to the player:", value -> {
                updateQuestion(holder.target(), question.withPrompt(value));
                openQuestionEditor(player, holder.target());
            });
            return;
        }
        if (slot == 31) {
            openQuestionBranch(player, holder.target(), -1);
            return;
        }
        int optionIndex = slot - 10;
        if (optionIndex < 0 || optionIndex >= NpcQuestion.MAX_OPTIONS) return;
        if (optionIndex < question.options().size()) {
            openQuestionBranch(player, holder.target(), optionIndex);
        } else if (optionIndex == question.options().size()) {
            chatInputService.request(player, "Enter the answer label:", label -> {
                try {
                    List<QuestionOption> options = new ArrayList<>(question.options());
                    options.add(new QuestionOption(label, List.of()));
                    updateQuestion(holder.target(), question.withOptions(options));
                    openQuestionEditor(player, holder.target());
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(Component.text(exception.getMessage()));
                    openQuestionEditor(player, holder.target());
                }
            });
        }
    }

    private void openQuestionBranch(Player player, QuestionTarget target, int optionIndex) {
        BehaviourAction action = questionAction(target);
        if (action == null) { openQuestionParent(player, target); return; }
        NpcQuestion question = action.question();
        if (optionIndex >= question.options().size()) { openQuestionEditor(player, target); return; }
        List<BehaviourAction> actions = optionIndex < 0 ? question.cancelActions()
                : question.options().get(optionIndex).actions();
        String title = optionIndex < 0 ? "Cancel / Timeout Branch"
                : "Answer: " + question.options().get(optionIndex).label();
        Inventory inventory = Bukkit.createInventory(new QuestionBranchHolder(target, optionIndex), 54,
                Component.text(title));
        for (int index = 0; index < NpcQuestion.MAX_BRANCH_ACTIONS; index++) {
            int slot = 10 + index;
            if (index < actions.size()) {
                BehaviourAction branchAction = actions.get(index);
                inventory.setItem(slot, item(actionMaterial(branchAction.type()),
                        (index + 1) + ". " + branchAction.type().displayName(), List.of(
                                ChatColor.GRAY + actionValueDisplay(branchAction),
                                ChatColor.YELLOW + "Left-click to replace",
                                ChatColor.RED + "Right-click to remove")));
            } else if (index == actions.size()) {
                inventory.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, "Add Action", List.of(
                        ChatColor.YELLOW + "Click to append")));
            }
        }
        if (optionIndex >= 0) {
            inventory.setItem(28, item(Material.ARROW, "Move Earlier", List.of()));
            inventory.setItem(30, item(Material.NAME_TAG, "Rename Answer", List.of()));
            inventory.setItem(32, item(Material.RED_CONCRETE, "Delete Answer", List.of(
                    ChatColor.RED + "Shift-click to confirm")));
            inventory.setItem(34, item(Material.ARROW, "Move Later", List.of()));
        }
        inventory.setItem(45, item(Material.ARROW, "Back to Question", List.of()));
        openInventory(player, inventory);
    }

    private void handleQuestionBranchClick(InventoryClickEvent event, Player player, QuestionBranchHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) return;
        BehaviourAction action = questionAction(holder.target());
        if (action == null) { openQuestionParent(player, holder.target()); return; }
        NpcQuestion question = action.question();
        int optionIndex = holder.optionIndex();
        int slot = event.getRawSlot();
        if (slot == 45) { openQuestionEditor(player, holder.target()); return; }
        if (optionIndex >= 0 && slot == 30) {
            chatInputService.request(player, "Enter the new answer label:", label -> {
                try {
                    List<QuestionOption> options = new ArrayList<>(question.options());
                    options.set(optionIndex, options.get(optionIndex).withLabel(label));
                    updateQuestion(holder.target(), question.withOptions(options));
                    openQuestionBranch(player, holder.target(), optionIndex);
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(Component.text(exception.getMessage()));
                    openQuestionBranch(player, holder.target(), optionIndex);
                }
            });
            return;
        }
        if (optionIndex >= 0 && slot == 32 && event.isShiftClick()) {
            List<QuestionOption> options = new ArrayList<>(question.options());
            options.remove(optionIndex);
            updateQuestion(holder.target(), question.withOptions(options));
            openQuestionEditor(player, holder.target());
            return;
        }
        if (optionIndex >= 0 && (slot == 28 || slot == 34)) {
            int destination = optionIndex + (slot == 28 ? -1 : 1);
            if (destination >= 0 && destination < question.options().size()) {
                List<QuestionOption> options = new ArrayList<>(question.options());
                java.util.Collections.swap(options, optionIndex, destination);
                updateQuestion(holder.target(), question.withOptions(options));
                openQuestionBranch(player, holder.target(), destination);
            }
            return;
        }
        int actionIndex = slot - 10;
        List<BehaviourAction> actions = questionBranch(question, optionIndex);
        if (actionIndex < 0 || actionIndex >= NpcQuestion.MAX_BRANCH_ACTIONS) return;
        if (actionIndex < actions.size() && event.isRightClick()) {
            List<BehaviourAction> changed = new ArrayList<>(actions);
            changed.remove(actionIndex);
            updateQuestionBranch(holder.target(), optionIndex, changed);
            openQuestionBranch(player, holder.target(), optionIndex);
        } else if (actionIndex <= actions.size()) {
            openQuestionBranchPicker(player, holder.target(), optionIndex, actionIndex);
        }
    }

    private void openQuestionBranchPicker(Player player, QuestionTarget target, int optionIndex, int actionIndex) {
        Inventory inventory = Bukkit.createInventory(new QuestionBranchPickerHolder(target, optionIndex, actionIndex),
                54, Component.text("Choose Branch Action"));
        for (Map.Entry<Integer, BehaviourActionType> entry : ACTION_PICKER_ACTIONS.entrySet()) {
            if (entry.getValue() == BehaviourActionType.ASK_QUESTION) continue;
            inventory.setItem(entry.getKey(), item(actionMaterial(entry.getValue()), entry.getValue().displayName(),
                    List.of(ChatColor.YELLOW + "Click to configure")));
        }
        int[] animationSlots = {37, 38, 39, 40, 41, 42, 43};
        for (int index = 0; index < ANIMATION_ACTIONS.size(); index++) {
            BehaviourActionType type = ANIMATION_ACTIONS.get(index);
            inventory.setItem(animationSlots[index], item(actionMaterial(type), type.displayName(),
                    List.of(ChatColor.YELLOW + "Click to select")));
        }
        inventory.setItem(49, item(Material.BARRIER, "Back", List.of()));
        openInventory(player, inventory);
    }

    private void handleQuestionBranchPickerClick(InventoryClickEvent event, Player player,
            QuestionBranchPickerHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) return;
        if (event.getRawSlot() == 49) {
            openQuestionBranch(player, holder.target(), holder.optionIndex());
            return;
        }
        BehaviourActionType type = ACTION_PICKER_ACTIONS.get(event.getRawSlot());
        if (type == BehaviourActionType.ASK_QUESTION) return;
        if (type == null && event.getRawSlot() >= 37 && event.getRawSlot() <= 43) {
            type = ANIMATION_ACTIONS.get(event.getRawSlot() - 37);
        }
        if (type == null) return;
        if (!type.requiresValue()) {
            setQuestionBranchAction(holder, new BehaviourAction(type, null));
            openQuestionBranch(player, holder.target(), holder.optionIndex());
            return;
        }
        BehaviourActionType selected = type;
        String prompt = switch (selected) {
            case SEND_DIALOG -> "Enter the dialog line:";
            case SHOW_HOLO_DIALOG -> "Enter the hologram dialog line:";
            case RUN_CONSOLE_COMMAND -> "Enter the command without a leading slash:";
            case WAIT -> "Enter the positive number of seconds to wait:";
            case SET_ROUTE -> "Enter an existing route key:";
            case SET_WALK_SPEED -> "Enter walk speed (slouch, slow, normal, fast, very_fast):";
            case EMIT_EVENT -> "Enter an existing custom event name:";
            case CHANGE_FIGHT_OPTIONS -> "Enter targets (mobs, animals, players, npcs), or 'none':";
            case MOVE_TO, TELEPORT_TO -> "Type 'here' to use your current location:";
            default -> "Enter the action value:";
        };
        chatInputService.request(player, prompt, value -> {
            String normalized = branchActionValue(player, selected, value);
            if (normalized == null) {
                openQuestionBranchPicker(player, holder.target(), holder.optionIndex(), holder.actionIndex());
                return;
            }
            setQuestionBranchAction(holder, new BehaviourAction(selected, normalized));
            openQuestionBranch(player, holder.target(), holder.optionIndex());
        });
    }

    private String branchActionValue(Player player, BehaviourActionType type, String value) {
        try {
            if (type == BehaviourActionType.WAIT) {
                double seconds = Double.parseDouble(value.trim());
                if (!Double.isFinite(seconds) || seconds <= 0.0) throw new IllegalArgumentException();
                return Double.toString(seconds);
            }
            if (type == BehaviourActionType.SET_ROUTE) {
                NpcRoute route = routeRepository.find(value).orElseThrow();
                return route.getKey();
            }
            if (type == BehaviourActionType.SET_WALK_SPEED) {
                return WalkingSpeed.fromStored(value).name().toLowerCase(java.util.Locale.ROOT);
            }
            if (type == BehaviourActionType.EMIT_EVENT) {
                return customEventRepository.find(value).orElseThrow().getName();
            }
            if (type == BehaviourActionType.CHANGE_FIGHT_OPTIONS) {
                return FightOptions.fromStored(value.equalsIgnoreCase("none") ? "" : value).storedValue();
            }
            if (type == BehaviourActionType.MOVE_TO || type == BehaviourActionType.TELEPORT_TO) {
                if (!value.equalsIgnoreCase("here")) throw new IllegalArgumentException();
                Location location = player.getLocation();
                return new ActionLocation(location.getWorld().getName(), location.getX(), location.getY(),
                        location.getZ()).serialize();
            }
            return value;
        } catch (RuntimeException exception) {
            player.sendMessage(Component.text("That value is not valid for " + type.displayName() + "."));
            return null;
        }
    }

    private void setQuestionBranchAction(QuestionBranchPickerHolder holder, BehaviourAction action) {
        BehaviourAction parent = questionAction(holder.target());
        if (parent == null) return;
        List<BehaviourAction> actions = new ArrayList<>(questionBranch(parent.question(), holder.optionIndex()));
        if (holder.actionIndex() < actions.size()) actions.set(holder.actionIndex(), action);
        else if (actions.size() < NpcQuestion.MAX_BRANCH_ACTIONS) actions.add(action);
        updateQuestionBranch(holder.target(), holder.optionIndex(), actions);
    }

    private List<BehaviourAction> questionBranch(NpcQuestion question, int optionIndex) {
        return optionIndex < 0 ? question.cancelActions() : question.options().get(optionIndex).actions();
    }

    private void updateQuestionBranch(QuestionTarget target, int optionIndex, List<BehaviourAction> actions) {
        BehaviourAction parent = questionAction(target);
        if (parent == null) return;
        NpcQuestion question = parent.question();
        if (optionIndex < 0) {
            updateQuestion(target, question.withCancelActions(actions));
        } else {
            List<QuestionOption> options = new ArrayList<>(question.options());
            options.set(optionIndex, options.get(optionIndex).withActions(actions));
            updateQuestion(target, question.withOptions(options));
        }
    }

    private BehaviourAction questionAction(QuestionTarget target) {
        List<BehaviourAction> actions = target.routeKey() == null
                ? definitionActions(target) : routeActions(target);
        if (target.actionIndex() < 0 || target.actionIndex() >= actions.size()) return null;
        BehaviourAction action = actions.get(target.actionIndex());
        return action.type() == BehaviourActionType.ASK_QUESTION ? action : null;
    }

    private void updateQuestion(QuestionTarget target, NpcQuestion question) {
        List<BehaviourAction> actions = new ArrayList<>(target.routeKey() == null
                ? definitionActions(target) : routeActions(target));
        if (target.actionIndex() < 0 || target.actionIndex() >= actions.size()) return;
        actions.set(target.actionIndex(), BehaviourAction.ask(question));
        if (target.routeKey() != null) {
            RoutePoint current = currentRoutePoint(target.routeKey(), target.point());
            if (current != null) saveRoutePointActions(target.routeKey(), current, actions);
            return;
        }
        NpcDefinition definition = definitionRepository.find(target.definitionKey()).orElse(null);
        if (definition == null) return;
        if (target.customEvent() == null) definition.setBehaviourActions(target.event(), actions);
        else definition.setCustomEventActions(target.customEvent(), actions);
        definitionRepository.save(definition);
    }

    private List<BehaviourAction> definitionActions(QuestionTarget target) {
        NpcDefinition definition = definitionRepository.find(target.definitionKey()).orElse(null);
        if (definition == null) return List.of();
        return target.customEvent() == null ? definition.getBehaviourActions(target.event())
                : definition.getCustomEventActions(target.customEvent());
    }

    private List<BehaviourAction> routeActions(QuestionTarget target) {
        RoutePoint current = currentRoutePoint(target.routeKey(), target.point());
        return current == null ? List.of() : current.actions();
    }

    private void openQuestionParent(Player player, QuestionTarget target) {
        if (target.routeKey() != null) {
            RoutePoint current = currentRoutePoint(target.routeKey(), target.point());
            if (current != null) openWaypointActions(player, target.routeKey(), current);
            else player.closeInventory();
            return;
        }
        NpcDefinition definition = definitionRepository.find(target.definitionKey()).orElse(null);
        if (definition == null) { player.closeInventory(); return; }
        if (target.customEvent() == null) openBehaviours(player, definition, target.page());
        else openCustomBehaviours(player, definition, target.page());
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
                openMain(player, holder.returnPage());
            } else {
                openInstances(player, definition, holder.returnPage());
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
            openInstances(player, definition, holder.returnPage());
        }
    }

    private void openConfirmation(Player player, NpcDefinition definition, ConfirmationAction action, int returnPage) {
        Inventory inventory = Bukkit.createInventory(new ConfirmationHolder(definition.getKey(), action, returnPage), 27,
                Component.text("Confirm deletion"));
        String target = action == ConfirmationAction.DELETE_DEFINITION ? "preset and all instances" : "all instances";
        inventory.setItem(11, item(Material.LIME_CONCRETE, "Confirm", List.of(
                ChatColor.RED + "Permanently delete " + target
        )));
        inventory.setItem(15, item(Material.RED_CONCRETE, "Cancel", List.of(ChatColor.GRAY + "Nothing will be changed")));
        openInventory(player, inventory);
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
        // Keep the URL entered by the user for the menu and future re-resolution.
        // The signed texture data is the part supplied by MineSkin for rendering.
        current.setResolvedSkin(requestedUrl, resolved.textureValue(), resolved.textureSignature());
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

    private void openInventory(Player player, Inventory inventory) {
        // The equipment editor uses its last row for actual item storage rather than navigation.
        if (!(inventory.getHolder() instanceof EquipmentHolder)) GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private boolean isManagedHolder(InventoryHolder holder) {
        return holder instanceof MainHolder
                || holder instanceof EditorHolder
                || holder instanceof FightingHolder
                || holder instanceof InstancesHolder
                || holder instanceof RouteAssignmentHolder
                || holder instanceof BehaviourHolder
                || holder instanceof CustomBehaviourHolder
                || holder instanceof ActionPickerHolder
                || holder instanceof AnimationPickerHolder
                || holder instanceof BehaviourValuePickerHolder
                || holder instanceof RoutePointActionsHolder
                || holder instanceof RoutePointActionPickerHolder
                || holder instanceof RoutePointAnimationPickerHolder
                || holder instanceof RoutePointValuePickerHolder
                || holder instanceof QuestionEditorHolder
                || holder instanceof QuestionBranchHolder
                || holder instanceof QuestionBranchPickerHolder
                || holder instanceof ConfirmationHolder;
    }

    private ItemStack definitionIcon(NpcDefinition definition, List<String> lore) {
        ItemStack head = item(Material.PLAYER_HEAD, definition.getDisplayName(), lore);
        if (definition.getSkinUrl() == null || !(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        try {
            UUID uuid = UUID.nameUUIDFromBytes(definition.getKey().getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfileExact(uuid, "Blockfolk");
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

    private ItemStack routeIcon(NpcRoute route) {
        ItemStack icon = route.getIcon();
        if (icon == null || icon.getType().isAir()) {
            return new ItemStack(Material.RAIL);
        }
        icon.setAmount(1);
        return icon;
    }

    private ItemStack customEventIcon(CustomEvent event) {
        ItemStack icon = event.getIcon();
        return icon == null || icon.getType().isAir() ? new ItemStack(Material.BELL) : icon;
    }

    private ItemStack item(ItemStack template, String name, List<String> lore) {
        ItemStack result = template.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(lore);
        result.setItemMeta(meta);
        return result;
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

    private String allianceLabel(CombatProfile combat) {
        return combat.alliance() == null ? "None" : combat.alliance();
    }

    private Material reactionMaterial(AttackReaction reaction) {
        return switch (reaction) {
            case IGNORE ->
                Material.GRAY_DYE;
            case FLEE ->
                Material.RABBIT_FOOT;
            case FIGHT_BACK ->
                Material.SHIELD;
            case HUNTING ->
                Material.IRON_SWORD;
        };
    }

    private ItemStack toggleItem(Material material, String name, boolean enabled, String description) {
        return item(enabled ? material : Material.GRAY_DYE, name, List.of(
                (enabled ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"),
                ChatColor.GRAY + description,
                ChatColor.YELLOW + "Click to toggle"
        ));
    }

    private int enabledTargetCount(CombatProfile combat) {
        return (combat.targetMobs() ? 1 : 0) + (combat.targetAnimals() ? 1 : 0)
                + (combat.targetPlayers() ? 1 : 0) + (combat.targetNpcs() ? 1 : 0);
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
            case IDLE ->
                Material.CLOCK;
            case DAMAGE_TAKEN ->
                Material.RED_DYE;
            case HEAL ->
                Material.SPLASH_POTION;
            case DROP_ITEM ->
                Material.DROPPER;
            case RECEIVE_ITEM ->
                Material.HOPPER;
            case LOW_HEALTH ->
                Material.GLISTERING_MELON_SLICE;
            case DAWN ->
                Material.ORANGE_DYE;
            case MORNING ->
                Material.SUNFLOWER;
            case MIDDAY ->
                Material.COOKED_BEEF;
        };
    }

    private Material actionMaterial(BehaviourActionType type) {
        return switch (type) {
            case SEND_DIALOG ->
                Material.WRITABLE_BOOK;
            case SHOW_HOLO_DIALOG ->
                Material.NAME_TAG;
            case ASK_QUESTION ->
                Material.OAK_SIGN;
            case SET_ROUTE ->
                Material.RAIL;
            case RUN_CONSOLE_COMMAND ->
                Material.COMMAND_BLOCK;
            case START_COMBAT ->
                Material.DIAMOND_SWORD;
            case CHANGE_FIGHT_OPTIONS ->
                Material.TARGET;
            case START_NAVIGATION ->
                Material.COMPASS;
            case STOP_NAVIGATION ->
                Material.BARRIER;
            case SET_WALK_SPEED ->
                Material.FEATHER;
            case MOVE_TO ->
                Material.LEATHER_BOOTS;
            case TELEPORT_TO ->
                Material.ENDER_PEARL;
            case WAIT ->
                Material.CLOCK;
            case INTERACT ->
                Material.LEVER;
            case MINE_BLOCKS ->
                Material.IRON_PICKAXE;
            case TAKE_ITEM ->
                Material.HOPPER;
            case SHOW_INVENTORY ->
                Material.CHEST;
            case DROP_INVENTORY ->
                Material.DROPPER;
            case HARVEST ->
                Material.IRON_HOE;
            case EMIT_EVENT ->
                Material.SCULK_SENSOR;
            case SLEEP ->
                Material.RED_BED;
            case SWIM ->
                Material.WATER_BUCKET;
            case FALL_FLY ->
                Material.ELYTRA;
            case STAND ->
                Material.ARMOR_STAND;
            case SNEAK ->
                Material.LEATHER_BOOTS;
            case WAVE ->
                Material.BELL;
            case JUMP ->
                Material.SLIME_BLOCK;
            case FOLLOW ->
                Material.LEAD;
            case UNFOLLOW ->
                Material.SHEARS;
        };
    }

    private String actionValueDisplay(BehaviourAction action) {
        if (action.type() == BehaviourActionType.ASK_QUESTION) {
            return action.question().prompt() + " (" + action.question().options().size() + " answers)";
        }
        if (!action.type().requiresValue() || action.value() == null) {
            return "No setting required";
        }
        if (action.type() == BehaviourActionType.MOVE_TO
                || action.type() == BehaviourActionType.TELEPORT_TO) {
            return ActionLocation.parse(action.value()).map(ActionLocation::display).orElse("Invalid waypoint");
        }
        if (action.type() == BehaviourActionType.WAIT) {
            return action.value() + " seconds";
        }
        if (action.type() == BehaviourActionType.CHANGE_FIGHT_OPTIONS) {
            return FightOptions.fromStored(action.value()).displayName();
        }
        return action.value();
    }

    private List<String> actionSummaryLore(List<String> introduction, List<BehaviourAction> actions) {
        List<String> lore = new ArrayList<>(introduction);
        lore.add("");
        if (actions.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "No actions configured");
            return lore;
        }
        for (int index = 0; index < actions.size(); index++) {
            BehaviourAction action = actions.get(index);
            String summary = ChatColor.GRAY + Integer.toString(index + 1) + ". "
                    + ChatColor.WHITE + action.type().displayName();
            if (action.type() == BehaviourActionType.ASK_QUESTION) {
                summary += ChatColor.GRAY + ": " + ChatColor.WHITE + actionValueDisplay(action);
            } else if (action.type().requiresValue()) {
                summary += ChatColor.GRAY + ": " + ChatColor.WHITE + actionValueDisplay(action);
            }
            lore.add(summary);
        }
        return lore;
    }

    private String reactionDescription(AttackReaction reaction) {
        return switch (reaction) {
            case IGNORE ->
                ChatColor.GRAY + "Does not react when attacked";
            case FLEE ->
                ChatColor.GRAY + "Runs away after taking entity damage";
            case FIGHT_BACK ->
                ChatColor.GRAY + "Attacks an entity that damages it";
            case HUNTING ->
                ChatColor.GRAY + "Actively hunts enabled attack targets";
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

    private record FightingHolder(String key) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TargetsHolder(String key) implements InventoryHolder {

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

    private record CustomBehaviourHolder(String key, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ActionPickerHolder(String key, BehaviourEvent event, String customEvent, int actionIndex, int page)
            implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record WaypointSession(
            ActionPickerHolder action,
            BehaviourActionType type,
            UUID token
            ) {

    }

    private record RoutePointActionsHolder(String routeKey, RoutePoint point) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RoutePointActionPickerHolder(String routeKey, RoutePoint point, int actionIndex)
            implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RoutePointAnimationPickerHolder(String routeKey, RoutePoint point, int actionIndex)
            implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RoutePointValuePickerHolder(
            String routeKey,
            RoutePoint point,
            int actionIndex,
            BehaviourValuePickerType pickerType,
            String folder,
            int page
            ) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record RouteActionWaypointSession(
            RoutePointActionPickerHolder action,
            BehaviourActionType type,
            UUID token
            ) {

    }

    private record AnimationPickerHolder(String key, BehaviourEvent event, String customEvent, int actionIndex, int page)
            implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record BehaviourValuePickerHolder(
            String key,
            BehaviourEvent event,
            String customEvent,
            int actionIndex,
            int behaviourPage,
            BehaviourValuePickerType pickerType,
            String folder,
            int valuePage
            ) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record QuestionTarget(String definitionKey, BehaviourEvent event, String customEvent,
            String routeKey, RoutePoint point, int actionIndex, int page) {
        static QuestionTarget definition(String key, BehaviourEvent event, String customEvent,
                int actionIndex, int page) {
            return new QuestionTarget(key, event, customEvent, null, null, actionIndex, page);
        }

        static QuestionTarget route(String routeKey, RoutePoint point, int actionIndex) {
            return new QuestionTarget(null, null, null, routeKey, point, actionIndex, 0);
        }
    }

    private record QuestionEditorHolder(QuestionTarget target) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record QuestionBranchHolder(QuestionTarget target, int optionIndex) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record QuestionBranchPickerHolder(QuestionTarget target, int optionIndex, int actionIndex)
            implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record BehaviourPickerOption(
            String value,
            String label,
            ItemStack icon,
            List<String> lore,
            boolean folder
            ) {

    }

    @FunctionalInterface
    public interface RouteCreator {
        void create(Player player, String folder, Consumer<NpcRoute> onCreated);
    }

    @FunctionalInterface
    public interface CustomEventCreator {
        void create(Player player, String folder, Consumer<CustomEvent> onCreated, Runnable onFailure);
    }

    private enum BehaviourValuePickerType {
        ROUTE(BehaviourActionType.SET_ROUTE, "Select Route", "Create a route from the main preset menu first"),
        WALK_SPEED(BehaviourActionType.SET_WALK_SPEED, "Select Walk Speed", "No walking speeds are available"),
        CUSTOM_EVENT(BehaviourActionType.EMIT_EVENT, "Select Custom Event", "Create a custom event first");

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

    private record ConfirmationHolder(String key, ConfirmationAction action, int returnPage) implements InventoryHolder {

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
