package dev.blockfolk.ai;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/** Readable response IDs tied to a spawned NPC's persistent identity. */
final class NpcResponseIds {

    private static final int MAX_NAME_LENGTH = 40;
    private static final int INSTANCE_SUFFIX_LENGTH = 16;

    private NpcResponseIds() {
    }

    static String forInstance(String name, UUID instanceId) {
        String suffix = instanceId.toString().replace("-", "").substring(0, INSTANCE_SUFFIX_LENGTH);
        return "npc_" + slug(name) + "_" + suffix;
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
