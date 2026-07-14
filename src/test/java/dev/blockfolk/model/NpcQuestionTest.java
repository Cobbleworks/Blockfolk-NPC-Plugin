package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NpcQuestionTest {

    @Test
    void createsStableTypedQuestionAction() {
        NpcQuestion question = NpcQuestion.create("Trade with me?");
        BehaviourAction action = BehaviourAction.ask(question);

        assertEquals(BehaviourActionType.ASK_QUESTION, action.type());
        assertEquals(question.id(), action.question().id());
        assertEquals(null, action.value());
        assertNotEquals(NpcQuestion.create("Trade with me?").id(), question.id());
    }

    @Test
    void defensivelyCopiesOptionsAndBranches() {
        List<BehaviourAction> branch = new ArrayList<>();
        branch.add(new BehaviourAction(BehaviourActionType.WAVE, null));
        List<QuestionOption> options = new ArrayList<>();
        options.add(new QuestionOption("Yes", branch));
        NpcQuestion question = new NpcQuestion(UUID.randomUUID(), "Ready?", options, branch);

        branch.clear();
        options.clear();
        assertEquals(1, question.options().size());
        assertEquals(1, question.options().getFirst().actions().size());
        assertEquals(1, question.cancelActions().size());
        assertThrows(UnsupportedOperationException.class, () -> question.options().clear());
    }

    @Test
    void enforcesLimitsUniqueLabelsAndOneLevelNesting() {
        List<QuestionOption> eight = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new QuestionOption("Option " + index, List.of())).toList();
        assertThrows(IllegalArgumentException.class,
                () -> new NpcQuestion(UUID.randomUUID(), "Too many?", eight, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new NpcQuestion(UUID.randomUUID(), "Duplicate?",
                List.of(new QuestionOption("Yes", List.of()), new QuestionOption("YES", List.of())), List.of()));
        BehaviourAction nested = BehaviourAction.ask(NpcQuestion.create("Nested?"));
        assertThrows(IllegalArgumentException.class, () -> new QuestionOption("Continue", List.of(nested)));
        assertThrows(IllegalArgumentException.class, () -> new QuestionOption("Too many",
                java.util.stream.IntStream.range(0, 8)
                        .mapToObj(index -> new BehaviourAction(BehaviourActionType.WAVE, null)).toList()));
    }
}
