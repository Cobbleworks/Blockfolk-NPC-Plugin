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
import dev.blockfolk.gui.RouteGuiService;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import net.kyori.adventure.text.Component;

public final class BlockfolkCommand implements CommandExecutor, TabCompleter {

    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final GuiService guiService;
    private final RouteGuiService routeGuiService;

    public BlockfolkCommand(
            NpcDefinitionRepository definitionRepository,
            NpcInstanceRegistry instanceRegistry,
            GuiService guiService,
            RouteGuiService routeGuiService
    ) {
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.guiService = guiService;
        this.routeGuiService = routeGuiService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blockfolk.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use Blockfolk."));
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
            guiService.openEditor(player, definition);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            NpcDefinition definition = definitionRepository.find(args[1]).orElse(null);
            if (definition == null) {
                player.sendMessage(Component.text("Unknown NPC: " + args[1]));
                return true;
            }
            Location spawnpoint = definition.getSpawnpoint();
            if (spawnpoint == null) {
                player.sendMessage(Component.text("Set a spawnpoint for this NPC first."));
                return true;
            }
            instanceRegistry.spawnPersistent(definition, spawnpoint);
            player.sendMessage(Component.text("Spawned NPC copy of " + definition.getDisplayName() + "."));
            return true;
        }
        player.sendMessage(Component.text("Usage: /bf [create [name]|routes|npc <name>]"));
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
            suggestions.add("npc");
            return filter(suggestions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return filter(definitionRepository.findAll().stream()
                    .map(NpcDefinition::getKey)
                    .toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
