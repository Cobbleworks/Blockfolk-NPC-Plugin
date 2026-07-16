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

    private RouteBrowserModel() { }

    static List<Entry> entries(
            Collection<NpcRoute> routes,
            Collection<NpcDefinition> definitions,
            String folder
    ) {
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
                    result.add(Entry.npcFolder(npcFolder(definition.getKey()),
                            definition.getDisplayName(), keys.size()));
                }
            }
            result.addAll(routeEntries(orderedRoutes, usedRouteKeys, "", "", true));
            return result;
        }

        if (isNpcFolder(folder)) {
            String npcKey = npcKey(folder);
            Set<String> keys = routeKeysByNpc.get(npcKey);
            if (keys == null) {
                return List.of();
            }
            String routeFolder = routeFolder(folder);
            return routeEntries(orderedRoutes, keys, routeFolder, npcFolder(npcKey), false);
        }

        return routeEntries(orderedRoutes, usedRouteKeys, folder, "", true);
    }

    static String parent(String folder) {
        if (isNpcFolder(folder)) {
            String npcRoot = npcFolder(npcKey(folder));
            if (folder.equals(npcRoot)) {
                return "";
            }
            int slash = folder.lastIndexOf('/');
            return slash < npcRoot.length() ? npcRoot : folder.substring(0, slash);
        }
        int slash = folder.lastIndexOf('/');
        return slash < 0 ? "" : folder.substring(0, slash);
    }

    static boolean isNpcFolder(String folder) {
        return folder.startsWith(NPC_PREFIX);
    }

    static String npcKey(String folder) {
        String rest = folder.substring(NPC_PREFIX.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    static String routeFolder(String folder) {
        String npcRoot = npcFolder(npcKey(folder));
        return folder.length() == npcRoot.length() ? "" : folder.substring(npcRoot.length() + 1);
    }

    private static String npcFolder(String npcKey) {
        return NPC_PREFIX + npcKey;
    }

    private static List<Entry> routeEntries(
            List<NpcRoute> routes,
            Set<String> selectedRouteKeys,
            String routeFolder,
            String virtualPrefix,
            boolean invertSelection
    ) {
        String prefix = routeFolder.isEmpty() ? "" : routeFolder + "/";
        Map<String, Entry> result = new LinkedHashMap<>();
        for (NpcRoute route : routes) {
            boolean selected = selectedRouteKeys.contains(route.getKey());
            if (selected == invertSelection || !route.getKey().startsWith(prefix)) {
                continue;
            }
            String rest = route.getKey().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String label = rest.substring(0, slash);
                String childRoutePath = prefix + label;
                String path = virtualPrefix.isEmpty()
                        ? childRoutePath
                        : virtualPrefix + "/" + childRoutePath;
                Entry old = result.get(path);
                result.put(path, Entry.routeFolder(path, label, old == null ? 1 : old.childCount() + 1));
            } else {
                result.put(route.getKey(), Entry.route(route));
            }
        }
        return new ArrayList<>(result.values());
    }

    record Entry(boolean folder, boolean npcFolder, String path, String label, int childCount, NpcRoute route) {
        static Entry npcFolder(String path, String label, int childCount) {
            return new Entry(true, true, path, label, childCount, null);
        }

        static Entry routeFolder(String path, String label, int childCount) {
            return new Entry(true, false, path, label, childCount, null);
        }

        static Entry route(NpcRoute route) {
            return new Entry(false, false, route.getKey(), route.getKey(), 0, route);
        }
    }
}
