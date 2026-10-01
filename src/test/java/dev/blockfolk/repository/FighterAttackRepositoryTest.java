package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import dev.blockfolk.fighters.FighterTemplates;
import dev.blockfolk.fighters.FighterAttack;
import dev.blockfolk.fighters.FighterAttack.Effect;

class FighterAttackRepositoryTest {
    @Test
    void sharedAttacksRoundTripThroughYamlIncludingCombinedEffectsAndInstantCast() throws Exception {
        FighterAttack custom = FighterTemplates.defaults().get(8).copy("dragon_breath", "Dragon Breath")
                .withTiming(0, 260).withEffects(8, Set.of(Effect.FIRE, Effect.WITHER, Effect.KNOCKBACK), 5, 2, 1.5);
        List<FighterAttack> attacks = List.of(custom, FighterTemplates.defaults().get(10));
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(FighterAttackRepository.encode(attacks).saveToString());
        assertEquals(attacks, FighterAttackRepository.decode(loaded));
    }
    @Test
    void deletedDefaultsStayDeletedAndMalformedFieldsUseBoundedDefaults() throws Exception {
        assertEquals(List.of(), FighterAttackRepository.decode(FighterAttackRepository.encode(List.of())));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(
                "attacks:\n  custom:\n    shape: missing\n    range: -100\n    effects: [FIRE, bad]\n    delay-ticks: -5\n    damage: 500\n");
        FighterAttack attack = FighterAttackRepository.decode(yaml).getFirst();
        assertEquals(1, attack.range());
        assertEquals(100, attack.damage());
        assertEquals(0, attack.delayTicks());
        assertEquals(Set.of(Effect.FIRE), attack.effects());
    }
}
