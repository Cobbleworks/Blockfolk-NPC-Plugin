package dev.blockfolk.model;

import java.util.Locale;

/**
 * The five parts of a Minecraft day. World time 0 is 06:00 on the in-game clock
 * and one in-game hour is 1000 ticks.
 */
public enum DayPeriod {
    NIGHT(0, BehaviourEvent.NIGHT), MORNING(5, BehaviourEvent.MORNING), NOON(10, BehaviourEvent.NOON), AFTERNOON(14,
            BehaviourEvent.AFTERNOON), EVENING(19, BehaviourEvent.EVENING);

    public static final long TICKS_PER_DAY = 24000L;
    private static final long TICKS_PER_HOUR = 1000L;
    /** World time 0 is 06:00. */
    private static final long CLOCK_OFFSET_TICKS = 6000L;

    private final int startHour;
    private final BehaviourEvent event;

    DayPeriod(int startHour, BehaviourEvent event) {
        this.startHour = startHour;
        this.event = event;
    }
    public int startHour() {
        return startHour;
    }
    /** The behaviour event that fires when this period begins. */
    public BehaviourEvent event() {
        return event;
    }
    /** World time, between 0 and 23999, at which this period begins. */
    public long startTick() {
        return Math.floorMod(startHour * TICKS_PER_HOUR - CLOCK_OFFSET_TICKS, TICKS_PER_DAY);
    }
    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }
    /** The period containing the given world time. */
    public static DayPeriod at(long worldTime) {
        long clock = Math.floorMod(worldTime + CLOCK_OFFSET_TICKS, TICKS_PER_DAY);
        DayPeriod current = NIGHT;
        for (DayPeriod period : values()) {
            if (clock >= period.startHour * TICKS_PER_HOUR)
                current = period;
        }
        return current;
    }
}
