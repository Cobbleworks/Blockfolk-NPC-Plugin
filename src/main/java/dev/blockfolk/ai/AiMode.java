package dev.blockfolk.ai;

import java.util.Locale;

public enum AiMode {
    RESPOND("Respond"),
    REACT("React"),
    DECIDE("Decide");

    private final String displayName;

    AiMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public AiMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static AiMode fromStored(String value) {
        if (value == null || value.isBlank()) return RESPOND;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RESPOND;
        }
    }
}
