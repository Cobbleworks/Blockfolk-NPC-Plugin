package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NpcBehaviourServiceTest {

    @Test
    void waitSecondsConvertToSchedulerTicks() {
        assertEquals(100L, NpcBehaviourService.secondsToTicks("5"));
        assertEquals(30L, NpcBehaviourService.secondsToTicks("1.5"));
        assertEquals(1L, NpcBehaviourService.secondsToTicks("0.01"));
        assertEquals(0L, NpcBehaviourService.secondsToTicks("0"));
        assertEquals(0L, NpcBehaviourService.secondsToTicks("invalid"));
    }
}
