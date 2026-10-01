package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SpecialAttackOptionsTest {
    @Test
    void customAssignmentsRoundTripInBehaviourActionsAndPreserveLegacyAttacks() {
        SpecialAttackOptions options = SpecialAttackOptions.disabled().toggle(SpecialAttack.FEAR)
                .toggle("dragon_breath").toggle("sonic_blast").withIntervalSeconds(5);
        FightOptions action = new FightOptions(AttackReaction.HUNTING, false, false, true, false, options);
        assertEquals(action, FightOptions.fromStored(action.storedValue()));
        assertEquals(Set.of("fear", "dragon_breath", "sonic_blast"), options.assignedAttackKeys());
        assertEquals(Set.of("fear", "sonic_blast"), options.toggle("dragon_breath").assignedAttackKeys());
        SpecialAttackOptions duplicatedLegacy = options.withFighterAttacks(List.of("fear", "sonic_blast"));
        assertFalse(duplicatedLegacy.toggle("fear").assignedAttackKeys().contains("fear"));
    }

    @Test
    void readsStableNamesAndSkipsUnknownOrDuplicateAttacks() {
        SpecialAttackOptions options = SpecialAttackOptions
                .fromStored(List.of(" LIFE_DRAIN ", "life_drain", "missing", "freezing_spell"), 8);
        assertEquals(Set.of(SpecialAttack.LIFE_DRAIN, SpecialAttack.FREEZING_SPELL), options.enabled());
        assertEquals(List.of("life_drain", "freezing_spell"), options.storedAttacks());
    }

    @Test
    void clampsCadenceAndCopiesEnabledSet() {
        Set<SpecialAttack> source = new HashSet<>(Set.of(SpecialAttack.FEAR));
        SpecialAttackOptions options = new SpecialAttackOptions(source, -1);
        source.clear();
        assertEquals(Set.of(SpecialAttack.FEAR), options.enabled());
        assertEquals(3, options.intervalSeconds());
        assertEquals(60, options.withIntervalSeconds(Integer.MAX_VALUE).intervalSeconds());
        assertThrows(UnsupportedOperationException.class, () -> options.enabled().clear());
        assertEquals(Set.of(), options.toggle(SpecialAttack.FEAR).enabled());
    }

    @Test
    void legacyConstructorsAndActionsDefaultToNoSpecialAttacks() {
        assertEquals(SpecialAttackOptions.disabled(), CombatProfile.disabled().specialAttacks());
        assertEquals(SpecialAttackOptions.disabled(),
                FightOptions.fromStored("aggression=fight_back;targets=players").specialAttacks());
        assertEquals(SpecialAttackOptions.disabled(), FightOptions.fromStored(null).specialAttacks());
    }

    @Test
    void profileEditsAndFightOptionEditsPreserveSpecialAttacks() {
        SpecialAttackOptions options = SpecialAttackOptions.disabled().toggle(SpecialAttack.LIFE_DRAIN);
        CombatProfile profile = CombatProfile.disabled().withSpecialAttacks(options).withMaxHealth(40)
                .withAlliance("guards").withRespawnSeconds(20).withShowBossBar(true).withDroppedExperience(5)
                .withAttackReaction(AttackReaction.HUNTING).withTargetAnimals(true).withTargetMobs(true)
                .withTargetNpcs(true).withTargetPlayers(true);
        assertEquals(options, profile.specialAttacks());
        FightOptions action = FightOptions.from(profile).withAttackReaction(AttackReaction.FIGHT_BACK)
                .withAnimals(false).withMobs(false).withNpcs(false).withPlayers(false);
        assertEquals(options, action.specialAttacks());
        assertEquals(action, FightOptions.fromStored(action.storedValue()));
    }

    @Test
    void readsReorderedSectionsAndInvalidCadenceWithoutLosingTargets() {
        FightOptions action = FightOptions.fromStored(
                "special-attacks=fear,unknown;targets=mobs,players;special-interval=bad;aggression=hunting");
        assertTrue(action.mobs());
        assertTrue(action.players());
        assertEquals(AttackReaction.HUNTING, action.attackReaction());
        assertEquals(Set.of(SpecialAttack.FEAR), action.specialAttacks().enabled());
        assertEquals(8, action.specialAttacks().intervalSeconds());
    }
}
