package dev.blockfolk.model;

import java.util.Locale;

public enum AttackReaction {
    IGNORE("Ignore"), FIGHT_BACK("Fights Back"), FLEE("Flee"), HUNTING("Hunting");

    private final String displayName;

    AttackReaction(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public AttackReaction next() {
        AttackReaction[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static AttackReaction fromStored(String value) {
        if (value == null || value.isBlank()) {
            return IGNORE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return IGNORE;
        }
    }
}
