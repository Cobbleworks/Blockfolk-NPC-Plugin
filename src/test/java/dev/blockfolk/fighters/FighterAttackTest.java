package dev.blockfolk.fighters;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import dev.blockfolk.fighters.FighterAttack.*;

class FighterAttackTest {
    @Test
    void castModesHaveSafeDefaultsAndSurviveCopiesAndOtherEdits() {
        FighterAttack delayed = FighterTemplates.defaults().get(8);
        assertEquals(CastMode.DELAYED, delayed.castMode());
        FighterAttack instant = delayed.withCastMode(CastMode.INSTANT);
        assertEquals(0, instant.delayTicks());
        assertEquals(20, instant.withCastMode(CastMode.DELAYED).delayTicks());
        FighterAttack charge = delayed.withCastMode(CastMode.NEXT_ATTACK);
        assertEquals(0, charge.delayTicks());
        assertEquals(CastMode.NEXT_ATTACK, charge.copy("copied", "Copy").withName("New name").withVisual(Visual.ENCHANT)
                .withTiming(0, 300).withConeLength(12).withIcon(null).castMode());
        assertEquals(CastMode.INSTANT, delayed.withTiming(0, 100).castMode());
        assertEquals(CastMode.DELAYED, instant.withTiming(40, 100).castMode());
    }

    @Test
    void clampsUntrustedNumbersAndOwnsItsEffects() {
        Set<Effect> effects = new HashSet<>(Set.of(Effect.FIRE));
        FighterAttack attack = new FighterAttack(" Test Laser! ", "Laser", Origin.TARGET, Shape.BEAM, Double.NaN,
                Double.POSITIVE_INFINITY, 200, -1, 0, -4, effects, 999, -1, 20, Visual.SONIC);
        effects.clear();
        assertEquals("test_laser", attack.key());
        assertEquals(Origin.NPC, attack.origin());
        assertEquals(8, attack.range());
        assertEquals(2, attack.size());
        assertEquals(160, attack.angle());
        assertEquals(0, attack.delayTicks());
        assertEquals(20, attack.cooldownTicks());
        assertEquals(0, attack.damage());
        assertEquals(30, attack.effectSeconds());
        assertEquals(1, attack.effectLevel());
        assertEquals(2.5, attack.knockback());
        assertEquals(Set.of(Effect.FIRE), attack.effects());
        assertThrows(UnsupportedOperationException.class, () -> attack.effects().clear());
    }
    @Test
    void invalidNamesCannotProduceEmptyStorageKeys() {
        assertEquals("", FighterAttack.normalizeKey("..."));
        assertEquals("", FighterAttack.normalizeKey(null));
        assertThrows(IllegalArgumentException.class, () -> FighterTemplates.defaults().getFirst().copy("!", "bad"));
        assertEquals(64, FighterAttack.normalizeKey("a".repeat(100)).length());
    }
    @Test
    void includesAllLegacyAttacksAndRequestedNewPresets() {
        Set<String> keys = FighterTemplates.defaults().stream().map(FighterAttack::key)
                .collect(java.util.stream.Collectors.toSet());
        for (var legacy : dev.blockfolk.model.SpecialAttack.values())
            assertTrue(keys.contains(legacy.storedValue()));
        assertTrue(keys.containsAll(Set.of("fire_breath", "sonic_blast", "blink")));
        FighterAttack shockwave = FighterTemplates.defaults().stream().filter(a -> a.key().equals("shockwave"))
                .findFirst().orElseThrow();
        assertEquals(Origin.NPC, shockwave.origin());
        assertTrue(shockwave.effects().contains(Effect.KNOCKBACK));
    }

    @Test
    void builderNormalizesShapeSpecificSettings() {
        FighterAttack ring = FighterAttack.builder("nova", "Nova").shape(Shape.RING).size(4).build();
        assertEquals(2, ring.innerRadius());
        assertEquals(3.5, ring.toBuilder().innerRadius(7).build().innerRadius());
        assertEquals(Origin.TARGET, ring.toBuilder().origin(Origin.TARGET).build().origin());
        FighterAttack chain = FighterAttack.builder("chain", "Chain").shape(Shape.CHAIN).origin(Origin.TARGET)
                .pulses(5).chainTargets(99).build();
        assertEquals(Origin.NPC, chain.origin());
        assertEquals(1, chain.pulses());
        assertEquals(FighterAttack.MAX_CHAIN_TARGETS, chain.chainTargets());
        FighterAttack zone = FighterAttack.builder("zone", "Zone").pulses(50).pulseIntervalTicks(1).range(6)
                .minRange(10).build();
        assertEquals(FighterAttack.MAX_PULSES, zone.pulses());
        assertEquals(FighterAttack.MIN_PULSE_INTERVAL_TICKS, zone.pulseIntervalTicks());
        assertEquals(5, zone.minRange());
        assertEquals(Condition.ALWAYS, zone.condition());
        assertEquals(zone, zone.toBuilder().build());
    }

    @Test
    void knockbackAndPullCannotBeCombinedAndCasterOnlyAbilitiesDoNotHitVictims() {
        FighterAttack both = FighterAttack.builder("push", "Push").effects(Effect.KNOCKBACK, Effect.PULL).build();
        assertEquals(Set.of(Effect.KNOCKBACK), both.effects());
        FighterAttack buff = FighterAttack.builder("buff", "Buff").damage(0).effects(Effect.STRENGTH).build();
        assertFalse(buff.affectsVictims());
        assertTrue(buff.toBuilder().damage(2).build().affectsVictims());
        assertFalse(buff.toBuilder().shape(Shape.SELF).damage(10).effects(Effect.POISON).build().affectsVictims());
    }
}
