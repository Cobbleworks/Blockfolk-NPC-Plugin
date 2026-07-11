package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatProfileTest {
    @Test
    void zeroHealthMeansInvulnerable() {
        CombatProfile profile = CombatProfile.disabled();

        assertEquals(0, profile.maxHealth());
        assertEquals(0, profile.respawnSeconds());
        assertTrue(profile.invulnerable());
    }

    @Test
    void clampsHealthAndNormalizesShoutout() {
        CombatProfile profile = new CombatProfile(
                -5, -10, null, false, false, false, false, "  Stand down!  "
        );

        assertEquals(0, profile.maxHealth());
        assertEquals(0, profile.respawnSeconds());
        assertEquals(AttackReaction.IGNORE, profile.attackReaction());
        assertEquals("Stand down!", profile.shoutout());
        assertNull(profile.withShoutout("   ").shoutout());
        assertEquals(CombatProfile.MAX_HEALTH, profile.withMaxHealth(Integer.MAX_VALUE).maxHealth());
        assertEquals(20, profile.withRespawnSeconds(20).respawnSeconds());
    }

    @Test
    void attackReactionCyclesAndReadsStoredNames() {
        assertEquals(AttackReaction.FIGHT_BACK, AttackReaction.IGNORE.next());
        assertEquals(AttackReaction.FIGHT_BACK, AttackReaction.fromStored("fights-back"));
        assertEquals(AttackReaction.FLEE, AttackReaction.FIGHT_BACK.next());
        assertEquals(AttackReaction.IGNORE, AttackReaction.FLEE.next());
    }

    @Test
    void targetTogglesAreIndependent() {
        CombatProfile profile = CombatProfile.disabled().withTargetAnimals(true).withTargetNpcs(true);

        assertTrue(profile.targetAnimals());
        assertTrue(profile.targetNpcs());
        assertTrue(profile.hasSightTargets());
    }
}
