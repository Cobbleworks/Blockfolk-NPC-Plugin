package dev.blockfolk.repository;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.ActionLocation;
import dev.blockfolk.model.NamedLocation;

/** Persists the global locations available to NPC behaviour editors. */
public final class LocationRepository {

    private final File file;
    private final Map<String, NamedLocation> locations = new LinkedHashMap<>();

    public LocationRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "locations.yml");
    }

    public void loadAll() {
        locations.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file)
                .getConfigurationSection("locations");
        if (root == null) return;
        for (String storedKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(storedKey);
            if (section == null) continue;
            try {
                ActionLocation location = new ActionLocation(
                        section.getString("world"),
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"));
                NamedLocation named = new NamedLocation(storedKey,
                        section.getString("display-name", storedKey), location);
                locations.put(named.key(), named);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed saved locations without preventing plugin startup.
            }
        }
    }

    public Optional<NamedLocation> find(String keyOrName) {
        try {
            return Optional.ofNullable(locations.get(NamedLocation.normalizeKey(keyOrName)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Collection<NamedLocation> findAll() {
        return java.util.List.copyOf(locations.values());
    }

    public NamedLocation save(NamedLocation location) {
        locations.put(location.key(), location);
        saveAll();
        return location;
    }

    public boolean delete(NamedLocation location) {
        if (locations.remove(location.key()) == null) return false;
        saveAll();
        return true;
    }

    private void saveAll() {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection root = configuration.createSection("locations");
        for (NamedLocation named : locations.values()) {
            ConfigurationSection section = root.createSection(named.key());
            section.set("display-name", named.displayName());
            section.set("world", named.location().worldName());
            section.set("x", named.location().x());
            section.set("y", named.location().y());
            section.set("z", named.location().z());
        }
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save global locations.", exception);
        }
    }
}
