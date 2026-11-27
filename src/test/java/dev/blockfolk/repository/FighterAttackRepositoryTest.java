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
    void presetsAreAlwaysListedFirstAndKeepTheirSavedEdits() {
        var presets = FighterTemplates.defaults();
        var custom = presets.get(8).copy("custom_breath", "Custom Breath");
        var editedFear = FighterTemplates.preset("fear").orElseThrow().withName("Terror");
        var loaded = FighterAttackRepository.withPresets(List.of(custom, editedFear));
        assertEquals(presets.size() + 1, loaded.size());
        assertEquals(presets.stream().map(FighterAttack::key).toList(),
                loaded.subList(0, presets.size()).stream().map(FighterAttack::key).toList());
        assertEquals("Terror", loaded.stream().filter(a -> a.key().equals("fear")).findFirst().orElseThrow().name());
        assertEquals(custom, loaded.getLast());
        assertTrue(FighterTemplates.isPreset("fear"));
        assertFalse(FighterTemplates.isPreset("custom_breath"));
    }

    @Test
    void chargedCastAndNewVisualThemesRoundTripAndOldTimingStillLoads() throws Exception {
        var charge = FighterTemplates.defaults().get(8).withCastMode(FighterAttack.CastMode.NEXT_ATTACK)
                .withVisual(FighterAttack.Visual.SPORES);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(FighterAttackRepository.encode(List.of(charge)).saveToString());
        assertEquals(List.of(charge), FighterAttackRepository.decode(yaml));
        yaml.loadFromString("attacks:\n  instant:\n    delay-ticks: 0\n  delayed:\n    delay-ticks: 45\n");
        var old = FighterAttackRepository.decode(yaml);
        assertEquals(FighterAttack.CastMode.INSTANT, old.get(0).castMode());
        assertEquals(FighterAttack.CastMode.DELAYED, old.get(1).castMode());
        assertEquals(45, old.get(1).delayTicks());
    }

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
    void removedChainAbilitiesLoadAsTargetCentredAreasWithTheirJumpRadius() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                attacks:
                  chain-lightning:
                    origin: NPC
                    shape: CHAIN
                    range: 12
                    size: 5
                    chain-targets: 4
                    visual: LIGHTNING
                  nova:
                    shape: RING
                """);
        assertEquals(List.of("chain-lightning"), FighterAttackRepository.legacyChainKeys(yaml));
        FighterAttack converted = FighterAttackRepository.decode(yaml).getFirst();
        assertEquals(FighterAttack.Shape.SPHERE, converted.shape());
        assertEquals(FighterAttack.Origin.TARGET, converted.origin());
        assertEquals(12, converted.range());
        assertEquals(5, converted.size());
        assertEquals(FighterAttack.Visual.LIGHTNING, converted.visual());
        String saved = FighterAttackRepository.encode(FighterAttackRepository.decode(yaml)).saveToString();
        assertFalse(saved.contains("CHAIN"));
        assertFalse(saved.contains("chain-targets"));
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

    @Test
    void everyTemplateRoundTripsIncludingNewShapesPulsesAndTriggers() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(FighterAttackRepository.encode(FighterTemplates.defaults()).saveToString());
        assertEquals(FighterTemplates.defaults(), FighterAttackRepository.decode(yaml));
        yaml.loadFromString("attacks:\n  legacy:\n    shape: SPHERE\n");
        FighterAttack legacy = FighterAttackRepository.decode(yaml).getFirst();
        assertEquals(1, legacy.pulses());
        assertEquals(0, legacy.minRange());
        assertEquals(FighterAttack.Condition.ALWAYS, legacy.condition());
    }
}
