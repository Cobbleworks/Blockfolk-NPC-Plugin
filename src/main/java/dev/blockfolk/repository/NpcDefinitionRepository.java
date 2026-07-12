package dev.blockfolk.repository;

import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.AggressionLevel;
import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.MovementProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.util.LocationCodec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class NpcDefinitionRepository {

    private final JavaPlugin plugin;
    private final File definitionsFolder;
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();

    public NpcDefinitionRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.definitionsFolder = new File(plugin.getDataFolder(), "definitions");
    }

    public void loadAll() {
        definitions.clear();
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
    }

    public Optional<NpcDefinition> find(String keyOrName) {
        return Optional.ofNullable(definitions.get(NpcDefinition.toKey(keyOrName)));
    }

    public Collection<NpcDefinition> findAll() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(NpcDefinition::getKey))
                .toList();
    }

    public NpcDefinition save(NpcDefinition definition) {
        definitions.put(definition.getKey(), definition);
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
        configuration.set("dialog.lines", definition.getDialogLines());
        configuration.set("combat.enabled", !definition.getCombatProfile().invulnerable());
        configuration.set("combat.max-health", definition.getCombatProfile().maxHealth());
        configuration.set("combat.respawn-seconds", definition.getCombatProfile().respawnSeconds());
        configuration.set("combat.aggression", null);
        configuration.set("combat.reaction-to-attacks",
                definition.getCombatProfile().attackReaction().name().toLowerCase(Locale.ROOT));
        configuration.set("combat.targets.mobs", definition.getCombatProfile().targetMobs());
        configuration.set("combat.targets.animals", definition.getCombatProfile().targetAnimals());
        configuration.set("combat.targets.players", definition.getCombatProfile().targetPlayers());
        configuration.set("combat.targets.npcs", definition.getCombatProfile().targetNpcs());
        configuration.set("combat.alliance", definition.getCombatProfile().alliance());
        configuration.set("combat.shoutout", null);
        configuration.set("movement.enabled", null);
        configuration.set("movement.route", null);
        configuration.set("movement.speed", definition.getMovementProfile().walkingSpeed().name().toLowerCase(Locale.ROOT));
        for (BehaviourEvent event : BehaviourEvent.values()) {
            List<Map<String, Object>> actions = definition.getBehaviourActions(event).stream().map(action -> {
                Map<String, Object> stored = new LinkedHashMap<String, Object>();
                stored.put("type", action.type().name().toLowerCase(Locale.ROOT));
                if (action.value() != null) {
                    stored.put("value", action.value());
                }
                return stored;
            }).toList();
            configuration.set("behaviours." + event.name().toLowerCase(Locale.ROOT), actions.isEmpty() ? null : actions);
        }
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save NPC definition " + definition.getKey(), exception);
        }
        return definition;
    }

    public boolean delete(NpcDefinition definition) {
        if (definitions.remove(definition.getKey()) == null) {
            return false;
        }
        File file = new File(definitionsFolder, definition.getKey() + ".yml");
        if (file.exists() && !file.delete()) {
            definitions.put(definition.getKey(), definition);
            throw new IllegalStateException("Could not delete NPC definition " + definition.getKey());
        }
        return true;
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
        definition.setDialogLines(configuration.getStringList("dialog.lines"));
        int legacyHealth = configuration.getBoolean("combat.enabled", false) ? 20 : 0;
        AggressionLevel legacyAggression = AggressionLevel.fromStored(configuration.getString("combat.aggression"));
        boolean hasNewAggressionSettings = configuration.contains("combat.reaction-to-attacks")
                || configuration.contains("combat.targets");
        AttackReaction attackReaction = hasNewAggressionSettings
                ? AttackReaction.fromStored(configuration.getString("combat.reaction-to-attacks"))
                : switch (legacyAggression) {
                    case FLEE -> AttackReaction.FLEE;
                    case FIGHT_BACK, FIGHTS_ON_SIGHT -> AttackReaction.FIGHT_BACK;
                    case NONE -> AttackReaction.IGNORE;
                };
        boolean legacySightTargeting = !hasNewAggressionSettings
                && legacyAggression == AggressionLevel.FIGHTS_ON_SIGHT;
        definition.setCombatProfile(new CombatProfile(
                configuration.getInt("combat.max-health", legacyHealth),
                configuration.getInt("combat.respawn-seconds", 0),
                attackReaction,
                configuration.getBoolean("combat.targets.mobs", legacySightTargeting),
                configuration.getBoolean("combat.targets.animals", legacySightTargeting),
                configuration.getBoolean("combat.targets.players", legacySightTargeting),
                configuration.getBoolean("combat.targets.npcs", legacySightTargeting),
                configuration.getString("combat.alliance"),
                configuration.getString("combat.shoutout")
        ));
        definition.setMovementProfile(new MovementProfile(
                configuration.getBoolean("movement.enabled", false),
                configuration.getString("movement.route"),
                WalkingSpeed.fromStored(configuration.getString("movement.speed"))
        ));
        for (BehaviourEvent event : BehaviourEvent.values()) {
            List<BehaviourAction> actions = new ArrayList<>();
            for (Map<?, ?> entry : configuration.getMapList("behaviours." + event.name().toLowerCase(Locale.ROOT))) {
                Object type = entry.get("type");
                if (type == null) {
                    continue;
                }
                try {
                    actions.add(new BehaviourAction(BehaviourActionType.fromStored(type.toString()),
                            entry.get("value") == null ? null : entry.get("value").toString()));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring unknown behaviour action '" + type + "' in " + file.getName());
                }
            }
            definition.setBehaviourActions(event, actions);
        }
        // One-time compatibility migration. New saves contain only behaviours for these features.
        String legacyShoutout = configuration.getString("combat.shoutout");
        if (legacyShoutout != null && definition.getBehaviourActions(BehaviourEvent.COMBAT_ENTERED).isEmpty()) {
            definition.addBehaviourAction(BehaviourEvent.COMBAT_ENTERED,
                    new BehaviourAction(BehaviourActionType.SEND_DIALOG, legacyShoutout));
        }
        String legacyRoute = configuration.getString("movement.route");
        if (legacyRoute != null && definition.getBehaviourActions(BehaviourEvent.SPAWN).isEmpty()) {
            definition.addBehaviourAction(BehaviourEvent.SPAWN,
                    new BehaviourAction(BehaviourActionType.SET_ROUTE, legacyRoute));
        }
        return definition;
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
