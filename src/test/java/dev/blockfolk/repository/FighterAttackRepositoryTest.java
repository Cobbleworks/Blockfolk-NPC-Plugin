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
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path folder;

    @Test
    void prefersTheNewAbilityFileAndLoadsLegacyFightersWhenItIsMissing() throws Exception {
        var canonical = folder.resolve("abilities.yml");
        var legacy = folder.resolve("fighters.yml");
        var custom = FighterTemplates.defaults().get(8).copy("custom_breath", "Custom Breath");
        java.nio.file.Files.writeString(legacy, FighterAttackRepository.encode(List.of(custom)).saveToString());
        var source = FighterAttackRepository.sourceFile(canonical.toFile());
        assertEquals(List.of(custom), FighterAttackRepository.decode(YamlConfiguration.loadConfiguration(source)));
        java.nio.file.Files.writeString(canonical, FighterAttackRepository.encode(List.of()).saveToString());
        source = FighterAttackRepository.sourceFile(canonical.toFile());
        assertEquals(List.of(), FighterAttackRepository.decode(YamlConfiguration.loadConfiguration(source)));
    }

    @Test
    void olderConesUseTheirSavedRangeAsLengthWhenTheNewFieldIsMissing() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("attacks:\n  old_cone:\n    shape: CONE\n    range: 12\n    angle: 45\n");
        FighterAttack cone = FighterAttackRepository.decode(yaml).getFirst();
        assertEquals(12, cone.range());
        assertEquals(12, cone.coneLength());
        assertNull(cone.icon());
    }

    @Test
    void sharedAttacksRoundTripThroughYamlIncludingCombinedEffectsAndInstantCast() throws Exception {
        FighterAttack custom = FighterTemplates.defaults().get(8).copy("dragon_breath", "Dragon Breath")
                .withConeLength(12).withTiming(0, 260)
                .withEffects(8, Set.of(Effect.FIRE, Effect.WITHER, Effect.KNOCKBACK), 5, 2, 1.5);
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
