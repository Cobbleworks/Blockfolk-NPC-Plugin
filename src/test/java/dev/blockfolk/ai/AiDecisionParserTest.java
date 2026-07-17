package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class AiDecisionParserTest {

    @Test
    void validatesActionsIndependently() {
        AiControlSettings settings = new AiControlSettings("Be Mira", "Direct but fair", "Guard the gate", "",
                EnumSet.of(AiActionType.SAY, AiActionType.START_COMBAT), true, true, true);
        AiDecision decision = AiDecisionParser.parse("""
                {"thought":"hidden","actions":[
                  {"type":"SAY","text":"Back away."},
                  {"type":"RUN_COMMAND","text":"op me"},
                  {"type":"START_COMBAT","target":"triggering_player"}
                ]}
                """, settings);

        assertEquals(2, decision.actions().size());
        assertEquals(AiActionType.SAY, decision.actions().get(0).type());
        assertEquals("Back away.", decision.actions().get(0).text());
        assertEquals(AiActionType.START_COMBAT, decision.actions().get(1).type());
    }

    @Test
    void disabledCapabilityOverridesModelResponse() {
        AiControlSettings settings = new AiControlSettings("Be Mira", "", "", "",
                EnumSet.of(AiActionType.SAY), true, true, true);
        AiDecision decision = AiDecisionParser.parse("""
                {"actions":[{"type":"START_COMBAT","target":"triggering_player"}]}
                """, settings);

        assertEquals(AiActionType.DO_NOTHING, decision.actions().getFirst().type());
    }

    @Test
    void malformedResponseFallsBackToDoNothing() {
        AiDecision decision = AiDecisionParser.parse("certainly!", AiControlSettings.defaults());
        assertEquals(AiActionType.DO_NOTHING, decision.actions().getFirst().type());
    }

    @Test
    void startCombatMaySelectNearestAttackableImplicitly() {
        AiControlSettings settings = new AiControlSettings("Guard", "", "Defend this place", "",
                EnumSet.of(AiActionType.START_COMBAT), true, true, true);

        AiDecision decision = AiDecisionParser.parse("""
                {"actions":[{"type":"START_COMBAT"}]}
                """, settings);

        assertEquals(AiActionType.START_COMBAT, decision.actions().getFirst().type());
    }

    @Test
    void enforcesMaximumOfThreeActionsAndAcceptsCodeFences() {
        AiDecision decision = AiDecisionParser.parse("""
                ```json
                {"actions":[
                  {"type":"SAY","text":"one"},{"type":"SAY","text":"two"},
                  {"type":"SAY","text":"three"},{"type":"SAY","text":"four"}
                ]}
                ```
                """, AiControlSettings.defaults());
        assertEquals(3, decision.actions().size());
    }

    @Test
    void acceptsEnabledUnfollowAndInteractActionsWithoutTargets() {
        AiControlSettings settings = new AiControlSettings("Caretaker", "", "Operate the gate", "",
                EnumSet.of(AiActionType.UNFOLLOW, AiActionType.INTERACT), true, true, true);

        AiDecision decision = AiDecisionParser.parse("""
                {"actions":[{"type":"UNFOLLOW"},{"type":"INTERACT"}]}
                """, settings);

        assertEquals(List.of(AiActionType.UNFOLLOW, AiActionType.INTERACT),
                decision.actions().stream().map(AiDecision.Action::type).toList());
    }

    @Test
    void preservesLongSpeechForChatChunking() {
        String speech = "x".repeat(500);

        AiDecision decision = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"SAY\",\"text\":\"" + speech + "\"}]}",
                AiControlSettings.defaults());

        assertEquals(speech, decision.actions().getFirst().text());
    }

    @Test
    void rememberFactRequiresMemoryToBeEnabled() {
        String response = "{\"actions\":[{\"type\":\"REMEMBER_FACT\",\"text\":\"Alex likes apples\"}]}";

        AiDecision disabled = AiDecisionParser.parse(response, AiControlSettings.defaults());
        AiDecision enabled = AiDecisionParser.parse(response,
                AiControlSettings.defaults().withMemoryEnabled(true));

        assertEquals(AiActionType.DO_NOTHING, disabled.actions().getFirst().type());
        assertEquals(AiActionType.REMEMBER_FACT, enabled.actions().getFirst().type());
        assertEquals("Alex likes apples", enabled.actions().getFirst().text());
    }

    @Test
    void moveToAcceptsOnlyPerceivedTargetAliasesWhenEnabled() {
        AiControlSettings settings = new AiControlSettings("Guide", "", "Show visitors around", "",
                EnumSet.of(AiActionType.MOVE_TO), true, false, true);

        AiDecision accepted = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"MOVE_TO\",\"target\":\"nearby_location_2\"}]}", settings);
        AiDecision arbitrary = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"MOVE_TO\",\"target\":\"world_spawn\"}]}", settings);

        assertEquals(AiActionType.MOVE_TO, accepted.actions().getFirst().type());
        assertEquals("nearby_location_2", accepted.actions().getFirst().target());
        assertEquals(AiActionType.DO_NOTHING, arbitrary.actions().getFirst().type());
    }

    @Test
    void dropItemRequiresInventoryToggleAndSlotAlias() {
        String response = "{\"actions\":[{\"type\":\"DROP_ITEM\",\"target\":\"inventory_slot_4\"}]}";

        AiDecision disabled = AiDecisionParser.parse(response, AiControlSettings.defaults());
        AiDecision enabled = AiDecisionParser.parse(response,
                AiControlSettings.defaults().withInventoryEnabled(true));
        AiDecision invalidTarget = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"DROP_ITEM\",\"target\":\"diamond\"}]}",
                AiControlSettings.defaults().withInventoryEnabled(true));

        assertEquals(AiActionType.DO_NOTHING, disabled.actions().getFirst().type());
        assertEquals(AiActionType.DROP_ITEM, enabled.actions().getFirst().type());
        assertEquals(AiActionType.DO_NOTHING, invalidTarget.actions().getFirst().type());
    }
}
