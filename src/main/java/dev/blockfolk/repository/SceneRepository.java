package dev.blockfolk.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.Scene;
import dev.blockfolk.model.SceneEnding;
import dev.blockfolk.model.SceneRole;
import dev.blockfolk.model.SceneTrigger;

/** Stores every scene in {@code scenes.yml}. */
public final class SceneRepository {

    static final String LEGACY_EVENTS_FILE = "custom-events.yml";

    private final File file;
    private final DebouncedYamlWriter writer;
    private final Logger logger;
    private final Map<String, Scene> scenes = new LinkedHashMap<>();

    public SceneRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "scenes.yml");
        this.writer = new DebouncedYamlWriter(plugin);
        this.logger = plugin.getLogger();
    }

    public void loadAll() {
        scenes.clear();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("scenes");
        if (root == null)
            return;
        for (String storageKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(storageKey);
            if (section == null)
                continue;
            try {
                Scene scene = read(section);
                if (scenes.putIfAbsent(scene.getKey(), scene) != null)
                    logger.warning(() -> "Ignoring a second scene with key '" + scene.getKey() + "' in scenes.yml.");
            } catch (IllegalArgumentException exception) {
                logger.warning(() -> "Ignoring a malformed scene in scenes.yml: " + exception.getMessage());
            }
        }
    }

    public Optional<Scene> find(String keyOrName) {
        return Scene.tryKey(keyOrName).map(scenes::get);
    }

    /** Every scene sorted by key, so groups stay together. */
    public Collection<Scene> findAll() {
        return scenes.values().stream().sorted(Comparator.comparing(Scene::getKey)).toList();
    }

    public Scene save(Scene scene) {
        scenes.put(scene.getKey(), scene);
        saveAll();
        return scene;
    }

    public boolean delete(Scene scene) {
        if (scenes.remove(scene.getKey()) == null)
            return false;
        saveAll();
        return true;
    }

    public void flush() {
        writer.flush();
    }

    private void saveAll() {
        writer.queue(file, this::serialize);
    }

    private YamlConfiguration serialize() {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection root = configuration.createSection("scenes");
        int index = 0;
        for (Scene scene : findAll())
            write(root.createSection(Integer.toString(index++)), scene);
        return configuration;
    }

    static void write(ConfigurationSection section, Scene scene) {
        section.set("key", scene.getKey());
        section.set("name", scene.getDisplayName());
        section.set("description", scene.getDescription().isBlank() ? null : scene.getDescription());
        section.set("icon", scene.getIcon());
        section.set("enabled", scene.isEnabled());
        if (scene.hasArea()) {
            section.set("area.location", scene.getAreaLocationKey());
            section.set("area.radius", scene.getAreaRadius());
        }
        section.set("triggers", scene.getTriggers().stream().map(SceneTrigger::storedName).toList());
        section.set("chance-percent", scene.getChancePercent());
        section.set("cooldown-seconds", scene.getCooldownSeconds());
        section.set("end.mode", scene.getEnding().storedName());
        section.set("end.duration-seconds", scene.getDurationSeconds());
        section.set("end.time", scene.getEndPeriod().lowerName());
        List<Map<String, Object>> roles = new ArrayList<>();
        for (SceneRole role : scene.getRoles()) {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("id", role.getId());
            if (role.getPresetKey() != null)
                stored.put("preset", role.getPresetKey());
            stored.put("lane", BehaviourActionCodec.encodeList(role.getLane()));
            stored.put("wrap-up", BehaviourActionCodec.encodeList(role.getWrapUp()));
            Map<String, Object> reactions = new LinkedHashMap<>();
            role.getReactions().forEach((event, actions) -> reactions.put(event.name().toLowerCase(Locale.ROOT),
                    BehaviourActionCodec.encodeList(actions)));
            if (!reactions.isEmpty())
                stored.put("reactions", reactions);
            roles.add(stored);
        }
        section.set("roles", roles);
    }

    static Scene read(ConfigurationSection section) {
        String key = section.getString("key");
        if (key == null)
            throw new IllegalArgumentException("A scene key is missing.");
        Scene scene = new Scene(key);
        scene.setDisplayName(section.getString("name", key));
        scene.setDescription(section.getString("description", ""));
        Object icon = section.get("icon");
        if (icon instanceof ItemStack item)
            scene.setIcon(item);
        scene.setEnabled(section.getBoolean("enabled", true));
        scene.setAreaLocationKey(section.getString("area.location"));
        scene.setAreaRadius(section.getInt("area.radius", Scene.DEFAULT_RADIUS));
        for (String stored : section.getStringList("triggers"))
            SceneTrigger.fromStored(stored).ifPresent(trigger -> scene.setTrigger(trigger, true));
        scene.setChancePercent(section.getInt("chance-percent", 100));
        scene.setCooldownSeconds(section.getInt("cooldown-seconds", Scene.DEFAULT_COOLDOWN_SECONDS));
        scene.setEnding(SceneEnding.fromStored(section.getString("end.mode")));
        scene.setDurationSeconds(section.getInt("end.duration-seconds", Scene.DEFAULT_DURATION_SECONDS));
        scene.setEndPeriod(period(section.getString("end.time")));
        for (Map<?, ?> stored : section.getMapList("roles")) {
            Object id = stored.get("id");
            if (id == null)
                continue;
            // Older files also store a role name and a cast; NPCs now play
            // themselves and every free instance takes part, so both are ignored.
            Object preset = stored.get("preset");
            SceneRole role = new SceneRole(id.toString(), preset == null ? null : preset.toString());
            role.setLane(BehaviourActionCodec.decodeList(stored.get("lane")));
            role.setWrapUp(BehaviourActionCodec.decodeList(stored.get("wrap-up")));
            if (stored.get("reactions") instanceof Map<?, ?> reactions) {
                for (Map.Entry<?, ?> entry : reactions.entrySet()) {
                    try {
                        BehaviourEvent event = BehaviourEvent
                                .valueOf(entry.getKey().toString().toUpperCase(Locale.ROOT));
                        if (SceneRole.REACTION_EVENTS.contains(event))
                            role.setReaction(event, BehaviourActionCodec.decodeList(entry.getValue()));
                    } catch (IllegalArgumentException ignored) {
                        // An unknown event name cannot be shown or run.
                    }
                }
            }
            if (scene.findRole(role.getId()).isEmpty() && scene.getRoles().size() < Scene.MAX_ROLES)
                scene.addRole(role);
        }
        return scene;
    }

    private static DayPeriod period(String value) {
        if (value == null)
            return DayPeriod.EVENING;
        try {
            return DayPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DayPeriod.EVENING;
        }
    }

    /**
     * Turns the removed custom events into scenes: every event becomes a scene
     * without triggers, and every preset that reacted to it becomes an
     * all-instances role whose lane is that reaction. Emit Custom Event actions
     * already load as Start Scene with the same name, so they start the new scene.
     *
     * @return how many scenes were created
     */
    public int migrateCustomEvents(Collection<NpcDefinition> definitions, Consumer<NpcDefinition> saveDefinition) {
        File legacyFile = new File(file.getParentFile(), LEGACY_EVENTS_FILE);
        YamlConfiguration legacy = legacyFile.isFile() ? YamlConfiguration.loadConfiguration(legacyFile) : null;
        Map<String, Map<String, List<BehaviourAction>>> reactions = new LinkedHashMap<>();
        for (NpcDefinition definition : definitions) {
            if (!definition.hasLegacyCustomEventActions())
                continue;
            reactions.put(definition.getKey(), definition.takeLegacyCustomEventActions());
        }
        if (legacy == null && reactions.isEmpty())
            return 0;
        int created = migrate(legacy, definitions, reactions);
        // Write the scenes before the presets drop their old rows.
        saveAll();
        flush();
        for (NpcDefinition definition : definitions) {
            if (reactions.containsKey(definition.getKey()))
                saveDefinition.accept(definition);
        }
        if (legacyFile.isFile()) {
            File archived = new File(legacyFile.getParentFile(), LEGACY_EVENTS_FILE + ".migrated");
            try {
                java.nio.file.Files.move(legacyFile.toPath(), archived.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                logger.warning(() -> "Could not rename " + LEGACY_EVENTS_FILE + " after migrating it to scenes: "
                        + exception.getMessage());
            }
        }
        return created;
    }

    /**
     * @param reactions
     *            per preset key, the custom-event rows it had, by event name
     */
    int migrate(YamlConfiguration legacy, Collection<NpcDefinition> definitions,
            Map<String, Map<String, List<BehaviourAction>>> reactions) {
        Map<String, ConfigurationSection> metadata = new LinkedHashMap<>();
        Set<String> names = new LinkedHashSet<>();
        if (legacy != null) {
            ConfigurationSection root = legacy.getConfigurationSection("events");
            List<String> order = legacy.getStringList("order");
            if (root != null) {
                for (String storageKey : root.getKeys(false)) {
                    ConfigurationSection section = root.getConfigurationSection(storageKey);
                    String name = section == null ? null : section.getString("name", storageKey);
                    if (name != null)
                        metadata.put(name, section);
                }
            }
            order.stream().filter(metadata::containsKey).forEach(names::add);
            names.addAll(metadata.keySet());
        }
        reactions.values().forEach(rows -> names.addAll(rows.keySet()));
        int created = 0;
        for (String name : names) {
            String key = Scene.tryKey(name).orElse(null);
            if (key == null) {
                logger.warning(() -> "Could not turn custom event '" + name + "' into a scene: invalid name.");
                continue;
            }
            Scene scene = scenes.get(key);
            boolean fresh = scene == null;
            if (fresh) {
                scene = new Scene(key);
                scene.setDisplayName(name.substring(name.lastIndexOf('/') + 1));
                ConfigurationSection section = metadata.get(name);
                if (section != null) {
                    scene.setDescription(section.getString("description", ""));
                    if (section.get("icon") instanceof ItemStack icon)
                        scene.setIcon(icon);
                }
                // Custom events never started on their own and had no cooldown.
                scene.setCooldownSeconds(0);
            }
            for (NpcDefinition definition : definitions) {
                List<BehaviourAction> actions = reactions.getOrDefault(definition.getKey(), Map.of()).get(name);
                if (actions == null || actions.isEmpty() || scene.getRoles().size() >= Scene.MAX_ROLES)
                    continue;
                if (scene.findRole(SceneRole.normalizeId(definition.getKey())).isPresent())
                    continue;
                SceneRole role = new SceneRole(definition.getKey(), definition.getKey());
                role.setLane(actions);
                scene.addRole(role);
            }
            scenes.put(key, scene);
            if (fresh)
                created++;
        }
        return created;
    }
}
