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

    @Test
    void keepsSpawnLocationSeparateFromCurrentLocation() {
        Location spawnLocation = new Location(null, 1.0, 2.0, 3.0);
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", spawnLocation);

        instance.setLocation(new Location(null, 10.0, 20.0, 30.0));

        assertEquals(spawnLocation, instance.getSpawnLocation());
        assertNotSame(spawnLocation, instance.getSpawnLocation());
    }

    @Test
    void canRelocateSpawnLocationIndependently() {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 1.0, 2.0, 3.0));
        Location relocatedSpawn = new Location(null, 4.0, 5.0, 6.0);

        instance.setSpawnLocation(relocatedSpawn);

        assertEquals(relocatedSpawn, instance.getSpawnLocation());
        assertNotSame(relocatedSpawn, instance.getSpawnLocation());
    }

    @Test
    void hasSeparateTemporaryInventory() {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));
        assertEquals(27, instance.getTemporaryInventoryContents().length);
        assertNotSame(instance.getTemporaryInventoryContents(), instance.getTemporaryInventoryContents());
    }
}
