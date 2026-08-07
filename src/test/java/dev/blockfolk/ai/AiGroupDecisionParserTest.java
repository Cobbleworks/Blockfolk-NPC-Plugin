package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

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
    void malformedGroupResponseProducesNoNpcActions() {
        assertEquals(Map.of(),
                AiGroupDecisionParser.parse("not json", Map.of("npc_1", settings(EnumSet.of(AiActionType.SAY)))));
    }

    private static AiControlSettings settings(EnumSet<AiActionType> actions) {
        return new AiControlSettings("Character", "", "", "", "", actions, true, true, false, false, false);
    }
}
