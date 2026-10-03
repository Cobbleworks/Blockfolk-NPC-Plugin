package dev.blockfolk.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import java.util.List;

import dev.blockfolk.fighters.FighterAttack;
import dev.blockfolk.fighters.FighterAttack.Condition;
import dev.blockfolk.model.SpecialAttack;
import dev.blockfolk.model.SpecialAttackOptions;

class SpecialAttackSchedulerTest {
    private final Random random = new Random(42);
    private final SpecialAttackOptions options = new SpecialAttackOptions(Set.of(SpecialAttack.LIGHTNING_MARK), 3);

    @Test
    void waitsBeforeFirstAttackAndHonorsIndividualCooldown() {
        SpecialAttackScheduler scheduler = new SpecialAttackScheduler(0, options);
        assertNull(scheduler.select(59, options, 4, random));
        assertEquals(SpecialAttack.LIGHTNING_MARK, scheduler.select(60, options, 4, random));
        for (int tick = 61; tick < 460; tick++)
            assertNull(scheduler.select(tick, options, 4, random));
        assertEquals(SpecialAttack.LIGHTNING_MARK, scheduler.select(480, options, 4, random));
    }

    @Test
    void filtersByEnabledAttacksAndRangeAndRetriesAfterApproaching() {
        SpecialAttackScheduler scheduler = new SpecialAttackScheduler(0, options);
        assertNull(scheduler.select(60, options, 145, random));
        assertNull(scheduler.select(79, options, 4, random));
        assertEquals(SpecialAttack.LIGHTNING_MARK, scheduler.select(80, options, 4, random));
        assertNull(scheduler.select(500, SpecialAttackOptions.disabled(), 4, random));
    }

    @Test
    void canUseAnotherAttackWhileFirstIsCoolingDown() {
        SpecialAttackOptions multiple = new SpecialAttackOptions(
                Set.of(SpecialAttack.LIGHTNING_MARK, SpecialAttack.POISON_SPIT), 3);
        SpecialAttackScheduler scheduler = new SpecialAttackScheduler(0, multiple);
        SpecialAttack first = scheduler.select(60, multiple, 4, random);
        SpecialAttack second = scheduler.select(160, multiple, 4, random);
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
        assertNull(scheduler.select(240, multiple, 4, random));
    }

    @Test
    void maintainsIndependentCooldownsForEachNpc() {
        SpecialAttackScheduler first = new SpecialAttackScheduler(0, options);
        SpecialAttackScheduler second = new SpecialAttackScheduler(0, options);
        assertNotNull(first.select(60, options, 4, random));
        assertNull(first.select(160, options, 4, random));
        assertNotNull(second.select(160, options, 4, random));
    }

    @Test
    void changingCadenceReschedulesWithoutRemovingAbilityCooldowns() {
        SpecialAttackScheduler scheduler = new SpecialAttackScheduler(0, options);
        assertNotNull(scheduler.select(60, options, 4, random));
        SpecialAttackOptions slower = options.withIntervalSeconds(10);
        assertNull(scheduler.select(61, slower, 4, random));
        assertNull(scheduler.select(260, slower, 4, random));
        assertNull(scheduler.select(261, slower, 4, random));
        assertNotNull(scheduler.select(481, slower, 4, random));
    }

    @Test
    void minimumRangeWaitsForTheOpponentToBeFarEnough() {
        FighterAttack dash = FighterAttack.builder("dash", "Dash").range(10).minRange(4).build();
        SpecialAttackOptions dashOnly = SpecialAttackOptions.disabled().toggle("dash").withIntervalSeconds(3);
        SpecialAttackScheduler scheduler = new SpecialAttackScheduler(0, dashOnly);
        assertNull(scheduler.select(60, dashOnly, List.of(dash), 9, random));
        assertEquals(dash, scheduler.select(80, dashOnly, List.of(dash), 25, random));
    }

    @Test
    void metTriggersTakePrecedenceAndUnmetTriggersAreSkipped() {
        FighterAttack plain = FighterAttack.builder("plain", "Plain").build();
        FighterAttack heal = FighterAttack.builder("heal", "Heal").condition(Condition.CASTER_HURT).build();
        SpecialAttackOptions both = SpecialAttackOptions.disabled().toggle("plain").toggle("heal")
                .withIntervalSeconds(3);
        for (int i = 0; i < 10; i++) {
            SpecialAttackScheduler healthy = new SpecialAttackScheduler(0, both);
            assertEquals(plain, healthy.select(60, both, List.of(plain, heal), new CastContext(4, () -> 1, () -> 1),
                    random));
            SpecialAttackScheduler hurt = new SpecialAttackScheduler(0, both);
            assertEquals(heal, hurt.select(60, both, List.of(plain, heal), new CastContext(4, () -> 0.4, () -> 1),
                    random));
        }
    }
}
