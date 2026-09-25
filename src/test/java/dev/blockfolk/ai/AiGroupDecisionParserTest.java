package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AiGroupDecisionParserTest {

    @Test
    void assignsResponsesToAliasesAndEnforcesCapabilitiesPerNpc() {
        Map<String, AiControlSettings> participants = new LinkedHashMap<>();
        participants.put("npc_1", settings(EnumSet.of(AiActionType.SAY)));
        participants.put("npc_2", settings(EnumSet.of(AiActionType.SAY, AiActionType.START_COMBAT)));

        Map<String, AiDecision> decisions = AiGroupDecisionParser.parse("""
                {"responses":[
                  {"npc":"npc_1","actions":[{"type":"SAY","text":"Hello."},
                    {"type":"START_COMBAT","target":"triggering_player"}]},
                  {"npc":"npc_2","actions":[{"type":"START_COMBAT","target":"triggering_player"}]}
                ]}
                """, participants);

        assertEquals(1, decisions.get("npc_1").actions().size());
        assertEquals(AiActionType.SAY, decisions.get("npc_1").actions().getFirst().type());
        assertEquals(AiActionType.START_COMBAT, decisions.get("npc_2").actions().getFirst().type());
    }

    @Test
    void ignoresUnknownAndDuplicateAliases() {
        Map<String, AiDecision> decisions = AiGroupDecisionParser.parse("""
                {"responses":[
                  {"npc":"npc_1","actions":[{"type":"SAY","text":"First."}]},
                  {"npc":"npc_1","actions":[{"type":"SAY","text":"Duplicate."}]},
                  {"npc":"npc_99","actions":[{"type":"SAY","text":"Unknown."}]}
                ]}
                """, Map.of("npc_1", settings(EnumSet.of(AiActionType.SAY))));

        assertEquals(1, decisions.size());
        assertEquals("First.", decisions.get("npc_1").actions().getFirst().text());
        assertFalse(decisions.containsKey("npc_99"));
    }

    @Test
    void keepsNpcSpeechInModelResponseOrder() {
        Map<String, AiControlSettings> participants = new LinkedHashMap<>();
        participants.put("npc_mira", settings(EnumSet.of(AiActionType.SAY)));
        participants.put("npc_mr_mario", settings(EnumSet.of(AiActionType.SAY)));
        Map<String, AiDecision> decisions = AiGroupDecisionParser.parse("""
                {"responses":[
                  {"npc":"npc_mr_mario","actions":[{"type":"SAY","text":"First"}]},
                  {"npc":"npc_mira","actions":[{"type":"SAY","text":"Second"}]}
                ]}
                """, participants);

        assertEquals(java.util.List.of("npc_mr_mario", "npc_mira"), java.util.List.copyOf(decisions.keySet()));
    }

    @Test
    void malformedGroupResponseProducesNoNpcActions() {
        assertEquals(Map.of(),
                AiGroupDecisionParser.parse("not json", Map.of("npc_1", settings(EnumSet.of(AiActionType.SAY)))));
    }

    @Test
    void missingActionListAndUnknownAliasesAreReportedAsUnusable() {
        Map<String, AiControlSettings> participants = Map.of("npc_1", settings(EnumSet.of(AiActionType.SAY)));
        assertFalse(
                AiGroupDecisionParser.parseDetailed("{\"responses\":[{\"npc\":\"npc_1\"}]}", participants).usable());
        assertFalse(AiGroupDecisionParser
                .parseDetailed("{\"responses\":[{\"npc\":\"npc_99\",\"actions\":[]}]}", participants).usable());
    }

    @Test
    void validatesTargetsAgainstTheRespondingNpcSnapshot() {
        String npcId = "npc_guard_1234567890abcdef";
        String target = "nearby_npc_mr_mario_fedcba9876543210";
        Map<String, AiControlSettings> participants = Map.of(npcId, settings(EnumSet.of(AiActionType.START_COMBAT)));
        String response = "{\"responses\":[{\"npc\":\"" + npcId
                + "\",\"actions\":[{\"type\":\"START_COMBAT\",\"target\":\"" + target + "\"}]}]}";
        AiTargetSnapshot bound = new AiTargetSnapshot(Map.of(), Map.of(target, UUID.randomUUID()), Map.of());

        assertEquals(true, AiGroupDecisionParser.parseDetailed(response, participants, Map.of(npcId, bound)).usable());
        assertFalse(AiGroupDecisionParser.parseDetailed(response, participants,
                Map.of(npcId, new AiTargetSnapshot(Map.of(), Map.of(), Map.of()))).usable());
    }

    @Test
    void rejectsFunctionUnavailableToOneGroupParticipant() {
        String response = """
                {"responses":[
                  {"npc":"npc_1","actions":[{"type":"DROP_ITEM","target":"inventory_slot_1"}]},
                  {"npc":"npc_2","actions":[{"type":"DROP_ITEM","target":"inventory_slot_1"}]}
                ]}
                """;
        Map<String, AiControlSettings> participants = Map.of("npc_1", settings(EnumSet.of(AiActionType.SAY)), "npc_2",
                settings(EnumSet.of(AiActionType.SAY)));
        Map<String, java.util.Set<AiActionType>> available = Map.of("npc_1", EnumSet.of(AiActionType.SAY), "npc_2",
                EnumSet.of(AiActionType.SAY, AiActionType.DROP_ITEM));

        var parsed = AiGroupDecisionParser.parseDetailed(response, participants, null, available);

        assertFalse(parsed.value().containsKey("npc_1"));
        assertEquals(AiActionType.DROP_ITEM, parsed.value().get("npc_2").actions().getFirst().type());
    }

    private static AiControlSettings settings(EnumSet<AiActionType> actions) {
        return new AiControlSettings("Character", "", "", "", "", actions, true, true, false, false);
    }
}
