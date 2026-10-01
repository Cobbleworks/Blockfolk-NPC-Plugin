package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.SpecialAttack;
import dev.blockfolk.model.SpecialAttackOptions;
import org.bukkit.configuration.file.YamlConfiguration;

class NpcDefinitionRepositoryTest {
    @Test
    void savesAndReloadsSpecialAttacksWithTheCombatProfile() throws Exception {
        CombatProfile profile = CombatProfile.disabled().withMaxHealth(40).withAlliance("guards")
                .withAttackReaction(AttackReaction.HUNTING).withTargetPlayers(true)
                .withSpecialAttacks(SpecialAttackOptions.disabled().toggle(SpecialAttack.LIFE_DRAIN)
                        .toggle(SpecialAttack.FREEZING_SPELL).withIntervalSeconds(15));
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
}
