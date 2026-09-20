package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.blockfolk.model.NamedLocation;

final class LocationBrowserModel {

    private LocationBrowserModel() {
    }

    static List<Entry> entries(Collection<NamedLocation> locations, String folder) {
        String prefix = folder.isEmpty() ? "" : folder + "/";
        Map<String, Entry> result = new LinkedHashMap<>();
        for (NamedLocation location : locations) {
            if (!location.key().startsWith(prefix))
                continue;
            String rest = location.key().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String keyPart = rest.substring(0, slash);
                String path = prefix + keyPart;
                String entryKey = "folder:" + path;
                Entry previous = result.get(entryKey);
                String label = previous == null ? displayGroup(location, folderDepth(folder)) : previous.label();
                result.put(entryKey,
                        new Entry(true, path, label, previous == null ? 1 : previous.childCount() + 1, null));
            } else {
                result.put("location:" + location.key(),
                        new Entry(false, location.key(), displayLeaf(location), 0, location));
            }
        }
        return new ArrayList<>(result.values());
    }

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static int folderDepth(String folder) {
        return folder.isEmpty() ? 0 : folder.split("/").length;
    }

    private static String displayGroup(NamedLocation location, int depth) {
        String[] parts = location.displayName().split("/", -1);
        if (depth < parts.length - 1 && !parts[depth].isBlank())
            return parts[depth].trim();
        String[] keys = location.key().split("/");
        return keys[Math.min(depth, keys.length - 1)];
    }

    private static String displayLeaf(NamedLocation location) {
        int slash = location.displayName().lastIndexOf('/');
        return slash < 0 ? location.displayName() : location.displayName().substring(slash + 1).trim();
    }

    record Entry(boolean folder, String path, String label, int childCount, NamedLocation location) {
    }
}
