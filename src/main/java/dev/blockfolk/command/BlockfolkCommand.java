package dev.blockfolk.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.ai.AiControlService;
import dev.blockfolk.gui.CustomEventGuiService;
import dev.blockfolk.gui.GuiService;
import dev.blockfolk.gui.RouteGuiService;
import dev.blockfolk.model.CustomEvent;
import dev.blockfolk.model.NamedLocation;
import dev.blockfolk.model.NpcColor;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.CustomEventRepository;
import dev.blockfolk.repository.LocationRepository;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.RouteRepository;
import dev.blockfolk.runtime.NpcBehaviourService;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.util.UiText;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class BlockfolkCommand implements CommandExecutor, TabCompleter, BasicCommand {

    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final GuiService guiService;
    private final RouteGuiService routeGuiService;
    private final RouteRepository routeRepository;
    private final CustomEventGuiService customEventGuiService;
    private final CustomEventRepository customEventRepository;
    private final NpcBehaviourService behaviourService;
    private final LocationRepository locationRepository;
    private final AiControlService aiControlService;
    private final JavaPlugin plugin;

    public BlockfolkCommand(NpcDefinitionRepository definitionRepository, NpcInstanceRegistry instanceRegistry,
            GuiService guiService, RouteGuiService routeGuiService,
            RouteRepository routeRepository, CustomEventGuiService customEventGuiService,
            CustomEventRepository customEventRepository, NpcBehaviourService behaviourService,
            LocationRepository locationRepository, AiControlService aiControlService, JavaPlugin plugin) {
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.guiService = guiService;
        this.routeGuiService = routeGuiService;
        this.routeRepository = routeRepository;
        this.customEventGuiService = customEventGuiService;
        this.customEventRepository = customEventRepository;
        this.behaviourService = behaviourService;
        this.locationRepository = locationRepository;
        this.aiControlService = aiControlService;
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        onCommand(commandSourceStack.getSender(), null, "blockfolk", args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        return onTabComplete(commandSourceStack.getSender(), null, "blockfolk", args);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender instanceof Player || sender.hasPermission("blockfolk.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && args.length == 4 && args[0].equalsIgnoreCase("config")
                && args[1].equalsIgnoreCase("ai") && args[2].equalsIgnoreCase("mute-me")) {
            Boolean muted = parseToggle(args[3]);
            if (muted == null) {
                player.sendMessage(UiText.error("Use /bf config ai mute-me <on|off>."));
                return true;
            }
            behaviourService.setChatMuted(player, muted);
            player.sendMessage(UiText.success("AI chat mute is now " + (muted ? "on" : "off") + "."));
            return true;
        }
        if (!sender.hasPermission("blockfolk.admin")) {
            sender.sendMessage(UiText.error("You do not have permission to use Blockfolk."));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("events") && args[1].equalsIgnoreCase("trigger")) {
            CustomEvent customEvent = customEventRepository.find(args[2]).orElse(null);
            if (customEvent == null) {
                sender.sendMessage(UiText.error("Unknown custom event: " + args[2]));
                return true;
            }
            behaviourService.emitCustomEvent(customEvent.getName(), sender instanceof Player player ? player : null);
            sender.sendMessage(UiText.success("Triggered custom event '" + customEvent.getName() + "'."));
            return true;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("ai")
                && args[2].equalsIgnoreCase("model")) {
            String model = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
            if (model.isBlank()) {
                sender.sendMessage(UiText.error("Specify a model name."));
                return true;
            }
            plugin.getConfig().set("openrouter.model", model);
            plugin.saveConfig();
            aiControlService.setModel(model);
            sender.sendMessage(UiText.success("AI model set to '" + model + "'."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(UiText.warning("Blockfolk is currently managed in-game."));
            return true;
        }
        if (args.length == 0) {
            guiService.openMain(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("routes")) {
            routeGuiService.openRoutes(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("locations")) {
            routeGuiService.openLocations(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("npc")) {
            guiService.openMain(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("events")) {
            customEventGuiService.open(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("create")) {
            guiService.beginCreate(player);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
            String name = String.join(" ", List.of(args).subList(1, args.length));
            guiService.beginCreate(player, name);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("npc")) {
            NpcDefinition definition = definitionRepository.find(args[1]).orElse(null);
            if (definition == null) {
                player.sendMessage(UiText.error("Unknown NPC: " + args[1]));
                return true;
            }
            if (args.length == 2 || args.length == 3 && args[2].equalsIgnoreCase("edit")) {
                guiService.openEditor(player, definition);
                return true;
            }
            if (handleNpcCommand(player, definition, args))
                return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("npc") && args[2].equalsIgnoreCase("duplicate")) {
            NpcDefinition source = definitionRepository.find(args[1]).orElse(null);
            if (source == null) {
                player.sendMessage(UiText.error("Unknown NPC: " + args[1]));
                return true;
            }
            NpcDefinition copy = duplicate(source);
            if (definitionRepository.find(copy.getKey()).isPresent()) {
                player.sendMessage(UiText.error("A copy of this preset already exists."));
                return true;
            }
            routeRepository.copyOwnedRoutes(source, copy);
            definitionRepository.save(copy);
            player.sendMessage(
                    UiText.success("Duplicated " + source.getDisplayName() + " as " + copy.getDisplayName() + "."));
            return true;
        }
        player.sendMessage(UiText.info(
                "Usage: /bf [npc [name <edit|set|tp|inventory|memory|events|combat|equipment|delete|spawn>]|routes|locations|config ai <model|mute-me>]"));
        return true;
    }

    private boolean handleNpcCommand(Player player, NpcDefinition definition, String[] args) {
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "events" -> {
                if (args.length != 3) return false;
                guiService.openBehaviours(player, definition, 0);
            }
            case "combat" -> {
                if (args.length != 3) return false;
                guiService.openFightingEditor(player, definition);
            }
            case "equipment" -> {
                if (args.length != 3) return false;
                guiService.openInventoryEditor(player, definition);
            }
            case "inventory" -> {
                if (args.length != 3) return false;
                guiService.openTemporaryInventoryEditor(player, definition);
            }
            case "delete" -> {
                if (args.length != 3) return false;
                int count = instanceRegistry.findByDefinition(definition).size();
                guiService.deleteDefinition(definition);
                player.sendMessage(UiText.success("Deleted '" + definition.getDisplayName() + "' and " + count
                        + " instance(s)."));
            }
            case "spawn" -> {
                if (args.length > 4) return false;
                Location target = args.length == 4 ? resolveLocation(player, args[3]) : player.getLocation();
                if (target == null) return true;
                if (instanceRegistry.spawnPersistent(definition, target) == null)
                    player.sendMessage(UiText.error("Could not spawn the NPC instance."));
                else
                    player.sendMessage(UiText.success("Spawned '" + definition.getDisplayName() + "'."));
            }
            case "set" -> {
                if (args.length < 4) return false;
                return setNpcProperty(player, definition, args);
            }
            case "tp" -> {
                if (args.length < 4 || args.length > 5) return false;
                String direction = args[3].toLowerCase(Locale.ROOT);
                NpcInstance instance = instanceRegistry.findByDefinition(definition).stream().findFirst().orElse(null);
                if (instance == null) {
                    player.sendMessage(UiText.warning("This NPC has no spawned instances."));
                    return true;
                }
                if (direction.equals("to") && args.length == 4) {
                    if (player.teleport(instanceRegistry.currentLocation(instance)))
                        player.sendMessage(UiText.success("Teleported to the NPC."));
                    else
                        player.sendMessage(UiText.error("Could not teleport to the NPC."));
                } else if (direction.equals("here") && args.length == 4
                        || direction.equals("toloc") && args.length == 5) {
                    Location target = direction.equals("here") ? player.getLocation() : resolveLocation(player, args[4]);
                    if (target != null)
                        player.sendMessage(instanceRegistry.relocate(instance, target)
                                ? UiText.success("Teleported the NPC.")
                                : UiText.error("Could not teleport the NPC."));
                } else return false;
            }
            case "memory" -> {
                if (args.length != 4) return false;
                switch (args[3].toLowerCase(Locale.ROOT)) {
                    case "open" -> guiService.openMemories(player, definition);
                    case "clear" -> {
                        definition.clearAiMemories();
                        definitionRepository.save(definition);
                        aiControlService.resetDefinition(definition);
                        player.sendMessage(UiText.success("Cleared memories for '" + definition.getDisplayName() + "'."));
                    }
                    case "on", "off" -> {
                        boolean enabled = args[3].equalsIgnoreCase("on");
                        definition.setAiControlSettings(definition.getAiControlSettings().withMemoryEnabled(enabled));
                        definitionRepository.save(definition);
                        player.sendMessage(UiText.success("AI memory " + (enabled ? "enabled" : "disabled") + "."));
                    }
                    default -> { return false; }
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private boolean setNpcProperty(Player player, NpcDefinition definition, String[] args) {
        String property = args[3].toLowerCase(Locale.ROOT);
        switch (property) {
            case "spawnpoint" -> {
                if (args.length > 5) return false;
                Location location = args.length == 5 ? resolveLocation(player, args[4]) : player.getLocation();
                if (location == null) return true;
                definition.setSpawnpoint(location);
            }
            case "name" -> {
                if (args.length < 5) return false;
                String name = String.join(" ", Arrays.copyOfRange(args, 4, args.length)).trim();
                if (name.isBlank()) return false;
                definition.setDisplayName(name);
            }
            case "color" -> {
                if (args.length != 5) return false;
                try {
                    definition.setColor(NpcColor.valueOf(args[4].toUpperCase(Locale.ROOT).replace('-', '_')));
                } catch (IllegalArgumentException error) {
                    player.sendMessage(UiText.error("Unknown NPC color: " + args[4]));
                    return true;
                }
            }
            case "pickupitems" -> {
                if (args.length != 5) return false;
                Boolean value = parseToggle(args[4]);
                if (value == null) return false;
                definition.setItemPickup(value);
            }
            case "health" -> {
                if (args.length != 5) return false;
                try {
                    int health = Integer.parseInt(args[4]);
                    if (health < 0 || health > dev.blockfolk.model.CombatProfile.MAX_HEALTH)
                        throw new NumberFormatException();
                    definition.setCombatProfile(definition.getCombatProfile().withMaxHealth(health));
                } catch (NumberFormatException error) {
                    player.sendMessage(UiText.error("Health must be between 0 and "
                            + dev.blockfolk.model.CombatProfile.MAX_HEALTH + "."));
                    return true;
                }
            }
            default -> { return false; }
        }
        definitionRepository.save(definition);
        instanceRegistry.refreshDefinition(definition);
        player.sendMessage(UiText.success("Updated " + property + " for '" + definition.getDisplayName() + "'."));
        return true;
    }

    private Location resolveLocation(Player player, String name) {
        if (name.equalsIgnoreCase("here")) return player.getLocation();
        NamedLocation saved = locationRepository.find(name).orElse(null);
        if (saved == null) {
            player.sendMessage(UiText.error("Unknown saved location: " + name));
            return null;
        }
        Location location = saved.location().toLocation();
        if (location == null)
            player.sendMessage(UiText.error("The world for location '" + name + "' is not loaded."));
        return location;
    }

    private static Boolean parseToggle(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("blockfolk.admin")) {
            if (!(sender instanceof Player)) return List.of();
            if (args.length == 1) return filter(List.of("config"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("config"))
                return filter(List.of("ai"), args[1]);
            if (args.length == 3 && args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("ai"))
                return filter(List.of("mute-me"), args[2]);
            if (args.length == 4 && args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("ai")
                    && args[2].equalsIgnoreCase("mute-me"))
                return filter(List.of("on", "off"), args[3]);
            return List.of();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("create");
            suggestions.add("routes");
            suggestions.add("locations");
            suggestions.add("events");
            suggestions.add("npc");
            suggestions.add("config");
            return filter(suggestions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return filter(definitionRepository.findAll().stream().map(NpcDefinition::getKey).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("events")) {
            return filter(List.of("trigger"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("events") && args[1].equalsIgnoreCase("trigger")) {
            return filter(customEventRepository.findAll().stream().map(CustomEvent::getName).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("npc")) {
            return filter(List.of("edit", "set", "tp", "inventory", "memory", "events", "combat", "equipment",
                    "delete", "spawn", "duplicate"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("npc")) {
            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "set" -> filter(List.of("spawnpoint", "name", "color", "pickupitems", "health"), args[3]);
                case "tp" -> filter(List.of("here", "to", "toloc"), args[3]);
                case "memory" -> filter(List.of("on", "off", "open", "clear"), args[3]);
                case "spawn" -> filter(locationSuggestions(), args[3]);
                default -> List.of();
            };
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("npc")) {
            if (args[2].equalsIgnoreCase("tp") && args[3].equalsIgnoreCase("toloc")
                    || args[2].equalsIgnoreCase("set") && args[3].equalsIgnoreCase("spawnpoint"))
                return filter(locationSuggestions(), args[4]);
            if (args[2].equalsIgnoreCase("set"))
                return switch (args[3].toLowerCase(Locale.ROOT)) {
                    case "color" -> filter(Arrays.stream(NpcColor.values())
                            .map(color -> color.name().toLowerCase(Locale.ROOT)).toList(), args[4]);
                    case "pickupitems" -> filter(List.of("on", "off"), args[4]);
                    default -> List.of();
                };
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config"))
            return filter(List.of("ai"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("ai"))
            return filter(List.of("model", "mute-me"), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("ai")
                && args[2].equalsIgnoreCase("mute-me"))
            return filter(List.of("on", "off"), args[3]);
        return List.of();
    }

    private List<String> locationSuggestions() {
        List<String> names = new ArrayList<>();
        names.add("here");
        locationRepository.findAll().stream().map(NamedLocation::key).forEach(names::add);
        return names;
    }

    private NpcDefinition duplicate(NpcDefinition source) {
        return source.copyAs(source.getDisplayName() + " (copy)");
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
