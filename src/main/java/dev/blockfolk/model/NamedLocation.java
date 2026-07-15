package dev.blockfolk.model;

import java.util.Objects;

/** A globally named position that can be selected by behaviour actions. */
public record NamedLocation(String key, String displayName, ActionLocation location) {

    public NamedLocation {
        key = normalizeKey(key);
        displayName = Objects.requireNonNull(displayName, "location name").trim();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Location name is required.");
        }
        Objects.requireNonNull(location, "location");
    }

    public static NamedLocation create(String displayName, ActionLocation location) {
        return new NamedLocation(normalizeKey(displayName), displayName, location);
    }

    public static String normalizeKey(String value) {
        String name = Objects.requireNonNull(value, "location name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Location name is required.");
        }
        return NpcDefinition.toKey(name);
    }
}
