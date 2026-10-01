package dev.blockfolk.fighters;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import dev.blockfolk.fighters.FighterAttack.*;

class FighterAttackTest {
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
}
