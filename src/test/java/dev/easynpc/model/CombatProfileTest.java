package dev.easynpc.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatProfileTest {
    @Test
    void zeroHealthMeansInvulnerable() {
        CombatProfile profile = CombatProfile.disabled();

        assertEquals(0, profile.maxHealth());
        assertTrue(profile.invulnerable());
    }

    @Test
    void clampsHealthAndNormalizesShoutout() {
        CombatProfile profile = new CombatProfile(-5, null, "  Stand down!  ");

        assertEquals(0, profile.maxHealth());
        assertEquals(AggressionLevel.NONE, profile.aggressionLevel());
        assertEquals("Stand down!", profile.shoutout());
        assertNull(profile.withShoutout("   ").shoutout());
        assertEquals(CombatProfile.MAX_HEALTH, profile.withMaxHealth(Integer.MAX_VALUE).maxHealth());
    }

    @Test
    void aggressionCyclesAndReadsStoredNames() {
        assertEquals(AggressionLevel.FLEE, AggressionLevel.NONE.next());
        assertEquals(AggressionLevel.FIGHT_BACK, AggressionLevel.fromStored("fight-back"));
        assertEquals(AggressionLevel.FIGHTS_ON_SIGHT, AggressionLevel.fromStored("start fights on sight"));
        assertEquals(AggressionLevel.NONE, AggressionLevel.FIGHTS_ON_SIGHT.next());
    }
}
