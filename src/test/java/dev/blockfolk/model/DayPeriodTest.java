package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DayPeriodTest {
    @Test
    void periodsStartAtTheirClockHourInWorldTicks() {
        assertEquals(18000, DayPeriod.NIGHT.startTick());
        assertEquals(23000, DayPeriod.MORNING.startTick());
        assertEquals(4000, DayPeriod.NOON.startTick());
        assertEquals(8000, DayPeriod.AFTERNOON.startTick());
        assertEquals(13000, DayPeriod.EVENING.startTick());
    }

    @Test
    void worldTimeMapsToTheContainingPeriod() {
        assertEquals(DayPeriod.MORNING, DayPeriod.at(0)); // 06:00
        assertEquals(DayPeriod.MORNING, DayPeriod.at(3999)); // 09:59
        assertEquals(DayPeriod.NOON, DayPeriod.at(4000));
        assertEquals(DayPeriod.NOON, DayPeriod.at(6000)); // 12:00
        assertEquals(DayPeriod.AFTERNOON, DayPeriod.at(8000));
        assertEquals(DayPeriod.AFTERNOON, DayPeriod.at(12999)); // 18:59
        assertEquals(DayPeriod.EVENING, DayPeriod.at(13000));
        assertEquals(DayPeriod.NIGHT, DayPeriod.at(18000)); // 00:00
        assertEquals(DayPeriod.NIGHT, DayPeriod.at(22999));
        assertEquals(DayPeriod.MORNING, DayPeriod.at(23000));
        assertEquals(DayPeriod.NOON, DayPeriod.at(24000L * 5 + 5000)); // later days wrap
    }

    @Test
    void eachPeriodFiresItsOwnBehaviourEvent() {
        for (DayPeriod period : DayPeriod.values())
            assertEquals(period.name(), period.event().name());
    }
}
