package dev.blockfolk.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record NpcQuestion(UUID id, String prompt, List<QuestionOption> options,
        List<BehaviourAction> cancelActions) {

    public static final int MAX_OPTIONS = 4;
    public static final int MAX_BRANCH_ACTIONS = 7;

    public NpcQuestion {
        id = Objects.requireNonNull(id, "id");
        prompt = prompt == null ? "" : prompt.trim();
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("Question prompt is required");
        }
        options = options == null ? List.of() : List.copyOf(options);
        if (options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("A question may have at most four answers");
        }
        HashSet<String> labels = new HashSet<>();
        for (QuestionOption option : options) {
            Objects.requireNonNull(option, "option");
            if (!labels.add(option.label().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Answer labels must be unique");
            }
        }
        cancelActions = validateBranch(cancelActions);
    }

    public static NpcQuestion create(String prompt) {
        return new NpcQuestion(UUID.randomUUID(), prompt, List.of(), List.of());
    }

    static List<BehaviourAction> validateBranch(List<BehaviourAction> actions) {
        List<BehaviourAction> copy = actions == null ? List.of() : List.copyOf(actions);
        if (copy.size() > MAX_BRANCH_ACTIONS) {
            throw new IllegalArgumentException("A question branch may have at most seven actions");
        }
        if (copy.stream().anyMatch(action -> action.type() == BehaviourActionType.ASK_QUESTION)) {
            throw new IllegalArgumentException("Question branches cannot contain another question");
        }
        return copy;
    }

    public NpcQuestion withPrompt(String prompt) {
        return new NpcQuestion(id, prompt, options, cancelActions);
    }

    public NpcQuestion withOptions(List<QuestionOption> options) {
        return new NpcQuestion(id, prompt, options, cancelActions);
    }

    public NpcQuestion withCancelActions(List<BehaviourAction> actions) {
        return new NpcQuestion(id, prompt, options, actions);
    }
}
