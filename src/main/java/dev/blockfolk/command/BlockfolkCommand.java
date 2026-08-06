package dev.blockfolk.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.blockfolk.gui.GuiService;
import dev.blockfolk.gui.CustomEventGuiService;
import dev.blockfolk.gui.RouteGuiService;
import dev.blockfolk.model.CustomEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.CustomEventRepository;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.runtime.NpcBehaviourService;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.util.UiText;

public final class BlockfolkCommand implements CommandExecutor, TabCompleter {

    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final GuiService guiService;
    private final RouteGuiService routeGuiService;
    private final CustomEventGuiService customEventGuiService;
    private final CustomEventRepository customEventRepository;
    private final NpcBehaviourService behaviourService;

    public BlockfolkCommand(
            NpcDefinitionRepository definitionRepository,
            NpcInstanceRegistry instanceRegistry,
            GuiService guiService,
            RouteGuiService routeGuiService,
            CustomEventGuiService customEventGuiService,
            CustomEventRepository customEventRepository,
            NpcBehaviourService behaviourService
    ) {
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.guiService = guiService;
        this.routeGuiService = routeGuiService;
        this.customEventGuiService = customEventGuiService;
        this.customEventRepository = customEventRepository;
        this.behaviourService = behaviourService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blockfolk.admin")) {
            sender.sendMessage(UiText.error("You do not have permission to use Blockfolk."));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("trigger")) {
            CustomEvent customEvent = customEventRepository.find(args[2]).orElse(null);
            if (customEvent == null) {
                sender.sendMessage(UiText.error("Unknown custom event: " + args[2]));
                return true;
            }
            behaviourService.emitCustomEvent(customEvent.getName(), sender instanceof Player player ? player : null);
            sender.sendMessage(UiText.success("Triggered custom event '" + customEvent.getName() + "'."));
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
            NpcDefinition definition = NpcDefinition.create(name);
            if (definitionRepository.find(definition.getKey()).isPresent()) {
                player.sendMessage(UiText.error("An NPC with that key already exists."));
                return true;
            }
            definition.setSpawnpoint(player.getLocation());
            definitionRepository.save(definition);
            if (instanceRegistry.spawnPersistent(definition, definition.getSpawnpoint()) == null) {
                player.sendMessage(UiText.warning("Preset created, but its NPC could not be rendered."));
            }
            guiService.openEditor(player, definition);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            NpcDefinition definition = definitionRepository.find(args[1]).orElse(null);
            if (definition == null) {
                player.sendMessage(UiText.error("Unknown NPC: " + args[1]));
                return true;
            }
            guiService.openEditor(player, definition);
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("npc") && args[2].equalsIgnoreCase("spawn")) {
            NpcDefinition definition = definitionRepository.find(args[1]).orElse(null);
            if (definition == null) {
                player.sendMessage(UiText.error("Unknown NPC: " + args[1]));
                return true;
            }
            Location spawnLocation;
            if (instanceRegistry.findByDefinition(definition).isEmpty()) {
                spawnLocation = definition.getSpawnpoint();
                if (spawnLocation == null) {
                    player.sendMessage(UiText.warning("Set a spawnpoint for this NPC first."));
                    return true;
                }
                if (spawnLocation.getWorld() == null) {
                    player.sendMessage(UiText.warning("The NPC spawnpoint world is not loaded."));
                    return true;
                }
            } else {
                spawnLocation = player.getLocation();
            }
            if (instanceRegistry.spawnPersistent(definition, spawnLocation) == null) {
                player.sendMessage(UiText.error("Could not render the NPC copy."));
            } else {
                player.sendMessage(UiText.success("Spawned NPC copy of " + definition.getDisplayName() + "."));
            }
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
            definitionRepository.save(copy);
            player.sendMessage(UiText.success("Duplicated " + source.getDisplayName() + " as " + copy.getDisplayName() + "."));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("npc")
                && (args[2].equalsIgnoreCase("tphere") || args[2].equalsIgnoreCase("warpto"))) {
            NpcDefinition definition = definitionRepository.find(args[1]).orElse(null);
            if (definition == null) {
                player.sendMessage(UiText.error("Unknown NPC: " + args[1]));
                return true;
            }
            NpcInstance firstInstance = instanceRegistry.findByDefinition(definition).stream().findFirst().orElse(null);
            if (firstInstance == null) {
                player.sendMessage(UiText.warning("This NPC has no spawned instances."));
                return true;
            }
            if (args[2].equalsIgnoreCase("tphere")) {
                if (instanceRegistry.relocate(firstInstance, player.getLocation())) {
                    player.sendMessage(UiText.success("Teleported the first NPC instance to you."));
                } else {
                    player.sendMessage(UiText.error("Could not teleport the NPC instance."));
                }
            } else if (player.teleport(firstInstance.getLocation())) {
                player.sendMessage(UiText.success("Teleported to the first NPC instance."));
            } else {
                player.sendMessage(UiText.error("Could not teleport to the NPC instance."));
            }
            return true;
        }
        player.sendMessage(UiText.info("Usage: /bf [create [name]|routes|events [trigger <event>]|npc <name> [spawn|duplicate|tphere|warpto]]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("blockfolk.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("create");
            suggestions.add("routes");
            suggestions.add("events");
            suggestions.add("npc");
            return filter(suggestions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return filter(definitionRepository.findAll().stream()
                    .map(NpcDefinition::getKey)
                    .toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("events")) {
            return filter(List.of("trigger"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("trigger")) {
            return filter(customEventRepository.findAll().stream()
                    .map(CustomEvent::getName)
                    .toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("npc")) {
            return filter(List.of("spawn", "duplicate", "tphere", "warpto"), args[2]);
        }
        return List.of();
    }

    private NpcDefinition duplicate(NpcDefinition source) {
        return source.copyAs(source.getDisplayName() + " (copy)");
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
