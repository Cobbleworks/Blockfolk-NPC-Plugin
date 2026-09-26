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

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.RoutePoint;

public final class RouteRepository {

    private final File file;
    private final DebouncedYamlWriter writer;
    private final Map<String, NpcRoute> routes = new LinkedHashMap<>();
    private final List<String> routeOrder = new java.util.ArrayList<>();

    public RouteRepository(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "routes.yml");
        this.writer = new DebouncedYamlWriter(plugin);
    }

    public void loadAll() {
        routes.clear();
        routeOrder.clear();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("routes");
        if (root == null) {
            loadOrder(configuration);
            return;
        }
        for (String storedKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(storedKey);
            if (section == null) {
                continue;
            }
            NpcRoute route;
            try {
                route = new NpcRoute(storedKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            route.setDisplayName(section.getString("display-name", route.getKey()));
            if (section.isString("owner")) {
                route.setOwnerKey(section.getString("owner"));
            }
            route.setIcon(section.getItemStack("icon"));
            ConfigurationSection points = section.getConfigurationSection("points");
            if (points != null) {
                points.getKeys(false).stream().sorted(RouteRepository::comparePointKeys).forEach(index -> {
                    ConfigurationSection point = points.getConfigurationSection(index);
                    if (point == null || point.getString("world") == null) {
                        return;
                    }
                    try {
                        List<BehaviourAction> actions = loadActions(point);
                        route.addPoint(new RoutePoint(point.getString("world"), point.getInt("x"), point.getInt("y"),
                                point.getInt("z"), actions));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed cross-world or duplicate points.
                    }
                });
            }
            routes.put(route.getKey(), route);
        }
        loadOrder(configuration);
    }

    /** Assigns legacy routes to their users, copying routes shared by several NPCs. */
    public void migrateOwnership(Collection<NpcDefinition> definitions,
            java.util.function.Consumer<NpcDefinition> saveDefinition) {
        Map<String, Set<String>> reachable = new LinkedHashMap<>();
        for (NpcDefinition definition : definitions) {
            Set<String> keys = new HashSet<>(definition.getReferencedRouteKeys());
            java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>(keys);
            while (!pending.isEmpty()) {
                find(pending.removeFirst()).ifPresent(route -> {
                    for (String key : route.getReferencedRouteKeys()) {
                        if (keys.add(key))
                            pending.addLast(key);
                    }
                });
            }
            reachable.put(definition.getKey(), keys);
        }
        Map<String, Map<String, String>> replacements = new LinkedHashMap<>();
        for (NpcRoute route : List.copyOf(findAll())) {
            if (route.getOwnerKey() != null)
                continue;
            List<NpcDefinition> users = definitions.stream()
                    .filter(definition -> reachable.get(definition.getKey()).contains(route.getKey())).toList();
            if (users.isEmpty())
                continue;
            route.setOwnerKey(users.getFirst().getKey());
            replacements.computeIfAbsent(users.getFirst().getKey(), ignored -> new LinkedHashMap<>())
                    .put(route.getKey(), route.getKey());
            save(route);
            for (NpcDefinition definition : users.subList(1, users.size())) {
                String base = route.getKey() + "-" + definition.getKey();
                String key = base;
                int suffix = 2;
                while (find(key).isPresent())
                    key = base + "-" + suffix++;
                NpcRoute copy = new NpcRoute(key);
                copy.setDisplayName(route.getDisplayName());
                copy.setIcon(route.getIcon());
                copy.setOwnerKey(definition.getKey());
                route.getPoints().forEach(copy::addPoint);
                save(copy);
                replacements.computeIfAbsent(definition.getKey(), ignored -> new LinkedHashMap<>())
                        .put(route.getKey(), copy.getKey());
                definition.replaceRouteReferences(route.getKey(), copy.getKey());
                saveDefinition.accept(definition);
            }
        }
        for (NpcRoute route : findAll()) {
            Map<String, String> mapping = replacements.get(route.getOwnerKey());
            if (mapping == null)
                continue;
            mapping.forEach((oldKey, newKey) -> {
                if (!oldKey.equals(newKey))
                    route.replaceRouteReferences(oldKey, newKey);
            });
            save(route);
        }
    }

    public void copyOwnedRoutes(NpcDefinition source, NpcDefinition target) {
        Map<String, String> replacements = new LinkedHashMap<>();
        for (NpcRoute route : List.copyOf(findAll())) {
            if (!route.isOwnedBy(source.getKey()))
                continue;
            String base = route.getKey() + "-" + target.getKey();
            String key = base;
            int suffix = 2;
            while (find(key).isPresent())
                key = base + "-" + suffix++;
            NpcRoute copy = new NpcRoute(key);
            copy.setDisplayName(route.getDisplayName());
            copy.setIcon(route.getIcon());
            copy.setOwnerKey(target.getKey());
            route.getPoints().forEach(copy::addPoint);
            save(copy);
            replacements.put(route.getKey(), copy.getKey());
            target.replaceRouteReferences(route.getKey(), copy.getKey());
        }
        for (NpcRoute route : findAll()) {
            if (!route.isOwnedBy(target.getKey()))
                continue;
            replacements.forEach(route::replaceRouteReferences);
            save(route);
        }
    }

    public Optional<NpcRoute> find(String keyOrName) {
        try {
            return Optional.ofNullable(routes.get(NpcRoute.normalizeKey(keyOrName)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Collection<NpcRoute> findAll() {
        return routeOrder.stream().map(routes::get).filter(java.util.Objects::nonNull).toList();
    }

    public NpcRoute save(NpcRoute route) {
        if (routes.put(route.getKey(), route) == null) {
            routeOrder.add(route.getKey());
        }
        saveAll();
        return route;
    }

    public void reorder(List<String> orderedKeys) {
        List<String> normalized = orderedKeys.stream().map(NpcRoute::normalizeKey).toList();
        if (normalized.size() != routes.size() || new HashSet<>(normalized).size() != normalized.size()
                || !routes.keySet().containsAll(normalized)) {
            throw new IllegalArgumentException("The route order must contain every route exactly once.");
        }
        routeOrder.clear();
        routeOrder.addAll(normalized);
        saveAll();
    }

    public boolean delete(NpcRoute route) {
        if (routes.remove(route.getKey()) == null) {
            return false;
        }
        routeOrder.remove(route.getKey());
        saveAll();
        return true;
    }

    private void loadOrder(YamlConfiguration configuration) {
        Set<String> seen = new HashSet<>();
        for (String storedKey : configuration.getStringList("order")) {
            try {
                String key = NpcRoute.normalizeKey(storedKey);
                if (routes.containsKey(key) && seen.add(key)) {
                    routeOrder.add(key);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        routes.keySet().stream().filter(seen::add).sorted(Comparator.naturalOrder()).forEach(routeOrder::add);
    }

    private void saveAll() {
        writer.queue(file, this::serialize);
    }

    private YamlConfiguration serialize() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("order", routeOrder);
        ConfigurationSection root = configuration.createSection("routes");
        for (NpcRoute route : findAll()) {
            ConfigurationSection section = root.createSection(route.getKey());
            section.set("display-name", route.getDisplayName());
            section.set("owner", route.getOwnerKey());
            section.set("icon", route.getIcon());
            ConfigurationSection points = section.createSection("points");
            for (int index = 0; index < route.getPoints().size(); index++) {
                RoutePoint routePoint = route.getPoints().get(index);
                ConfigurationSection point = points.createSection(String.valueOf(index));
                point.set("world", routePoint.worldName());
                point.set("x", routePoint.x());
                point.set("y", routePoint.y());
                point.set("z", routePoint.z());
                if (!routePoint.actions().isEmpty()) {
                    point.set("actions", BehaviourActionCodec.encodeList(routePoint.actions()));
                }
            }
        }
        return configuration;
    }

    public void flush() {
        writer.flush();
    }

    private List<BehaviourAction> loadActions(ConfigurationSection point) {
        List<BehaviourAction> actions = new java.util.ArrayList<>();
        for (Map<?, ?> stored : point.getMapList("actions")) {
            try {
                actions.add(BehaviourActionCodec.decode(stored));
            } catch (IllegalArgumentException ignored) {
                /* Ignore malformed waypoint actions. */ }
        }
        return actions;
    }

    private static int comparePointKeys(String first, String second) {
        try {
            return Integer.compare(Integer.parseInt(first), Integer.parseInt(second));
        } catch (NumberFormatException ignored) {
            return first.compareTo(second);
        }
    }
}
