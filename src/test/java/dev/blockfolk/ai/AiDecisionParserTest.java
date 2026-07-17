package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class AiDecisionParserTest {

    @Test
    void validatesActionsIndependently() {
        AiControlSettings settings = new AiControlSettings("Be Mira", AiMode.REACT,
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
    void modeCeilingOverridesPresetAllowlist() {
        AiControlSettings settings = new AiControlSettings("Be Mira", AiMode.RESPOND,
                EnumSet.of(AiActionType.SAY, AiActionType.START_COMBAT));
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
}
