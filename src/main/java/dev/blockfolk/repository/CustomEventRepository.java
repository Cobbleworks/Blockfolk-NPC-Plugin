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

import dev.blockfolk.model.CustomEvent;

public final class CustomEventRepository {
    private final File file;
    private final DebouncedYamlWriter writer;
    private final Map<String, CustomEvent> events = new LinkedHashMap<>();
    private final List<String> eventOrder = new java.util.ArrayList<>();

    public CustomEventRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "custom-events.yml");
        this.writer = new DebouncedYamlWriter(plugin);
    }

    public void loadAll() {
        events.clear();
        eventOrder.clear();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("events");
        if (root == null) {
            loadOrder(configuration);
            return;
        }
        for (String storageKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(storageKey);
            if (section == null)
                continue;
            try {
                CustomEvent event = new CustomEvent(section.getString("name", storageKey));
                event.setDescription(section.getString("description", ""));
                event.setIcon(section.getItemStack("icon"));
                events.put(event.getName(), event);
            } catch (IllegalArgumentException ignored) {
            }
        }
        loadOrder(configuration);
    }

    public Optional<CustomEvent> find(String name) {
        return Optional.ofNullable(events.get(name));
    }
    public Collection<CustomEvent> findAll() {
        return eventOrder.stream().map(events::get).filter(java.util.Objects::nonNull).toList();
    }
    public CustomEvent save(CustomEvent event) {
        if (events.put(event.getName(), event) == null)
            eventOrder.add(event.getName());
        saveAll();
        return event;
    }
    public void reorder(List<String> orderedNames) {
        if (orderedNames.size() != events.size() || new HashSet<>(orderedNames).size() != orderedNames.size()
                || !events.keySet().containsAll(orderedNames)) {
            throw new IllegalArgumentException("The event order must contain every event exactly once.");
        }
        eventOrder.clear();
        eventOrder.addAll(orderedNames);
        saveAll();
    }
    public boolean delete(CustomEvent event) {
        if (events.remove(event.getName()) == null)
            return false;
        eventOrder.remove(event.getName());
        saveAll();
        return true;
    }

    private void loadOrder(YamlConfiguration configuration) {
        Set<String> seen = new HashSet<>();
        for (String name : configuration.getStringList("order")) {
            if (events.containsKey(name) && seen.add(name))
                eventOrder.add(name);
        }
        events.keySet().stream().filter(seen::add).sorted(Comparator.naturalOrder()).forEach(eventOrder::add);
    }

    private void saveAll() {
        writer.queue(file, this::serialize);
    }

    private YamlConfiguration serialize() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("order", eventOrder);
        ConfigurationSection root = configuration.createSection("events");
        int index = 0;
        for (CustomEvent event : findAll()) {
            ConfigurationSection section = root.createSection(Integer.toString(index++));
            section.set("name", event.getName());
            section.set("description", event.getDescription().isBlank() ? null : event.getDescription());
            section.set("icon", event.getIcon());
        }
        return configuration;
    }

    public void flush() {
        writer.flush();
    }
}
