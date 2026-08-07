package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class AiDecisionParserTest {

    @Test
    void validatesActionsIndependently() {
        AiControlSettings settings = settings("Be Mira", "Direct but fair", "Guard the gate",
                EnumSet.of(AiActionType.SAY, AiActionType.START_COMBAT));
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
    void skipsMalformedArrayEntriesWithoutDiscardingLaterActions() {
        AiDecision decision = AiDecisionParser.parse("""
                {"actions":["invalid",{"type":"SAY","text":"Still valid."}]}
                """, AiControlSettings.defaults());

        assertEquals(1, decision.actions().size());
        assertEquals("Still valid.", decision.actions().getFirst().text());
    }

    @Test
    void disabledCapabilityOverridesModelResponse() {
        AiControlSettings settings = settings("Be Mira", "", "", EnumSet.of(AiActionType.SAY));
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
        AiControlSettings settings = settings("Guard", "", "Defend this place", EnumSet.of(AiActionType.START_COMBAT));

        AiDecision decision = AiDecisionParser.parse("""
                {"actions":[{"type":"START_COMBAT"}]}
                """, settings);

        assertEquals(AiActionType.START_COMBAT, decision.actions().getFirst().type());
    }

    @Test
    void startCombatAcceptsPerceivedEntitiesRegardlessOfNormalTargetCategories() {
        AiControlSettings settings = settings("Guard", "", "Defend this place", EnumSet.of(AiActionType.START_COMBAT));

        AiDecision entity = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"START_COMBAT\",\"target\":\"nearby_entity_2\"}]}", settings);
        AiDecision npc = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"START_COMBAT\",\"target\":\"nearby_npc_1\"}]}", settings);
        AiDecision arbitrary = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"START_COMBAT\",\"target\":\"some_cow\"}]}", settings);

        assertEquals("nearby_entity_2", entity.actions().getFirst().target());
        assertEquals("nearby_npc_1", npc.actions().getFirst().target());
        assertEquals(AiActionType.DO_NOTHING, arbitrary.actions().getFirst().type());
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
        AiControlSettings settings = settings("Caretaker", "", "Operate the gate",
                EnumSet.of(AiActionType.UNFOLLOW, AiActionType.INTERACT));

        AiDecision decision = AiDecisionParser.parse("""
                {"actions":[{"type":"UNFOLLOW"},{"type":"INTERACT"}]}
                """, settings);

        assertEquals(List.of(AiActionType.UNFOLLOW, AiActionType.INTERACT),
                decision.actions().stream().map(AiDecision.Action::type).toList());
    }

    @Test
    void containerInteractionsRequireTemporaryInventoryAccess() {
        AiControlSettings interact = settings("Storekeeper", "", "Manage supplies", EnumSet.of(AiActionType.INTERACT));
        String take = "{\"actions\":[{\"type\":\"INTERACT\",\"target\":\"take_from_container\"}]}";
        String store = "{\"actions\":[{\"type\":\"INTERACT\",\"target\":\"store_in_container\"}]}";

        assertEquals(AiActionType.DO_NOTHING, AiDecisionParser.parse(take, interact).actions().getFirst().type());
        assertEquals("take_from_container",
                AiDecisionParser.parse(take, interact.withInventoryEnabled(true)).actions().getFirst().target());
        assertEquals("store_in_container",
                AiDecisionParser.parse(store, interact.withInventoryEnabled(true)).actions().getFirst().target());
    }

    @Test
    void acceptsExplicitSwitchAndContainerAliases() {
        AiControlSettings interact = settings("Caretaker", "", "Operate nearby mechanisms",
                EnumSet.of(AiActionType.INTERACT)).withInventoryEnabled(true);

        AiDecision decision = AiDecisionParser.parse("""
                {"actions":[
                  {"type":"INTERACT","target":"nearby_lever_1"},
                  {"type":"INTERACT","target":"nearby_button_2"},
                  {"type":"INTERACT","target":"store_in_container_3"}
                ]}
                """, interact);

        assertEquals(List.of("nearby_lever_1", "nearby_button_2", "store_in_container_3"),
                decision.actions().stream().map(AiDecision.Action::target).toList());
    }

    @Test
    void rejectsUnrecognizedInteractionAliases() {
        AiControlSettings interact = settings("Caretaker", "", "Operate nearby mechanisms",
                EnumSet.of(AiActionType.INTERACT));

        AiDecision decision = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"INTERACT\",\"target\":\"lever_by_door\"}]}", interact);

        assertEquals(AiActionType.DO_NOTHING, decision.actions().getFirst().type());
    }

    @Test
    void followRequiresAndAcceptsPerceivedPlayerTargets() {
        AiControlSettings settings = settings("Companion", "", "Stay with visitors", EnumSet.of(AiActionType.FOLLOW));

        AiDecision missing = AiDecisionParser.parse("{\"actions\":[{\"type\":\"FOLLOW\"}]}", settings);
        AiDecision alias = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"FOLLOW\",\"target\":\"nearby_player_1\"}]}", settings);
        AiDecision playerName = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"FOLLOW\",\"target\":\"VoidValkon\"}]}", settings);

        assertEquals(AiActionType.DO_NOTHING, missing.actions().getFirst().type());
        assertEquals(AiActionType.FOLLOW, alias.actions().getFirst().type());
        assertEquals("nearby_player_1", alias.actions().getFirst().target());
        assertEquals(AiActionType.FOLLOW, playerName.actions().getFirst().type());
        assertEquals("voidvalkon", playerName.actions().getFirst().target());
    }

    @Test
    void preservesLongSpeechForChatChunking() {
        String speech = "x".repeat(500);

        AiDecision decision = AiDecisionParser.parse("{\"actions\":[{\"type\":\"SAY\",\"text\":\"" + speech + "\"}]}",
                AiControlSettings.defaults());

        assertEquals(speech, decision.actions().getFirst().text());
    }

    @Test
    void rememberFactRequiresMemoryToBeEnabled() {
        String response = "{\"actions\":[{\"type\":\"REMEMBER_FACT\",\"text\":\"Alex likes apples\"}]}";

        AiDecision disabled = AiDecisionParser.parse(response, AiControlSettings.defaults());
        AiDecision enabled = AiDecisionParser.parse(response, AiControlSettings.defaults().withMemoryEnabled(true));

        assertEquals(AiActionType.DO_NOTHING, disabled.actions().getFirst().type());
        assertEquals(AiActionType.REMEMBER_FACT, enabled.actions().getFirst().type());
        assertEquals("Alex likes apples", enabled.actions().getFirst().text());
    }

    @Test
    void moveToAcceptsOnlyPerceivedTargetAliasesWhenEnabled() {
        AiControlSettings settings = settings("Guide", "", "Show visitors around", EnumSet.of(AiActionType.MOVE_TO));

        AiDecision accepted = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"MOVE_TO\",\"target\":\"nearby_location_2\"}]}", settings);
        AiDecision arbitrary = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"MOVE_TO\",\"target\":\"world_spawn\"}]}", settings);

        assertEquals(AiActionType.MOVE_TO, accepted.actions().getFirst().type());
        assertEquals("nearby_location_2", accepted.actions().getFirst().target());
        assertEquals(AiActionType.DO_NOTHING, arbitrary.actions().getFirst().type());
    }

    @Test
    void dropItemRequiresInventoryToggleAndSlotAlias() {
        String response = "{\"actions\":[{\"type\":\"DROP_ITEM\",\"target\":\"inventory_slot_4\"}]}";

        AiDecision disabled = AiDecisionParser.parse(response, AiControlSettings.defaults());
        AiDecision enabled = AiDecisionParser.parse(response, AiControlSettings.defaults().withInventoryEnabled(true));
        AiDecision invalidTarget = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"DROP_ITEM\",\"target\":\"diamond\"}]}",
                AiControlSettings.defaults().withInventoryEnabled(true));

        assertEquals(AiActionType.DO_NOTHING, disabled.actions().getFirst().type());
        assertEquals(AiActionType.DROP_ITEM, enabled.actions().getFirst().type());
        assertEquals(AiActionType.DO_NOTHING, invalidTarget.actions().getFirst().type());
    }

    @Test
    void miningRequiresCapabilityAndAResourceTargetButNotInventory() {
        AiControlSettings enabled = AiControlSettings.defaults().withInventoryEnabled(true)
                .toggle(AiActionType.MINE_BLOCKS);

        AiDecision accepted = AiDecisionParser
                .parse("{\"actions\":[{\"type\":\"MINE_BLOCKS\",\"target\":\"all_ores\"}]}", enabled);
        AiDecision noInventory = AiDecisionParser.parse(
                "{\"actions\":[{\"type\":\"MINE_BLOCKS\",\"target\":\"trees\"}]}", enabled.withInventoryEnabled(false));
        AiDecision noTarget = AiDecisionParser.parse("{\"actions\":[{\"type\":\"MINE_BLOCKS\"}]}", enabled);

        assertEquals(AiActionType.MINE_BLOCKS, accepted.actions().getFirst().type());
        assertEquals("all_ores", accepted.actions().getFirst().target());
        assertEquals(AiActionType.MINE_BLOCKS, noInventory.actions().getFirst().type());
        assertEquals(AiActionType.DO_NOTHING, noTarget.actions().getFirst().type());
    }

    private static AiControlSettings settings(String identity, String behaviour, String goal,
            EnumSet<AiActionType> actions) {
        return new AiControlSettings(identity, behaviour, "", goal, "", actions, true, true, false, false, false);
    }
}
