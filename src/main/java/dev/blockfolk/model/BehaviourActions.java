package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Helpers that walk action lists including nested question branches. */
public final class BehaviourActions {

    private BehaviourActions() {
    }

    /**
     * Applies {@code mapper} to every non-question action, descending into the
     * answer and cancel branches of Ask Question actions.
     */
    public static List<BehaviourAction> map(List<BehaviourAction> actions, UnaryOperator<BehaviourAction> mapper) {
        List<BehaviourAction> result = new ArrayList<>(actions.size());
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.ASK_QUESTION && action.question() != null) {
                NpcQuestion question = action.question();
                List<QuestionOption> options = question.options().stream()
                        .map(option -> option.withActions(map(option.actions(), mapper))).toList();
                result.add(BehaviourAction.ask(new NpcQuestion(question.id(), question.prompt(), options,
                        map(question.cancelActions(), mapper))));
            } else {
                result.add(mapper.apply(action));
            }
        }
        return result;
    }

    /**
     * Visits every non-question action, descending into question branches.
     */
    public static void forEach(List<BehaviourAction> actions, Consumer<BehaviourAction> visitor) {
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.ASK_QUESTION && action.question() != null) {
                action.question().options().forEach(option -> forEach(option.actions(), visitor));
                forEach(action.question().cancelActions(), visitor);
            } else {
                visitor.accept(action);
            }
        }
    }
}
