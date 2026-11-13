package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class NpcVehicleServiceTest {

    private static final long COOLDOWN = NpcVehicleService.EXIT_COOLDOWN_TICKS;

    @Test
    void boardingWaitsOutTheCooldownAfterAnyRide() {
        assertFalse(NpcVehicleService.reboardAllowed(0, false, false));
        assertFalse(NpcVehicleService.reboardAllowed(COOLDOWN - 1, false, false));
        assertTrue(NpcVehicleService.reboardAllowed(COOLDOWN, false, false));
    }

    @Test
    void ejectedNpcDoesNotClimbBackIntoTheCartItIsStandingBeside() {
        assertFalse(NpcVehicleService.reboardAllowed(COOLDOWN * 100, true, true));
        // Another cart at the same station is fine, as is the old one once the NPC
        // walked away.
        assertTrue(NpcVehicleService.reboardAllowed(COOLDOWN * 100, true, false));
        assertTrue(NpcVehicleService.reboardAllowed(COOLDOWN * 100, false, true));
    }

    @Test
    void requestedBoardingCompletesWithinReachOfTheVehicle() {
        Location vehicle = new Location(null, 10.5, 64.0, 10.5);

        assertTrue(NpcVehicleService.withinRequestedRange(new Location(null, 11.5, 64.0, 11.5), vehicle));
        assertTrue(NpcVehicleService.withinRequestedRange(new Location(null, 10.5, 65.5, 12.5), vehicle));
        assertFalse(NpcVehicleService.withinRequestedRange(new Location(null, 13.0, 64.0, 10.5), vehicle));
        assertFalse(NpcVehicleService.withinRequestedRange(new Location(null, 10.5, 67.0, 10.5), vehicle));
    }

    @Test
    void vehicleKindsReadNaturally() {
        assertEquals("minecart", NpcVehicles.readable("MINECART"));
        assertEquals("bamboo chest raft", NpcVehicles.readable("BAMBOO_CHEST_RAFT"));
    }
}
