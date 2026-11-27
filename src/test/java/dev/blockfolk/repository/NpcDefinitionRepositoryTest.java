package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.SpecialAttack;
import dev.blockfolk.model.SpecialAttackOptions;
import dev.blockfolk.model.Shop;
import org.bukkit.configuration.file.YamlConfiguration;

class NpcDefinitionRepositoryTest {
    @Test
    void findsRenamedPresetsByTheirCurrentNameAndStillByKey() {
        NpcDefinition baker = NpcDefinition.create("Town Baker");
        baker.setDisplayName("Town Florist");
        List<NpcDefinition> all = List.of(baker, NpcDefinition.create("Guard"));

        assertEquals(List.of(baker), NpcDefinitionRepository.matchNameOrKey(all, "town-florist"));
        assertEquals(List.of(baker), NpcDefinitionRepository.matchNameOrKey(all, "Town Florist"));
        assertEquals(List.of(baker), NpcDefinitionRepository.matchNameOrKey(all, "town-baker"));
        assertEquals(List.of(), NpcDefinitionRepository.matchNameOrKey(all, "smith"));
        assertEquals(List.of(), NpcDefinitionRepository.matchNameOrKey(all, " "));
    }

    @Test
    void theCurrentNameWinsOverAnotherPresetsOldKey() {
        NpcDefinition watchman = NpcDefinition.create("Guard");
        watchman.setDisplayName("Watchman");
        NpcDefinition guard = NpcDefinition.create("Soldier");
        guard.setDisplayName("Guard");

        assertEquals(List.of(guard), NpcDefinitionRepository.matchNameOrKey(List.of(watchman, guard), "guard"));
    }

    @Test
    void sharedLegacyNamesAreAmbiguousUnlessAKeyMatches() {
        NpcDefinition first = NpcDefinition.create("Baker One");
        first.setDisplayName("Baker");
        NpcDefinition second = NpcDefinition.create("Baker Two");
        second.setDisplayName("Baker");
        NpcDefinition original = NpcDefinition.create("Baker");

        assertEquals(List.of(first, second), NpcDefinitionRepository.matchNameOrKey(List.of(first, second), "baker"));
        assertEquals(List.of(original), NpcDefinitionRepository.matchNameOrKey(List.of(first, original), "baker"));
        assertEquals(List.of(second), NpcDefinitionRepository.matchNameOrKey(List.of(first, second), "baker-two"));
    }

    @Test
    void namesMustNotClashWithAnotherPresetsNameOrKey() {
        NpcDefinition watchman = NpcDefinition.create("Guard");
        watchman.setDisplayName("Watchman");
        NpcDefinition baker = NpcDefinition.create("Baker");
        List<NpcDefinition> all = List.of(watchman, baker);

        assertEquals(watchman, NpcDefinitionRepository.nameConflict(all, "Watchman", null).orElse(null));
        assertEquals(watchman, NpcDefinitionRepository.nameConflict(all, "guard", baker).orElse(null));
        assertEquals(baker, NpcDefinitionRepository.nameConflict(all, "BAKER!", null).orElse(null));
        assertNull(NpcDefinitionRepository.nameConflict(all, "Guard", watchman).orElse(null));
        assertNull(NpcDefinitionRepository.nameConflict(all, "Florist", null).orElse(null));
    }

    @Test
    void savesAndReloadsSpecialAttacksWithTheCombatProfile() throws Exception {
        CombatProfile profile = CombatProfile.disabled().withMaxHealth(40).withArmor(8).withAlliance("guards")
                .withAttackReaction(AttackReaction.HUNTING).withTargetPlayers(true)
                .withSpecialAttacks(SpecialAttackOptions.disabled().toggle(SpecialAttack.LIFE_DRAIN)
                        .toggle(SpecialAttack.FREEZING_SPELL).toggle("fire_breath").toggle("custom_laser")
                        .withIntervalSeconds(15));
        YamlConfiguration saved = new YamlConfiguration();
        NpcDefinitionRepository.writeCombatProfile(saved, profile);
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(saved.saveToString());
        assertEquals(profile, NpcDefinitionRepository.readCombatProfile(loaded));
    }

    @Test
    void oldDefinitionsKeepTheirCombatSettingsAndDefaultToNoSpecialAttacks() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString(
                "combat:\n  max-health: 30\n  aggression-level: fight_back\n  targets:\n    players: true\n");
        assertEquals(CombatProfile.disabled().withMaxHealth(30).withAttackReaction(AttackReaction.FIGHT_BACK)
                .withTargetPlayers(true), NpcDefinitionRepository.readCombatProfile(old));
    }

    @Test
    void combinesLegacyAttackAndDamageActionsInTheirOriginalOrder() {
        BehaviourAction attacked = new BehaviourAction(BehaviourActionType.START_COMBAT, null);
        BehaviourAction damaged = new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Ouch!");

        assertEquals(List.of(attacked, damaged),
                NpcDefinitionRepository.mergeDamageActions(List.of(attacked), List.of(damaged)));
    }

    @Test
    void migratedDamageRowRespectsEditorLimit() {
        BehaviourAction attacked = new BehaviourAction(BehaviourActionType.START_COMBAT, null);
        BehaviourAction damaged = new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Ouch!");

        assertEquals(List.of(attacked, attacked, attacked, attacked, attacked, damaged, damaged),
                NpcDefinitionRepository.mergeDamageActions(List.of(attacked, attacked, attacked, attacked, attacked),
                        List.of(damaged, damaged, damaged)));
    }

    @Test
    void sunriseAndSunsetRowsMoveToMorningAndEveningWithoutReplacingNewRows() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("""
                behaviours:
                  sunrise:
                  - type: wait
                    value: '1.0'
                  sunset:
                  - type: wait
                    value: '2.0'
                """);
        NpcDefinition definition = new NpcDefinition("guard");
        BehaviourAction evening = new BehaviourAction(BehaviourActionType.WAIT, "3.0");
        definition.setBehaviourActions(BehaviourEvent.EVENING, List.of(evening));
        NpcDefinitionRepository.migrateLegacyTimeEvents(old, definition,
                stored -> stored.stream().map(BehaviourActionCodec::decode).toList());
        assertEquals(List.of(new BehaviourAction(BehaviourActionType.WAIT, "1.0")),
                definition.getBehaviourActions(BehaviourEvent.MORNING));
        assertEquals(List.of(evening), definition.getBehaviourActions(BehaviourEvent.EVENING));
    }

    @Test
    void legacyShopSectionIsReadForMigration() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("shop:\n  enabled: false\n  title: Fresh Bread\n  offers: []\n");

        Shop shop = NpcDefinitionRepository.readLegacyShop(old, "baker", "Baker");

        assertEquals("baker", shop.key());
        assertEquals("Fresh Bread", shop.name());
    }

    @Test
    void legacyShopWithOnlyEmptyTradesHasNothingToMigrate() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("shop:\n  offers:\n    - {}\n  title: '  '\n");

        assertNull(NpcDefinitionRepository.readLegacyShop(old, "baker", "Baker"),
                "a section with only empty trades has nothing to migrate");
    }

    @Test
    void definitionsWithoutAShopSectionHaveNothingToMigrate() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("combat:\n  max-health: 30\n");
        assertNull(NpcDefinitionRepository.readLegacyShop(old, "baker", "Baker"));
    }
}
