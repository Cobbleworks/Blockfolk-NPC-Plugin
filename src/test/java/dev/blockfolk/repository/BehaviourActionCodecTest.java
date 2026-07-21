package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.NpcQuestion;
import dev.blockfolk.model.QuestionOption;

class BehaviourActionCodecTest {

    @Test
    void roundTripsNestedQuestionActions() {
        NpcQuestion question = new NpcQuestion(UUID.randomUUID(), "Choose",
                List.of(new QuestionOption("First", List.of(
                        new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Selected"),
                        new BehaviourAction(BehaviourActionType.WAVE, null)))),
                List.of(new BehaviourAction(BehaviourActionType.RUN_CONSOLE_COMMAND, "say cancelled")));
        BehaviourAction original = BehaviourAction.ask(question);

        BehaviourAction decoded = BehaviourActionCodec.decode(BehaviourActionCodec.encode(original));

        assertEquals(original, decoded);
    }

    @Test
    void decodesActionWithValue() {
        BehaviourAction decoded = BehaviourActionCodec.decode(Map.of(
                "type", "send_dialog",
                "value", "Stored line"));

        assertEquals(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Stored line"), decoded);
    }

    @Test
    void roundTripsGatherBlockSelections() {
        BehaviourAction original = new BehaviourAction(
                BehaviourActionType.GATHER_BLOCKS, "coal,gold,oak");

        BehaviourAction decoded = BehaviourActionCodec.decode(BehaviourActionCodec.encode(original));

        assertEquals(original, decoded);
    }

    @Test
    void migratesLegacyAiControlAction() {
        BehaviourAction decoded = BehaviourActionCodec.decode(Map.of("type", "ai_control"));

        assertEquals(new BehaviourAction(BehaviourActionType.TRIGGER_AI, null), decoded);
    }

    @Test
    void limitsQuestionsToFourAnswers() {
        List<Map<String, Object>> options = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> Map.of("label", "Option " + index, "actions", List.of()))
                .toList();

        BehaviourAction decoded = BehaviourActionCodec.decode(Map.of(
                "type", "ask_question",
                "question", Map.of("prompt", "Choose", "options", options)));

        assertEquals(4, decoded.question().options().size());
        assertEquals("Option 3", decoded.question().options().getLast().label());
    }
}
