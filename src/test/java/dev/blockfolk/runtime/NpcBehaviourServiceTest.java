package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.NpcInstance;

class NpcBehaviourServiceTest {

    @Test
    void waitSecondsConvertToSchedulerTicks() {
        assertEquals(100L, NpcBehaviourService.secondsToTicks("5"));
        assertEquals(30L, NpcBehaviourService.secondsToTicks("1.5"));
        assertEquals(1L, NpcBehaviourService.secondsToTicks("0.01"));
        assertEquals(0L, NpcBehaviourService.secondsToTicks("0"));
        assertEquals(0L, NpcBehaviourService.secondsToTicks("invalid"));
    }

    @Test
    void chatRangeUsesTheLocationCapturedWhenTheMessageWasSent() {
        Location sentFrom = new Location(null, 0.0, 64.0, 0.0);
        NpcInstance closest = instanceAt("closest", 2.0);
        NpcInstance boundary = instanceAt("boundary", 8.0);
        NpcInstance outside = instanceAt("outside", 8.01);

        // A later player position must not alter which NPCs heard the message.
        Location laterPlayerLocation = new Location(null, 100.0, 64.0, 0.0);
        assertEquals(List.of(closest, boundary),
                NpcBehaviourService.nearbyChatInstances(List.of(outside, boundary, closest), sentFrom));
        assertEquals(List.of(),
                NpcBehaviourService.nearbyChatInstances(List.of(outside, boundary, closest), laterPlayerLocation));
    }

    @Test
    void harvestSupportsOtherPlantableSeedsOnFarmland() {
        assertEquals(new NpcBehaviourService.Planting(Material.WHEAT_SEEDS, Material.WHEAT),
                NpcBehaviourService.plantingForSeed(Material.WHEAT_SEEDS, Material.FARMLAND));
        assertEquals(new NpcBehaviourService.Planting(Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM),
                NpcBehaviourService.plantingForSeed(Material.PUMPKIN_SEEDS, Material.FARMLAND));
        assertEquals(new NpcBehaviourService.Planting(Material.PITCHER_POD, Material.PITCHER_CROP),
                NpcBehaviourService.plantingForSeed(Material.PITCHER_POD, Material.FARMLAND));
        assertEquals(new NpcBehaviourService.Planting(Material.NETHER_WART, Material.NETHER_WART),
                NpcBehaviourService.plantingForSeed(Material.NETHER_WART, Material.SOUL_SAND));
        assertNull(NpcBehaviourService.plantingForSeed(Material.WHEAT_SEEDS, Material.DIRT));
    }

    private static NpcInstance instanceAt(String key, double x) {
        return new NpcInstance(UUID.randomUUID(), key, new Location(null, x, 64.0, 0.0));
    }
}
