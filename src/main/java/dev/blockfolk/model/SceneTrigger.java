package dev.blockfolk.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Something in the world that starts a scene on its own. A scene can use any
 * number of triggers; manual starts (command, Start Scene action) always work.
 */
public enum SceneTrigger {
    NIGHT(Kind.TIME, "Night begins", "At 00:00"), MORNING(Kind.TIME, "Morning begins", "At 05:00"), NOON(Kind.TIME,
            "Noon begins", "At 10:00"), AFTERNOON(Kind.TIME, "Afternoon begins", "At 14:00"), EVENING(Kind.TIME,
                    "Evening begins", "At 19:00"), PRECIPITATION(Kind.WEATHER, "Rain or snow starts",
                            "Any rain or snowfall"), RAIN(Kind.WEATHER, "Rain starts",
                                    "Only where the area is warm enough for rain"), SNOW(Kind.WEATHER, "Snow starts",
                                            "Only where the area is cold enough for snow"), THUNDER(Kind.WEATHER,
                                                    "Thunderstorm starts", "Lightning and thunder"), CLEAR(Kind.WEATHER,
                                                            "Weather clears", "Rain or snow stops"), PLAYER_ENTERS(
                                                                    Kind.AREA, "Player enters the area",
                                                                    "Someone walks into the scene area");

    public enum Kind {
        TIME, WEATHER, AREA
    }

    private final Kind kind;
    private final String displayName;
    private final String summary;

    SceneTrigger(Kind kind, String displayName, String summary) {
        this.kind = kind;
        this.displayName = displayName;
        this.summary = summary;
    }

    public Kind kind() {
        return kind;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }

    /** The day period this trigger waits for, or null for other kinds. */
    public DayPeriod period() {
        return switch (this) {
            case NIGHT -> DayPeriod.NIGHT;
            case MORNING -> DayPeriod.MORNING;
            case NOON -> DayPeriod.NOON;
            case AFTERNOON -> DayPeriod.AFTERNOON;
            case EVENING -> DayPeriod.EVENING;
            default -> null;
        };
    }

    public static SceneTrigger forPeriod(DayPeriod period) {
        return valueOf(period.name());
    }

    public String storedName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<SceneTrigger> fromStored(String value) {
        if (value == null)
            return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
