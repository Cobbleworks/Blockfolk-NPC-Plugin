package dev.blockfolk.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.RegionalEvent;
import dev.blockfolk.model.RegionalEvent.Phase;
import dev.blockfolk.model.RegionalEvent.Region;
import dev.blockfolk.model.RegionalEvent.Trigger;

public final class RegionalEventRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final DebouncedYamlWriter writer;
    private final Map<UUID, Region> regions = new LinkedHashMap<>();
    private final Map<UUID, RegionalEvent> events = new LinkedHashMap<>();

    public RegionalEventRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regional-events.yml");
        this.writer = new DebouncedYamlWriter(plugin);
    }

    public void loadAll() {
        regions.clear();
        events.clear();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        for (ConfigurationSection section : children(configuration, "regions")) {
            try {
                UUID id = UUID.fromString(section.getName());
                Region region = new Region(id, section.getString("name", ""), section.getString("world", ""),
                        section.getInt("x1"), section.getInt("z1"), section.getInt("x2"), section.getInt("z2"));
                regions.put(id, region);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipped invalid regional event area " + section.getName() + ".");
            }
        }
        for (ConfigurationSection section : children(configuration, "events")) {
            try {
                UUID id = UUID.fromString(section.getName());
                UUID regionId = UUID.fromString(section.getString("region", ""));
                if (!regions.containsKey(regionId))
                    throw new IllegalArgumentException("Unknown region");
                List<Phase> phases = new ArrayList<>();
                for (ConfigurationSection phase : children(section, "phases")) {
                    Map<UUID, List<BehaviourAction>> actors = new LinkedHashMap<>();
                    ConfigurationSection rows = phase.getConfigurationSection("actors");
                    if (rows != null) {
                        for (String actor : rows.getKeys(false))
                            actors.put(UUID.fromString(actor), BehaviourActionCodec.decodeList(rows.get(actor)));
                    }
                    phases.add(new Phase(UUID.fromString(phase.getName()), actors));
                }
                RegionalEvent event = new RegionalEvent(id, regionId, section.getString("name", ""),
                        Trigger.valueOf(section.getString("trigger", "MANUAL")),
                        DayPeriod.valueOf(section.getString("period", "EVENING")), section.getBoolean("enabled"),
                        phases);
                events.put(id, event);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipped invalid regional event " + section.getName() + ".");
            }
        }
    }

    public Collection<Region> regions() {
        return List.copyOf(regions.values());
    }

    public Collection<RegionalEvent> events() {
        return List.copyOf(events.values());
    }

    public Region region(UUID id) {
        return regions.get(id);
    }

    public RegionalEvent event(UUID id) {
        return events.get(id);
    }

    public String qualifiedName(RegionalEvent event) {
        Region region = regions.get(event.regionId());
        return region == null ? event.name() : region.name() + "/" + event.name();
    }

    public void save(Region region) {
        regions.put(region.id(), region);
        queueSave();
    }

    public void save(RegionalEvent event) {
        events.put(event.id(), event);
        queueSave();
    }

    public void deleteEvent(UUID id) {
        events.remove(id);
        queueSave();
    }

    public void deleteRegion(UUID id) {
        regions.remove(id);
        events.values().removeIf(event -> event.regionId().equals(id));
        queueSave();
    }

    public void flush() {
        writer.flush();
    }

    private void queueSave() {
        writer.queue(file, this::serialize);
    }

    private YamlConfiguration serialize() {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection areas = configuration.createSection("regions");
        for (Region region : regions.values()) {
            areas.createSection(region.id().toString(), Map.of("name", region.name(), "world", region.world(), "x1",
                    region.x1(), "z1", region.z1(), "x2", region.x2(), "z2", region.z2()));
        }
        ConfigurationSection definitions = configuration.createSection("events");
        for (RegionalEvent event : events.values()) {
            ConfigurationSection section = definitions.createSection(event.id().toString());
            section.set("name", event.name());
            section.set("region", event.regionId().toString());
            section.set("trigger", event.trigger().name());
            section.set("period", event.period().name());
            section.set("enabled", event.enabled());
            ConfigurationSection phases = section.createSection("phases");
            for (Phase phase : event.phases()) {
                ConfigurationSection rows = phases.createSection(phase.id().toString()).createSection("actors");
                phase.actors().forEach(
                        (actor, actions) -> rows.set(actor.toString(), BehaviourActionCodec.encodeList(actions)));
            }
        }
        return configuration;
    }

    private static List<ConfigurationSection> children(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null)
            return List.of();
        return section.getKeys(false).stream().filter(section::isConfigurationSection)
                .map(section::getConfigurationSection).toList();
    }
}
