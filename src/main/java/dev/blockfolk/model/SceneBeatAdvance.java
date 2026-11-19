package dev.blockfolk.model;

import java.util.Locale;

/**
 * When a scene moves on from one beat to the next. Actions still running in the
 * beat are cut off when it moves on.
 */
public enum SceneBeatAdvance {
    EVERYONE_DONE("When everyone is done", "Every role finished its actions in this beat"), AFTER_TIME(
            "After a set time", "The chosen time passed since the beat started"), ROLE_DONE("When one role is done",
                    "The chosen role finished its actions in this beat"), NEXT_BEAT_ONLY("Only by Next Beat",
                            "A Next Beat action moves on, e.g. from a While Active reaction");

    private final String displayName;
    private final String summary;

    SceneBeatAdvance(String displayName, String summary) {
        this.displayName = displayName;
        this.summary = summary;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }

    public String storedName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static SceneBeatAdvance fromStored(String value) {
        if (value == null)
            return EVERYONE_DONE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return EVERYONE_DONE;
        }
    }
}
