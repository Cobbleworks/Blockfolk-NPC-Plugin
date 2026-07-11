package dev.blockfolk.model;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class NpcInstanceTest {
    @Test
    void storesDefinitionKeySeparatelyFromInstanceId() {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 1.0, 2.0, 3.0));

        assertEquals("guard", instance.getDefinitionKey());
        assertEquals(1.0, instance.getLocation().getX());
        assertNull(instance.getLocation().getWorld());
    }

    @Test
    void returnsClonedLocation() {
        Location location = new Location(null, 1.0, 2.0, 3.0);
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", location);

        assertNotSame(location, instance.getLocation());
    }
}
