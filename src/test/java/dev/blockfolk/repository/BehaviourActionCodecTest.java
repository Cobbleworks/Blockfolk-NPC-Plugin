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
    void decodesLegacyFlatActionMap() {
        BehaviourAction decoded = BehaviourActionCodec.decode(Map.of(
                "type", "send-dialog",
                "value", "Legacy line"));

        assertEquals(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Legacy line"), decoded);
    }
}
