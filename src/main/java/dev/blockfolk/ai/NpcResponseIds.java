package dev.blockfolk.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Readable, unique aliases for NPCs within one AI request. */
final class NpcResponseIds {

    private static final int MAX_NAME_LENGTH = 40;

    private NpcResponseIds() {
    }

    static List<String> forNames(List<String> names) {
        List<String> bases = names.stream().map(name -> "npc_" + slug(name)).toList();
        Map<String, Integer> counts = new HashMap<>();
        bases.forEach(base -> counts.merge(base, 1, Integer::sum));
        List<String> aliases = new ArrayList<>(java.util.Collections.nCopies(bases.size(), null));
        Set<String> used = new HashSet<>();
        for (int index = 0; index < bases.size(); index++) {
            String base = bases.get(index);
            if (counts.get(base) == 1) {
                aliases.set(index, base);
                used.add(base);
            }
        }
        Map<String, Integer> nextSuffix = new HashMap<>();
        for (int index = 0; index < bases.size(); index++) {
            String base = bases.get(index);
            if (counts.get(base) == 1)
                continue;
            int suffix = nextSuffix.getOrDefault(base, 1);
            String candidate;
            do {
                candidate = base + "_" + String.format(Locale.ROOT, "%02d", suffix++);
            } while (used.contains(candidate));
            aliases.set(index, candidate);
            used.add(candidate);
            nextSuffix.put(base, suffix);
        }
        return List.copyOf(aliases);
    }

    private static String slug(String name) {
        String normalized = Normalizer.normalize(plainName(name), Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.length() > MAX_NAME_LENGTH)
            normalized = normalized.substring(0, MAX_NAME_LENGTH).replaceAll("_+$", "");
        return normalized.isEmpty() ? "unnamed" : normalized;
    }

    static String plainName(String name) {
        return name == null ? "" : name.replaceAll("(?i)§[0-9a-fk-or]", "");
    }
}
