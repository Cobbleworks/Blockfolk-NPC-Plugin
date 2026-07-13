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
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.gui.GuiService;
import dev.blockfolk.gui.CustomEventGuiService;
import dev.blockfolk.gui.RouteGuiService;
import dev.blockfolk.model.CustomEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.repository.CustomEventRepository;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.runtime.NpcBehaviourService;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import net.kyori.adventure.text.Component;

public final class BlockfolkCommand implements CommandExecutor, TabCompleter {

    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final GuiService guiService;
    private final RouteGuiService routeGuiService;
    private final CustomEventGuiService customEventGuiService;
    private final CustomEventRepository customEventRepository;
    private final NpcBehaviourService behaviourService;
    private final JavaPlugin plugin;

    public BlockfolkCommand(
            NpcDefinitionRepository definitionRepository,
            NpcInstanceRegistry instanceRegistry,
            GuiService guiService,
            RouteGuiService routeGuiService,
            CustomEventGuiService customEventGuiService,
            CustomEventRepository customEventRepository,
            NpcBehaviourService behaviourService,
            JavaPlugin plugin
    ) {
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.guiService = guiService;
        this.routeGuiService = routeGuiService;
        this.customEventGuiService = customEventGuiService;
        this.customEventRepository = customEventRepository;
        this.behaviourService = behaviourService;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blockfolk.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use Blockfolk."));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("config")
                && args[1].equalsIgnoreCase("-seconds-per-line")) {
            try {
                int seconds = Integer.parseInt(args[2]);
                if (seconds < 1) {
                    throw new NumberFormatException();
                }
                plugin.getConfig().set("seconds-per-line", seconds);
                plugin.saveConfig();
                sender.sendMessage(Component.text("Seconds per line set to " + seconds + "."));
            } catch (NumberFormatException exception) {
                sender.sendMessage(Component.text("Seconds per line must be a whole number of at least 1."));
            }
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("trigger")) {
            CustomEvent customEvent = customEventRepository.find(args[2]).orElse(null);
            if (customEvent == null) {
                sender.sendMessage(Component.text("Unknown custom event: " + args[2]));
                return true;
            }
            behaviourService.emitCustomEvent(customEvent.getName(), sender instanceof Player player ? player : null);
            sender.sendMessage(Component.text("Triggered custom event '" + customEvent.getName() + "'."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Blockfolk is currently managed in-game."));
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
                player.sendMessage(Component.text("An NPC with that key already exists."));
                return true;
            }
            definition.setSpawnpoint(player.getLocation());
            definitionRepository.save(definition);
            instanceRegistry.spawnPersistent(definition, definition.getSpawnpoint());
            guiService.openEditor(player, definition);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            NpcDefinition definition = definitionRepository.find(args[1]).orElse(null);
            if (definition == null) {
                player.sendMessage(Component.text("Unknown NPC: " + args[1]));
                return true;
            }
            Location spawnLocation;
            if (instanceRegistry.findByDefinition(definition).isEmpty()) {
                spawnLocation = definition.getSpawnpoint();
                if (spawnLocation == null) {
                    player.sendMessage(Component.text("Set a spawnpoint for this NPC first."));
                    return true;
                }
            } else {
                spawnLocation = player.getLocation();
            }
            instanceRegistry.spawnPersistent(definition, spawnLocation);
            player.sendMessage(Component.text("Spawned NPC copy of " + definition.getDisplayName() + "."));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("npc") && args[2].equalsIgnoreCase("duplicate")) {
            NpcDefinition source = definitionRepository.find(args[1]).orElse(null);
            if (source == null) {
                player.sendMessage(Component.text("Unknown NPC: " + args[1]));
                return true;
            }
            NpcDefinition copy = duplicate(source);
            if (definitionRepository.find(copy.getKey()).isPresent()) {
                player.sendMessage(Component.text("A copy of this preset already exists."));
                return true;
            }
            definitionRepository.save(copy);
            player.sendMessage(Component.text("Duplicated " + source.getDisplayName() + " as " + copy.getDisplayName() + "."));
            return true;
        }
        player.sendMessage(Component.text("Usage: /bf [create [name]|routes|events [trigger <event>]|npc <name> [duplicate]|config -seconds-per-line <seconds>]"));
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
            suggestions.add("config");
            return filter(suggestions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return filter(definitionRepository.findAll().stream()
                    .map(NpcDefinition::getKey)
                    .toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return filter(List.of("-seconds-per-line"), args[1]);
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
            return filter(List.of("duplicate"), args[2]);
        }
        return List.of();
    }

    private NpcDefinition duplicate(NpcDefinition source) {
        NpcDefinition copy = NpcDefinition.create(source.getDisplayName() + " (copy)");
        copy.setResolvedSkin(source.getSkinUrl(), source.getSkinTextureValue(), source.getSkinTextureSignature());
        copy.setSpawnpoint(source.getSpawnpoint());
        copy.setInventoryContents(source.getInventoryContents());
        copy.setArmorContents(source.getArmorContents());
        copy.setMainHand(source.getMainHand());
        copy.setOffHand(source.getOffHand());
        copy.setCombatProfile(source.getCombatProfile());
        copy.setMovementProfile(source.getMovementProfile());
        for (dev.blockfolk.model.BehaviourEvent event : dev.blockfolk.model.BehaviourEvent.values()) {
            copy.setBehaviourActions(event, source.getBehaviourActions(event));
        }
        for (String eventName : source.getCustomEventNames()) {
            copy.setCustomEventActions(eventName, source.getCustomEventActions(eventName));
        }
        return copy;
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
