package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;

final class RouteBrowserModel {

    private static final String NPC_PREFIX = "npc:";

    private RouteBrowserModel() {
    }

    static List<Entry> entries(Collection<NpcRoute> routes, Collection<NpcDefinition> definitions, String folder) {
        List<NpcRoute> orderedRoutes = List.copyOf(routes);
        List<NpcDefinition> orderedDefinitions = List.copyOf(definitions);
        if (folder.isEmpty()) {
            List<Entry> result = new ArrayList<>();
            for (NpcDefinition definition : orderedDefinitions) {
                long count = orderedRoutes.stream().filter(route -> route.isOwnedBy(definition.getKey())).count();
                if (count > 0) {
                    result.add(Entry.npcFolder(npcFolder(definition.getKey()), definition.getDisplayName(),
                            (int) count));
                }
            }
            orderedRoutes.stream().filter(route -> route.getOwnerKey() == null || orderedDefinitions.stream()
                    .noneMatch(definition -> route.isOwnedBy(definition.getKey())))
                    .map(Entry::route).forEach(result::add);
            return result;
        }
        if (isNpcFolder(folder)) {
            String npcKey = npcKey(folder);
            return orderedRoutes.stream().filter(route -> route.isOwnedBy(npcKey)).map(Entry::route).toList();
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

    record Entry(boolean folder, boolean npcFolder, String path, String label, int childCount, NpcRoute route) {
        static Entry npcFolder(String path, String label, int childCount) {
            return new Entry(true, true, path, label, childCount, null);
        }

        static Entry route(NpcRoute route) {
            return new Entry(false, false, route.getKey(), route.getKey(), 0, route);
        }
    }
}
