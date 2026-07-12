package dev.blockfolk.repository;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.CustomEvent;

public final class CustomEventRepository {
    private final File file;
    private final Map<String, CustomEvent> events = new LinkedHashMap<>();

    public CustomEventRepository(JavaPlugin plugin) { this.file = new File(plugin.getDataFolder(), "custom-events.yml"); }

    public void loadAll() {
        events.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("events");
        if (root == null) return;
        for (String storageKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(storageKey);
            if (section == null) continue;
            try {
                CustomEvent event = new CustomEvent(section.getString("name", storageKey));
                event.setDescription(section.getString("description", ""));
                event.setIcon(section.getItemStack("icon"));
                events.put(event.getName(), event);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public Optional<CustomEvent> find(String name) { return Optional.ofNullable(events.get(name)); }
    public Collection<CustomEvent> findAll() {
        return events.values().stream().sorted(Comparator.comparing(CustomEvent::getName)).toList();
    }
    public CustomEvent save(CustomEvent event) { events.put(event.getName(), event); saveAll(); return event; }
    public boolean delete(CustomEvent event) {
        if (events.remove(event.getName()) == null) return false;
        saveAll();
        return true;
    }

    private void saveAll() {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection root = configuration.createSection("events");
        int index = 0;
        for (CustomEvent event : events.values()) {
            ConfigurationSection section = root.createSection(Integer.toString(index++));
            section.set("name", event.getName());
            section.set("description", event.getDescription().isBlank() ? null : event.getDescription());
            section.set("icon", event.getIcon());
        }
        try { configuration.save(file); }
        catch (IOException exception) { throw new IllegalStateException("Could not save custom events.", exception); }
    }
}
