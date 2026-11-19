package dev.blockfolk.model;

import java.util.Locale;

/**
 * When a running scene ends and its roles start their wrap-up actions.
 */
public enum SceneEnding {
    BEATS_FINISHED("After the last beat", "Ends once the last beat is done"), DURATION("After a set time",
            "Ends after the chosen number of minutes"), TIME_OF_DAY("At a time of day",
                    "Ends when the chosen part of the day begins"), WEATHER_CHANGES("When the weather changes",
                            "Ends when rain, snow, or thunder starts or stops");

    private final String displayName;
    private final String summary;

    SceneEnding(String displayName, String summary) {
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

    public static SceneEnding fromStored(String value) {
        if (value == null)
            return BEATS_FINISHED;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        // Before beats, this ending waited for every role's single action row.
        if (normalized.equals("LANES_FINISHED"))
            return BEATS_FINISHED;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return BEATS_FINISHED;
        }
    }
}
