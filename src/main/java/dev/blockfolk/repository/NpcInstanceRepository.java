package dev.blockfolk.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.util.LocationCodec;

public final class NpcInstanceRepository {

    private final File file;

    public NpcInstanceRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "instances.yml");
    }

    public List<NpcInstance> loadAll() {
        List<NpcInstance> instances = new ArrayList<>();
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
                continue;
            }
            if (LocationCodec.read(section.getConfigurationSection("location")) == null) {
                continue;
            }
            instances.add(new NpcInstance(
                    UUID.fromString(key),
                    definitionKey,
                    LocationCodec.read(section.getConfigurationSection("location"))
            ));
        }
        return instances;
    }

    public void saveAll(Collection<NpcInstance> instances) {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection root = configuration.createSection("instances");
        for (NpcInstance instance : instances) {
            ConfigurationSection section = root.createSection(instance.getId().toString());
            section.set("definition", instance.getDefinitionKey());
            LocationCodec.write(section.createSection("location"), instance.getLocation());
        }
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save NPC instances.", exception);
        }
    }
}
