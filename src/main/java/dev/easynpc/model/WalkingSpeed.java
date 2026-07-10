package dev.easynpc.model;

import java.util.Locale;

public enum WalkingSpeed {
    SLOUCH("Slouch", 1.0),
    SLOW("Slow", 2.0),
    NORMAL("Normal", 4.317),
    FAST("Fast", 6.0),
    VERY_FAST("Very Fast", 8.0);

    private final String displayName;
    private final double blocksPerSecond;

    WalkingSpeed(String displayName, double blocksPerSecond) {
        this.displayName = displayName;
        this.blocksPerSecond = blocksPerSecond;
    }

    public String displayName() {
        return displayName;
    }

    public double blocksPerSecond() {
        return blocksPerSecond;
    }

    public WalkingSpeed next() {
        WalkingSpeed[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static WalkingSpeed fromStored(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return NORMAL;
        }
    }
}
