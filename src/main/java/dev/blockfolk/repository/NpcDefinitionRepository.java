package dev.blockfolk.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.MovementProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.util.LocationCodec;

public final class NpcDefinitionRepository {

    private final JavaPlugin plugin;
    private final File definitionsFolder;
    private final File orderFile;
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
    private final List<String> definitionOrder = new ArrayList<>();

    public NpcDefinitionRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.definitionsFolder = new File(plugin.getDataFolder(), "definitions");
        this.orderFile = new File(plugin.getDataFolder(), "definition-order.yml");
    }

    public void loadAll() {
        definitions.clear();
        definitionOrder.clear();
        if (!definitionsFolder.exists() && !definitionsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create definitions folder.");
            return;
        }
        File[] files = definitionsFolder.listFiles((directory, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                NpcDefinition definition = load(file);
                definitions.put(definition.getKey(), definition);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to load NPC definition " + file.getName(), exception);
            }
        }
        loadOrder();
    }

    public Optional<NpcDefinition> find(String keyOrName) {
        return Optional.ofNullable(definitions.get(NpcDefinition.toKey(keyOrName)));
    }

    public Collection<NpcDefinition> findAll() {
        return definitionOrder.stream()
                .map(definitions::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public NpcDefinition save(NpcDefinition definition) {
        boolean added = definitions.put(definition.getKey(), definition) == null;
        if (added) {
            definitionOrder.add(definition.getKey());
            saveOrder();
        }
        File file = new File(definitionsFolder, definition.getKey() + ".yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("key", definition.getKey());
        configuration.set("display-name", definition.getDisplayName());
        configuration.set("skin-url", definition.getSkinUrl());
        configuration.set("skin-texture-value", definition.getSkinTextureValue());
        configuration.set("skin-texture-signature", definition.getSkinTextureSignature());
        if (definition.getSpawnpoint() != null) {
            LocationCodec.write(configuration.createSection("spawnpoint"), definition.getSpawnpoint());
        }
        configuration.set("inventory.contents", Arrays.asList(definition.getInventoryContents()));
        configuration.set("inventory.armor", Arrays.asList(definition.getArmorContents()));
        configuration.set("inventory.main-hand", definition.getMainHand());
        configuration.set("inventory.off-hand", definition.getOffHand());
        configuration.set("combat.max-health", definition.getCombatProfile().maxHealth());
        configuration.set("combat.respawn-seconds", definition.getCombatProfile().respawnSeconds());
        configuration.set("combat.aggression-level",
                definition.getCombatProfile().attackReaction().name().toLowerCase(Locale.ROOT));
        configuration.set("combat.targets.mobs", definition.getCombatProfile().targetMobs());
        configuration.set("combat.targets.animals", definition.getCombatProfile().targetAnimals());
        configuration.set("combat.targets.players", definition.getCombatProfile().targetPlayers());
        configuration.set("combat.targets.npcs", definition.getCombatProfile().targetNpcs());
        configuration.set("combat.alliance", definition.getCombatProfile().alliance());
        configuration.set("combat.show-boss-bar", definition.getCombatProfile().showBossBar());
        configuration.set("combat.dropped-experience", definition.getCombatProfile().droppedExperience());
        configuration.set("movement.speed", definition.getMovementProfile().walkingSpeed().name().toLowerCase(Locale.ROOT));
        configuration.set("properties.show-name", definition.isShowName());
        configuration.set("properties.look-at-player", definition.isLookAtPlayer());
        configuration.set("properties.item-pickup", definition.isItemPickup());
        for (BehaviourEvent event : BehaviourEvent.values()) {
            List<Map<String, Object>> actions = BehaviourActionCodec.encodeList(definition.getBehaviourActions(event));
            configuration.set("behaviours." + event.name().toLowerCase(Locale.ROOT), actions.isEmpty() ? null : actions);
        }
        for (String eventName : definition.getCustomEventNames()) {
            List<Map<String, Object>> actions = BehaviourActionCodec.encodeList(
                    definition.getCustomEventActions(eventName));
            configuration.set("custom-event-behaviours." + encodeEventName(eventName), actions);
        }
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save NPC definition " + definition.getKey(), exception);
        }
        return definition;
    }

    public void reorder(List<String> orderedKeys) {
        List<String> normalized = orderedKeys.stream().map(NpcDefinition::toKey).toList();
        if (normalized.size() != definitions.size()
                || new HashSet<>(normalized).size() != normalized.size()
                || !definitions.keySet().containsAll(normalized)) {
            throw new IllegalArgumentException("The NPC order must contain every definition exactly once.");
        }
        definitionOrder.clear();
        definitionOrder.addAll(normalized);
        saveOrder();
    }

    public boolean delete(NpcDefinition definition) {
        if (definitions.remove(definition.getKey()) == null) {
            return false;
        }
        int orderIndex = definitionOrder.indexOf(definition.getKey());
        definitionOrder.remove(definition.getKey());
        File file = new File(definitionsFolder, definition.getKey() + ".yml");
        if (file.exists() && !file.delete()) {
            definitions.put(definition.getKey(), definition);
            definitionOrder.add(Math.max(0, orderIndex), definition.getKey());
            throw new IllegalStateException("Could not delete NPC definition " + definition.getKey());
        }
        saveOrder();
        return true;
    }

    private void loadOrder() {
        definitionOrder.clear();
        List<String> stored = YamlConfiguration.loadConfiguration(orderFile).getStringList("order");
        Set<String> seen = new HashSet<>();
        for (String key : stored) {
            String normalized = NpcDefinition.toKey(key);
            if (definitions.containsKey(normalized) && seen.add(normalized)) {
                definitionOrder.add(normalized);
            }
        }
        definitions.keySet().stream()
                .filter(seen::add)
                .sorted(Comparator.naturalOrder())
                .forEach(definitionOrder::add);
    }

    private void saveOrder() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("order", definitionOrder);
        try {
            configuration.save(orderFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save NPC definition order", exception);
        }
    }

    private NpcDefinition load(File file) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String key = configuration.getString("key", file.getName().replaceFirst("\\.yml$", ""));
        NpcDefinition definition = new NpcDefinition(NpcDefinition.toKey(key));
        definition.setDisplayName(configuration.getString("display-name", definition.getKey()));
        definition.setResolvedSkin(
                configuration.getString("skin-url"),
                configuration.getString("skin-texture-value"),
                configuration.getString("skin-texture-signature")
        );
        definition.setSpawnpoint(LocationCodec.read(configuration.getConfigurationSection("spawnpoint")));
        definition.setInventoryContents(readItemArray(configuration, "inventory.contents", 36));
        definition.setArmorContents(readItemArray(configuration, "inventory.armor", 4));
        definition.setMainHand(configuration.getItemStack("inventory.main-hand"));
        definition.setOffHand(configuration.getItemStack("inventory.off-hand"));
        definition.setCombatProfile(new CombatProfile(
                configuration.getInt("combat.max-health", 0),
                configuration.getInt("combat.respawn-seconds", 0),
                AttackReaction.fromStored(configuration.getString("combat.aggression-level")),
                configuration.getBoolean("combat.targets.mobs", false),
                configuration.getBoolean("combat.targets.animals", false),
                configuration.getBoolean("combat.targets.players", false),
                configuration.getBoolean("combat.targets.npcs", false),
                configuration.getString("combat.alliance"),
                configuration.getBoolean("combat.show-boss-bar", false),
                configuration.getInt("combat.dropped-experience", 0)
        ));
        definition.setMovementProfile(MovementProfile.disabled().withWalkingSpeed(
                WalkingSpeed.fromStored(configuration.getString("movement.speed"))));
        definition.setShowName(configuration.getBoolean("properties.show-name", true));
        definition.setLookAtPlayer(configuration.getBoolean("properties.look-at-player", true));
        definition.setItemPickup(configuration.getBoolean("properties.item-pickup", false));
        for (BehaviourEvent event : BehaviourEvent.values()) {
            List<BehaviourAction> actions = new ArrayList<>();
            String path = "behaviours." + event.name().toLowerCase(Locale.ROOT);
            if (!configuration.contains(path)) {
                path = switch (event) {
                    // Pre-Sunrise/Noon/Sunset definitions used these event keys.
                    case SUNRISE ->
                        "behaviours.dawn";
                    case NOON ->
                        "behaviours.midday";
                    case SUNSET ->
                        "behaviours.morning";
                    default ->
                        path;
                };
            }
            for (Map<?, ?> entry : configuration.getMapList(path)) {
                Object type = entry.get("type");
                if (type == null) {
                    continue;
                }
                try {
                    actions.add(BehaviourActionCodec.decode(entry));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning(() -> "Ignoring unknown behaviour action '" + type + "' in " + file.getName());
                }
            }
            definition.setBehaviourActions(event, actions);
        }
        ConfigurationSection custom = configuration.getConfigurationSection("custom-event-behaviours");
        if (custom != null) {
            for (String encodedName : custom.getKeys(false)) {
                String eventName;
                try {
                    eventName = decodeEventName(encodedName);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning(() -> "Ignoring malformed custom event name in " + file.getName());
                    continue;
                }
                List<BehaviourAction> actions = new ArrayList<>();
                for (Map<?, ?> entry : custom.getMapList(encodedName)) {
                    Object type = entry.get("type");
                    if (type == null) {
                        continue;
                    }
                    try {
                        actions.add(BehaviourActionCodec.decode(entry));
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning(() -> "Ignoring unknown custom-event action '" + type + "' in " + file.getName());
                    }
                }
                definition.setCustomEventActions(eventName, actions);
            }
        }
        return definition;
    }

    private static String encodeEventName(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decodeEventName(String value) {
        return new String(java.util.Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    private ItemStack[] readItemArray(YamlConfiguration configuration, String path, int size) {
        ItemStack[] items = new ItemStack[size];
        List<?> list = configuration.getList(path, new ArrayList<>());
        for (int index = 0; index < Math.min(list.size(), size); index++) {
            Object value = list.get(index);
            if (value instanceof ItemStack itemStack) {
                items[index] = itemStack;
            }
        }
        return items;
    }
}
