package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;

final class RouteBrowserModel {

    private static final String NPC_PREFIX = "npc:";

    private RouteBrowserModel() {
    }

    static List<Entry> entries(Collection<NpcRoute> routes, Collection<NpcDefinition> definitions, String folder) {
        List<NpcRoute> orderedRoutes = List.copyOf(routes);
        List<NpcDefinition> orderedDefinitions = List.copyOf(definitions);
        Map<String, NpcRoute> routesByKey = new LinkedHashMap<>();
        orderedRoutes.forEach(route -> routesByKey.put(route.getKey(), route));

        Map<String, Set<String>> routeKeysByNpc = new LinkedHashMap<>();
        Set<String> usedRouteKeys = new LinkedHashSet<>();
        for (NpcDefinition definition : orderedDefinitions) {
            Set<String> existingKeys = new LinkedHashSet<>(definition.getReferencedRouteKeys());
            existingKeys.retainAll(routesByKey.keySet());
            if (!existingKeys.isEmpty()) {
                routeKeysByNpc.put(definition.getKey(), existingKeys);
                usedRouteKeys.addAll(existingKeys);
            }
        }

        if (folder.isEmpty()) {
            List<Entry> result = new ArrayList<>();
            for (NpcDefinition definition : orderedDefinitions) {
                Set<String> keys = routeKeysByNpc.get(definition.getKey());
                if (keys != null) {
                    result.add(
                            Entry.npcFolder(npcFolder(definition.getKey()), definition.getDisplayName(), keys.size()));
                }
            }
            result.addAll(routeEntries(orderedRoutes, usedRouteKeys, true));
            return result;
        }

        if (isNpcFolder(folder)) {
            String npcKey = npcKey(folder);
            Set<String> keys = routeKeysByNpc.get(npcKey);
            if (keys == null) {
                return List.of();
            }
            return routeEntries(orderedRoutes, keys, false);
        }

        return List.of();
    }

    static boolean isNpcFolder(String folder) {
        return folder.startsWith(NPC_PREFIX);
    }

    static String npcKey(String folder) {
        return folder.substring(NPC_PREFIX.length());
    }

    private static String npcFolder(String npcKey) {
        return NPC_PREFIX + npcKey;
    }

    private static List<Entry> routeEntries(List<NpcRoute> routes, Set<String> selectedRouteKeys,
            boolean invertSelection) {
        List<Entry> result = new ArrayList<>();
        for (NpcRoute route : routes) {
            boolean selected = selectedRouteKeys.contains(route.getKey());
            if (selected == invertSelection) {
                continue;
            }
            result.add(Entry.route(route));
        }
        return result;
    }

    record Entry(boolean folder, boolean npcFolder, String path, String label, int childCount, NpcRoute route) {
        static Entry npcFolder(String path, String label, int childCount) {
            return new Entry(true, true, path, label, childCount, null);
        }

        static Entry route(NpcRoute route) {
            return new Entry(false, false, route.getKey(), route.getKey(), 0, route);
        }
    }
}
