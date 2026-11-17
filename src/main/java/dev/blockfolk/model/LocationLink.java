package dev.blockfolk.model;

import java.util.Optional;

/**
 * A behaviour action value that points at a global {@link NamedLocation} by key
 * instead of storing fixed coordinates, so moving the location moves the target.
 */
public final class LocationLink {

    public static final String PREFIX = "location:";

    private LocationLink() {
    }

    public static String serialize(String locationKey) {
        return PREFIX + locationKey;
    }

    /** Returns the linked location key, or empty when the value is not a link. */
    public static Optional<String> key(String value) {
        if (value == null || !value.startsWith(PREFIX) || value.length() == PREFIX.length())
            return Optional.empty();
        return Optional.of(value.substring(PREFIX.length()));
    }
}
