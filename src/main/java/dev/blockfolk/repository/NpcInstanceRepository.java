package dev.blockfolk.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.StoredLocation;
import dev.blockfolk.util.LocationCodec;

public final class NpcInstanceRepository {

    private final File file;
    private final DebouncedYamlWriter writer;
    private final JavaPlugin plugin;
    private final Map<String, Map<String, Object>> preservedMalformed = new LinkedHashMap<>();

    public NpcInstanceRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "instances.yml");
        this.writer = new DebouncedYamlWriter(plugin);
    }

    public List<NpcInstance> loadAll() {
        List<NpcInstance> instances = new ArrayList<>();
        preservedMalformed.clear();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("instances");
        if (root == null) {
            return instances;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String definitionKey = section.getString("definition");
            if (definitionKey == null) {
                preserveMalformed(key, section, "definition is missing");
                continue;
            }
            StoredLocation location = LocationCodec.readStored(section.getConfigurationSection("location"));
            if (location == null) {
                preserveMalformed(key, section, "location is malformed");
                continue;
            }
            StoredLocation spawnLocation = LocationCodec.readStored(section.getConfigurationSection("spawn-location"));
            try {
                NpcInstance instance = new NpcInstance(UUID.fromString(key), definitionKey, location,
                        spawnLocation == null ? location : spawnLocation, section.getLong("respawn-at", 0L));
                readOverrides(section, instance);
                instances.add(instance);
            } catch (IllegalArgumentException exception) {
                preserveMalformed(key, section, exception.getMessage());
            }
        }
        return instances;
    }

    public void saveAll(Collection<NpcInstance> instances) {
        writer.queue(file, () -> serialize(instances));
    }

    private YamlConfiguration serialize(Collection<NpcInstance> instances) {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection root = configuration.createSection("instances");
        preservedMalformed.forEach((key, values) -> {
            ConfigurationSection section = root.createSection(key);
            values.forEach(section::set);
        });
        for (NpcInstance instance : instances) {
            ConfigurationSection section = root.createSection(instance.getId().toString());
            section.set("definition", instance.getDefinitionKey());
            LocationCodec.write(section.createSection("location"), instance.getStoredLocation());
            LocationCodec.write(section.createSection("spawn-location"), instance.getStoredSpawnLocation());
            if (instance.isAwaitingRespawn())
                section.set("respawn-at", instance.getRespawnAtEpochMillis());
            writeOverrides(section, instance);
        }
        return configuration;
    }

    /**
     * Reads the optional variant overrides. Absent keys inherit from the preset.
     */
    static void readOverrides(ConfigurationSection section, NpcInstance instance) {
        instance.setLabel(section.getString("label"));
        ConfigurationSection skin = section.getConfigurationSection("skin");
        if (skin != null)
            instance.setResolvedSkin(skin.getString("url"), skin.getString("value"), skin.getString("signature"));
        ConfigurationSection swaps = section.getConfigurationSection("route-swaps");
        if (swaps != null) {
            for (String from : swaps.getKeys(false)) {
                try {
                    instance.setRouteSwap(from, swaps.getString(from));
                } catch (IllegalArgumentException ignored) {
                    // A malformed swap just falls back to the preset's route.
                }
            }
        }
        ConfigurationSection behaviours = section.getConfigurationSection("behaviours");
        if (behaviours != null) {
            for (String name : behaviours.getKeys(false)) {
                BehaviourEvent event;
                try {
                    event = BehaviourEvent.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                // An empty list is kept: it means the variant does nothing there.
                instance.setBehaviourOverride(event, BehaviourActionCodec.decodeList(behaviours.getList(name)));
            }
        }
    }

    /**
     * Writes only the overrides that are set, so plain copies stay unchanged.
     */
    static void writeOverrides(ConfigurationSection section, NpcInstance instance) {
        if (instance.getLabel() != null)
            section.set("label", instance.getLabel());
        if (instance.hasSkinOverride()) {
            ConfigurationSection skin = section.createSection("skin");
            if (instance.getSkinUrl() != null)
                skin.set("url", instance.getSkinUrl());
            if (instance.getSkinTextureValue() != null)
                skin.set("value", instance.getSkinTextureValue());
            if (instance.getSkinTextureSignature() != null)
                skin.set("signature", instance.getSkinTextureSignature());
        }
        if (!instance.getRouteSwaps().isEmpty()) {
            ConfigurationSection swaps = section.createSection("route-swaps");
            instance.getRouteSwaps().forEach(swaps::set);
        }
        if (!instance.getBehaviourOverrideEvents().isEmpty()) {
            ConfigurationSection behaviours = section.createSection("behaviours");
            for (BehaviourEvent event : instance.getBehaviourOverrideEvents())
                behaviours.set(event.name().toLowerCase(Locale.ROOT),
                        BehaviourActionCodec.encodeList(instance.getBehaviourOverride(event)));
        }
    }

    private void preserveMalformed(String key, ConfigurationSection section, String reason) {
        preservedMalformed.put(key, new LinkedHashMap<>(section.getValues(true)));
        plugin.getLogger().warning("Preserving malformed NPC instance '" + key + "' in instances.yml: " + reason);
    }

    public void flush() {
        writer.flush();
    }
}
