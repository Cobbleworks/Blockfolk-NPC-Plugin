package dev.blockfolk.model;

import java.util.Locale;

/**
 * When a running scene ends and its roles start their wrap-up actions.
 */
public enum SceneEnding {
    LANES_FINISHED("When everyone is done", "Ends once every role finished its scene actions"), DURATION(
            "After a set time", "Ends after the chosen number of minutes"), TIME_OF_DAY("At a time of day",
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
            return LANES_FINISHED;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return LANES_FINISHED;
        }
    }
}
