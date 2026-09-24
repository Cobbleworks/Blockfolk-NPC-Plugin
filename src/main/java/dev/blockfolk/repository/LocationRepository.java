package dev.blockfolk.repository;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.ActionLocation;
import dev.blockfolk.model.NamedLocation;

/** Persists the global locations available to NPC behaviour editors. */
public final class LocationRepository {

    private final File file;
    private final DebouncedYamlWriter writer;
    private final Map<String, NamedLocation> locations = new LinkedHashMap<>();
    private final List<String> locationOrder = new java.util.ArrayList<>();

    public LocationRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "locations.yml");
        this.writer = new DebouncedYamlWriter(plugin);
    }

    public void loadAll() {
        locations.clear();
        locationOrder.clear();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("locations");
        if (root == null) {
            loadOrder(configuration);
            return;
        }
        for (String storedKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(storedKey);
            if (section == null)
                continue;
            try {
                ActionLocation location = new ActionLocation(section.getString("world"), section.getDouble("x"),
                        section.getDouble("y"), section.getDouble("z"));
                String displayName = section.getString("display-name", storedKey);
                String key = section.contains("key")
                        ? section.getString("key")
                        : NamedLocation.normalizeKey(displayName);
                NamedLocation named = new NamedLocation(key, displayName, location, section.getItemStack("icon"));
                locations.put(named.key(), named);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed saved locations without preventing plugin startup.
            }
        }
        loadOrder(configuration);
    }

    public Optional<NamedLocation> find(String keyOrName) {
        try {
            return Optional.ofNullable(locations.get(NamedLocation.normalizeKey(keyOrName)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Collection<NamedLocation> findAll() {
        return locationOrder.stream().map(locations::get).filter(java.util.Objects::nonNull).toList();
    }

    public NamedLocation save(NamedLocation location) {
        if (locations.put(location.key(), location) == null)
            locationOrder.add(location.key());
        saveAll();
        return location;
    }

    public NamedLocation replace(NamedLocation previous, NamedLocation replacement) {
        int index = locationOrder.indexOf(previous.key());
        if (index < 0 || !previous.equals(locations.get(previous.key()))) {
            throw new IllegalArgumentException("That location has changed. Select it again to replace it.");
        }
        if (!previous.key().equals(replacement.key()) && locations.containsKey(replacement.key())) {
            throw new IllegalArgumentException("A location with that key already exists.");
        }
        locations.remove(previous.key());
        locations.put(replacement.key(), replacement);
        locationOrder.set(index, replacement.key());
        saveAll();
        return replacement;
    }

    public void reorder(List<String> orderedKeys) {
        List<String> normalized = orderedKeys.stream().map(NamedLocation::normalizeKey).toList();
        if (normalized.size() != locations.size() || new HashSet<>(normalized).size() != normalized.size()
                || !locations.keySet().containsAll(normalized)) {
            throw new IllegalArgumentException("The location order must contain every location exactly once.");
        }
        locationOrder.clear();
        locationOrder.addAll(normalized);
        saveAll();
    }

    public boolean delete(NamedLocation location) {
        if (locations.remove(location.key()) == null)
            return false;
        locationOrder.remove(location.key());
        saveAll();
        return true;
    }

    private void loadOrder(YamlConfiguration configuration) {
        Set<String> seen = new HashSet<>();
        for (String storedKey : configuration.getStringList("order")) {
            try {
                String key = NamedLocation.normalizeKey(storedKey);
                if (locations.containsKey(key) && seen.add(key))
                    locationOrder.add(key);
            } catch (IllegalArgumentException ignored) {
            }
        }
        locations.keySet().stream().filter(seen::add).sorted(Comparator.naturalOrder()).forEach(locationOrder::add);
    }

    private void saveAll() {
        writer.queue(file, this::serialize);
    }

    private YamlConfiguration serialize() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("order", locationOrder);
        ConfigurationSection root = configuration.createSection("locations");
        int index = 0;
        for (NamedLocation named : findAll()) {
            ConfigurationSection section = root.createSection(Integer.toString(index++));
            section.set("key", named.key());
            section.set("display-name", named.displayName());
            section.set("world", named.location().worldName());
            section.set("x", named.location().x());
            section.set("y", named.location().y());
            section.set("z", named.location().z());
            section.set("icon", named.icon());
        }
        return configuration;
    }

    public void flush() {
        writer.flush();
    }
}
