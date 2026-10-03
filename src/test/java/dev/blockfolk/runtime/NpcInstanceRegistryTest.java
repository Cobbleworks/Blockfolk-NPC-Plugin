package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NpcInstanceRegistryTest {
    @Test
    void freezingSpellSlowsNativeNavigationAndNormalSpeedReturnsWhenItExpires() {
        assertEquals(1.6, NpcInstanceRegistry.slowedNavigationSpeed(4.0, 3), 0.0001);
        assertEquals(3.4, NpcInstanceRegistry.slowedNavigationSpeed(4.0, 0), 0.0001);
        assertEquals(4.0, NpcInstanceRegistry.slowedNavigationSpeed(4.0, -1));
        assertEquals(0.2, NpcInstanceRegistry.slowedNavigationSpeed(4.0, 100), 0.0001);
    }
}
