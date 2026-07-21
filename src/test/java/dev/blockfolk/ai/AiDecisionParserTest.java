package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class AiDecisionParserTest {

    @Test
    void validatesActionsIndependently() {
        AiControlSettings settings = new AiControlSettings("Be Mira", "Direct but fair", "Guard the gate", "",
                EnumSet.of(AiActionType.SAY, AiActionType.START_COMBAT), true);
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
                EnumSet.of(AiActionType.SAY), true);
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
                EnumSet.of(AiActionType.START_COMBAT), true);

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
                EnumSet.of(AiActionType.UNFOLLOW, AiActionType.INTERACT), true);

        AiDecision decision = AiDecisionParser.parse("""
                {"actions":[{"type":"UNFOLLOW"},{"type":"INTERACT"}]}
                """, settings);

        assertEquals(List.of(AiActionType.UNFOLLOW, AiActionType.INTERACT),
                decision.actions().stream().map(AiDecision.Action::type).toList());
    }

    @Test
    void followRequiresAndAcceptsPerceivedPlayerTargets() {
        AiControlSettings settings = new AiControlSettings("Companion", "", "Stay with visitors", "",
                EnumSet.of(AiActionType.FOLLOW), true);

        AiDecision missing = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"FOLLOW\"}]}", settings);
        AiDecision alias = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"FOLLOW\",\"target\":\"nearby_player_1\"}]}", settings);
        AiDecision playerName = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"FOLLOW\",\"target\":\"VoidValkon\"}]}", settings);

        assertEquals(AiActionType.DO_NOTHING, missing.actions().getFirst().type());
        assertEquals(AiActionType.FOLLOW, alias.actions().getFirst().type());
        assertEquals("nearby_player_1", alias.actions().getFirst().target());
        assertEquals(AiActionType.FOLLOW, playerName.actions().getFirst().type());
        assertEquals("voidvalkon", playerName.actions().getFirst().target());
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
    void moveToAcceptsPerceivedAliasesAndPlayerNamesWhenEnabled() {
        AiControlSettings settings = new AiControlSettings("Guide", "", "Show visitors around", "",
                EnumSet.of(AiActionType.MOVE_TO), true);

        AiDecision accepted = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"MOVE_TO\",\"target\":\"nearby_location_2\"}]}", settings);
        AiDecision arbitrary = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"MOVE_TO\",\"target\":\"../../bad\"}]}", settings);
        AiDecision playerName = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"MOVE_TO\",\"target\":\"VoidValkon\"}]}", settings);

        assertEquals(AiActionType.MOVE_TO, accepted.actions().getFirst().type());
        assertEquals("nearby_location_2", accepted.actions().getFirst().target());
        assertEquals("voidvalkon", playerName.actions().getFirst().target());
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

    @Test
    void mineBlocksAcceptsOnlyValidatedResourceSelectionsWhenEnabled() {
        AiControlSettings settings = AiControlSettings.defaults().toggle(AiActionType.GATHER_BLOCKS);

        AiDecision selected = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"GATHER_BLOCKS\",\"target\":\"coal,gold\"}]}", settings);
        AiDecision defaultResources = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"GATHER_BLOCKS\"}]}", settings);
        AiDecision arbitrary = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"GATHER_BLOCKS\",\"target\":\"bedrock\"}]}", settings);
        AiDecision partlyArbitrary = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"GATHER_BLOCKS\",\"target\":\"coal,bedrock\"}]}", settings);
        AiDecision wood = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"GATHER_BLOCKS\",\"target\":\"oak,spruce\"}]}", settings);
        AiDecision trees = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"GATHER_BLOCKS\",\"target\":\"all nearby trees\"}]}", settings);

        assertEquals(AiActionType.GATHER_BLOCKS, selected.actions().getFirst().type());
        assertEquals("coal,gold", selected.actions().getFirst().target());
        assertEquals(AiActionType.GATHER_BLOCKS, defaultResources.actions().getFirst().type());
        assertEquals(AiActionType.DO_NOTHING, arbitrary.actions().getFirst().type());
        assertEquals(AiActionType.DO_NOTHING, partlyArbitrary.actions().getFirst().type());
        assertEquals(AiActionType.GATHER_BLOCKS, wood.actions().getFirst().type());
        assertEquals(AiActionType.GATHER_BLOCKS, trees.actions().getFirst().type());
        assertEquals("wood", trees.actions().getFirst().target());
    }
}
