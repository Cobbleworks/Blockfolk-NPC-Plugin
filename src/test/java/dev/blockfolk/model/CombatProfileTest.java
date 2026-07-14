package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                -5, -10, null, false, false, false, false, "  guards  ", "  Stand down!  ", false
        );

        assertEquals(0, profile.maxHealth());
        assertEquals(0, profile.respawnSeconds());
        assertEquals(AttackReaction.IGNORE, profile.attackReaction());
        assertEquals("guards", profile.alliance());
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
        assertEquals(AttackReaction.HUNTING, AttackReaction.FLEE.next());
        assertEquals(AttackReaction.IGNORE, AttackReaction.HUNTING.next());
    }

    @Test
    void fightOptionsRoundTripAndAcceptMonsterAlias() {
        FightOptions options = FightOptions.fromStored("monsters, NPCs");

        assertTrue(options.mobs());
        assertTrue(options.npcs());
        assertFalse(options.animals());
        assertEquals("mobs,npcs", options.storedValue());
        assertEquals(options, FightOptions.fromStored(options.storedValue()));
    }

    @Test
    void fightOptionsStoreAggressionAndStillReadLegacyTargetLists() {
        FightOptions options = new FightOptions(AttackReaction.HUNTING, true, false, true, false);

        assertEquals("aggression=hunting;targets=mobs,players", options.storedValue());
        assertEquals(options, FightOptions.fromStored(options.storedValue()));
        assertNull(FightOptions.fromStored("mobs,players").attackReaction());
    }

    @Test
    void targetTogglesAreIndependent() {
        CombatProfile profile = CombatProfile.disabled().withTargetAnimals(true).withTargetNpcs(true);

        assertTrue(profile.targetAnimals());
        assertTrue(profile.targetNpcs());
        assertTrue(profile.hasSightTargets());
    }

    @Test
    void bossBarToggleIsPreservedByOtherChanges() {
        CombatProfile profile = CombatProfile.disabled().withShowBossBar(true).withMaxHealth(20);

        assertTrue(profile.showBossBar());
        assertTrue(profile.withAlliance("guards").showBossBar());
        assertFalse(CombatProfile.disabled().showBossBar());
    }

    @Test
    void onlyNonEmptyMatchingAlliancesAreAllied() {
        CombatProfile guards = CombatProfile.disabled().withAlliance(" guards ");

        assertTrue(guards.alliedWith(CombatProfile.disabled().withAlliance("guards")));
        assertFalse(guards.alliedWith(CombatProfile.disabled().withAlliance("raiders")));
        assertFalse(CombatProfile.disabled().alliedWith(CombatProfile.disabled()));
        assertNull(guards.withAlliance("   ").alliance());
    }
}
