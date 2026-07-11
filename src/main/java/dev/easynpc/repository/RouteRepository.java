package dev.easynpc.repository;

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

import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcRoute;
import dev.easynpc.model.RoutePoint;

public final class RouteRepository {

    private final File file;
    private final Map<String, NpcRoute> routes = new LinkedHashMap<>();

    public RouteRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "routes.yml");
    }

    public void loadAll() {
        routes.clear();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("routes");
        if (root == null) {
            return;
        }
        for (String storedKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(storedKey);
            if (section == null) {
                continue;
            }
            NpcRoute route = new NpcRoute(NpcDefinition.toKey(storedKey));
            route.setDisplayName(section.getString("display-name", route.getKey()));
            route.setIcon(section.getItemStack("icon"));
            ConfigurationSection points = section.getConfigurationSection("points");
            if (points != null) {
                points.getKeys(false).stream().sorted(RouteRepository::comparePointKeys).forEach(index -> {
                    ConfigurationSection point = points.getConfigurationSection(index);
                    if (point == null || point.getString("world") == null) {
                        return;
                    }
                    try {
                        route.addPoint(new RoutePoint(
                                point.getString("world"),
                                point.getInt("x"),
                                point.getInt("y"),
                                point.getInt("z")
                        ));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed cross-world or duplicate legacy points.
                    }
                });
            }
            routes.put(route.getKey(), route);
        }
    }

    public Optional<NpcRoute> find(String keyOrName) {
        return Optional.ofNullable(routes.get(NpcDefinition.toKey(keyOrName)));
    }

    public Collection<NpcRoute> findAll() {
        return routes.values().stream().sorted(Comparator.comparing(NpcRoute::getKey)).toList();
    }

    public NpcRoute save(NpcRoute route) {
        routes.put(route.getKey(), route);
        saveAll();
        return route;
    }

    public boolean delete(NpcRoute route) {
        if (routes.remove(route.getKey()) == null) {
            return false;
        }
        saveAll();
        return true;
    }

    private void saveAll() {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection root = configuration.createSection("routes");
        for (NpcRoute route : routes.values()) {
            ConfigurationSection section = root.createSection(route.getKey());
            section.set("display-name", route.getDisplayName());
            section.set("icon", route.getIcon());
            ConfigurationSection points = section.createSection("points");
            for (int index = 0; index < route.getPoints().size(); index++) {
                RoutePoint routePoint = route.getPoints().get(index);
                ConfigurationSection point = points.createSection(String.valueOf(index));
                point.set("world", routePoint.worldName());
                point.set("x", routePoint.x());
                point.set("y", routePoint.y());
                point.set("z", routePoint.z());
            }
        }
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save NPC routes.", exception);
        }
    }

    private static int comparePointKeys(String first, String second) {
        try {
            return Integer.compare(Integer.parseInt(first), Integer.parseInt(second));
        } catch (NumberFormatException ignored) {
            return first.compareTo(second);
        }
    }
}
