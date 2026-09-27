package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;

class NpcDefinitionRepositoryTest {
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
