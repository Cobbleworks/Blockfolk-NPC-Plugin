package dev.blockfolk.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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
                instances.add(new NpcInstance(UUID.fromString(key), definitionKey, location,
                        spawnLocation == null ? location : spawnLocation, section.getLong("respawn-at", 0L)));
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
        }
        return configuration;
    }

    private void preserveMalformed(String key, ConfigurationSection section, String reason) {
        preservedMalformed.put(key, new LinkedHashMap<>(section.getValues(true)));
        plugin.getLogger().warning("Preserving malformed NPC instance '" + key + "' in instances.yml: " + reason);
    }

    public void flush() {
        writer.flush();
    }
}
