package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthRegenerationTest {

    private final HealthRegeneration regeneration = HealthRegeneration.fromConfig(true, 10, 40);

    @Test
    void waitsUntilTheNpcHasBeenOutOfCombatLongEnough() {
        assertEquals(0.0, regeneration.amount(10.0, 20.0, 199));
        assertEquals(0.5, regeneration.amount(10.0, 20.0, 200));
    }

    @Test
    void scalesWithMaximumHealth() {
        assertEquals(5.0, regeneration.amount(50.0, 200.0, Long.MAX_VALUE));
    }

    @Test
    void neverOverheals() {
        assertEquals(0.2, regeneration.amount(19.8, 20.0, Long.MAX_VALUE), 1.0E-9);
    }

    @Test
    void skipsFullDeadAndDisabled() {
        assertEquals(0.0, regeneration.amount(20.0, 20.0, Long.MAX_VALUE));
        assertEquals(0.0, regeneration.amount(0.0, 20.0, Long.MAX_VALUE));
        assertEquals(0.0, HealthRegeneration.fromConfig(false, 10, 40).amount(10.0, 20.0, Long.MAX_VALUE));
    }

    @Test
    void clampsInvalidConfiguration() {
        HealthRegeneration clamped = HealthRegeneration.fromConfig(true, -5, 0);

        assertEquals(0, clamped.delayTicks());
        assertEquals(1, clamped.secondsToFull());
    }
}
