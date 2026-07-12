package dev.blockfolk.model;

import java.util.Locale;

public enum AggressionLevel {
    NONE("None"),
    FLEE("Flee"),
    FIGHT_BACK("Fight Back"),
    FIGHTS_ON_SIGHT("Start Fights on Sight");

    private final String displayName;

    AggressionLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public AggressionLevel next() {
        AggressionLevel[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static AggressionLevel fromStored(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.equals("START_FIGHTS_ON_SIGHT")) {
            return FIGHTS_ON_SIGHT;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
