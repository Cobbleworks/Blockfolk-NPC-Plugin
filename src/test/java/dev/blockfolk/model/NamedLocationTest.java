package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NamedLocationTest {

    @Test
    void displayNameProducesStableKey() {
        NamedLocation location = NamedLocation.create("Town Square", new ActionLocation("world", 1, 2, 3));

        assertEquals("town-square", location.key());
        assertEquals("Town Square", location.displayName());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NamedLocation.create("  ", new ActionLocation("world", 1, 2, 3)));
    }
}
