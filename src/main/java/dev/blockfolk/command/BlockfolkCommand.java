package dev.blockfolk.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import dev.blockfolk.BlockfolkPlugin;
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
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class BlockfolkCommand implements CommandExecutor, TabCompleter, BasicCommand {

    private static final List<String> CONFIG_OPTIONS = List.of("--model", "--timeout-seconds", "--max-tokens");

    private final BlockfolkPlugin plugin;
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final GuiService guiService;
    private final RouteGuiService routeGuiService;
    private final CustomEventGuiService customEventGuiService;
    private final CustomEventRepository customEventRepository;
    private final NpcBehaviourService behaviourService;

    public BlockfolkCommand(BlockfolkPlugin plugin, NpcDefinitionRepository definitionRepository,
            NpcInstanceRegistry instanceRegistry, GuiService guiService, RouteGuiService routeGuiService,
            CustomEventGuiService customEventGuiService, CustomEventRepository customEventRepository,
            NpcBehaviourService behaviourService) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.guiService = guiService;
        this.routeGuiService = routeGuiService;
        this.customEventGuiService = customEventGuiService;
        this.customEventRepository = customEventRepository;
        this.behaviourService = behaviourService;
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
        return sender.hasPermission("blockfolk.admin");
    }

    @Override
    public String permission() {
        return "blockfolk.admin";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
        if (args.length > 0 && args[0].equalsIgnoreCase("config")) {
            return handleConfig(sender, args);
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
        if ((args.length == 2 || args.length == 3 && args[2].equalsIgnoreCase("edit"))
                && args[0].equalsIgnoreCase("npc")) {
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
            player.sendMessage(
                    UiText.success("Duplicated " + source.getDisplayName() + " as " + copy.getDisplayName() + "."));
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
        player.sendMessage(UiText.info(
                "Usage: /bf [config|create [name]|routes|locations|events [trigger <event>]|npc <name> [edit|spawn|duplicate|tphere|warpto]]"));
        return true;
    }

    private boolean handleConfig(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage(
                    UiText.info("OpenRouter model: " + plugin.getConfig().getString("openrouter.model", "")));
            sender.sendMessage(UiText.info("Request timeout: "
                    + plugin.getConfig().getInt("openrouter.timeout-seconds", 12) + " seconds; max tokens: "
                    + plugin.getConfig().getInt("openrouter.max-tokens", 1600) + "."));
            sender.sendMessage(UiText.info("Usage: /bf config <" + String.join("|", CONFIG_OPTIONS) + "> <value>"));
            return true;
        }
        if (args.length != 3 || !CONFIG_OPTIONS.contains(args[1].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(UiText.error("Usage: /bf config <" + String.join("|", CONFIG_OPTIONS) + "> <value>"));
            return true;
        }

        String option = args[1].toLowerCase(Locale.ROOT);
        String path;
        Object value;
        if (option.equals("--model")) {
            if (args[2].isBlank() || args[2].chars()
                    .anyMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character))) {
                sender.sendMessage(UiText.error("Model must be a non-empty identifier without spaces."));
                return true;
            }
            path = "openrouter.model";
            value = args[2];
        } else {
            int number;
            try {
                number = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(UiText.error("Enter a whole number for " + option + "."));
                return true;
            }
            int minimum = option.equals("--timeout-seconds") ? 2 : 350;
            if (number < minimum) {
                sender.sendMessage(UiText.error(option + " must be at least " + minimum + "."));
                return true;
            }
            path = option.equals("--timeout-seconds") ? "openrouter.timeout-seconds" : "openrouter.max-tokens";
            value = number;
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration diskConfig = new YamlConfiguration();
        try {
            diskConfig.load(configFile);
            diskConfig.set(path, value);
            diskConfig.save(configFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Could not save Blockfolk config.yml: " + exception.getMessage());
            sender.sendMessage(UiText.error("Could not save config.yml; the setting was not changed."));
            return true;
        }
        plugin.getConfig().set(path, value);
        plugin.refreshOpenRouterClient();
        sender.sendMessage(UiText.success("Saved " + path + " as " + value + ". New AI requests use this value."));
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
            suggestions.add("locations");
            suggestions.add("events");
            suggestions.add("npc");
            suggestions.add("config");
            return filter(suggestions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return filter(CONFIG_OPTIONS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "--model" -> filter(List.of(plugin.getConfig().getString("openrouter.model", "")), args[2]);
                case "--timeout-seconds" -> filter(
                        List.of(String.valueOf(plugin.getConfig().getInt("openrouter.timeout-seconds", 12))), args[2]);
                case "--max-tokens" ->
                    filter(List.of(String.valueOf(plugin.getConfig().getInt("openrouter.max-tokens", 1600))), args[2]);
                default -> List.of();
            };
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
            return filter(List.of("edit", "spawn", "duplicate", "tphere", "warpto"), args[2]);
        }
        return List.of();
    }

    private NpcDefinition duplicate(NpcDefinition source) {
        return source.copyAs(source.getDisplayName() + " (copy)");
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
