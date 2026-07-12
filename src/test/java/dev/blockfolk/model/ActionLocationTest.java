package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionLocationTest {

    @Test
    void serializedWaypointRoundTrips() {
        ActionLocation location = new ActionLocation("world_nether", 12.5, 65.0, -8.5);

        assertEquals(location, ActionLocation.parse(location.serialize()).orElseThrow());
        assertEquals("world_nether (12.5, 65, -8.5)", location.display());
    }

    @Test
    void malformedWaypointIsRejected() {
        assertTrue(ActionLocation.parse(null).isEmpty());
        assertTrue(ActionLocation.parse("world|not-a-number|1|2").isEmpty());
        assertTrue(ActionLocation.parse("world|1|2").isEmpty());
    }
}
