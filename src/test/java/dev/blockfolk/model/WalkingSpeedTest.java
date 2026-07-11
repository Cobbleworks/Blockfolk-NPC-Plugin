package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkingSpeedTest {
    @Test
    void cyclesThroughAllSpeedTiersAndWraps() {
        assertEquals(WalkingSpeed.SLOW, WalkingSpeed.SLOUCH.next());
        assertEquals(WalkingSpeed.NORMAL, WalkingSpeed.SLOW.next());
        assertEquals(WalkingSpeed.FAST, WalkingSpeed.NORMAL.next());
        assertEquals(WalkingSpeed.VERY_FAST, WalkingSpeed.FAST.next());
        assertEquals(WalkingSpeed.SLOUCH, WalkingSpeed.VERY_FAST.next());
    }

    @Test
    void usesPlayerWalkingSpeedAsTheNewDefault() {
        MovementProfile profile = MovementProfile.disabled();

        assertEquals(WalkingSpeed.NORMAL, profile.walkingSpeed());
        assertEquals(4.317, profile.walkingSpeed().blocksPerSecond());
        assertEquals(WalkingSpeed.NORMAL, WalkingSpeed.fromStored(null));
        assertEquals(WalkingSpeed.NORMAL, WalkingSpeed.fromStored("unknown"));
    }

    @Test
    void preservesChosenSpeedWhenAssigningOrClearingRoutes() {
        MovementProfile profile = MovementProfile.disabled().withWalkingSpeed(WalkingSpeed.VERY_FAST);

        profile = profile.withRoute("Village Patrol");
        assertTrue(profile.enabled());
        assertEquals("village-patrol", profile.routeKey());
        assertEquals(WalkingSpeed.VERY_FAST, profile.walkingSpeed());

        profile = profile.withoutRoute();
        assertFalse(profile.enabled());
        assertEquals(WalkingSpeed.VERY_FAST, profile.walkingSpeed());
    }
}
