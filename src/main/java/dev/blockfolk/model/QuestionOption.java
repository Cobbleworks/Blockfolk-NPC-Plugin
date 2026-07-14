package dev.blockfolk.model;

import java.util.List;

public record QuestionOption(String label, List<BehaviourAction> actions) {

    public QuestionOption {
        label = label == null ? "" : label.trim();
        actions = NpcQuestion.validateBranch(actions);
        if (label.isBlank() && !actions.isEmpty()) {
            throw new IllegalArgumentException("An empty answer cannot have actions");
        }
    }

    public static QuestionOption empty() {
        return new QuestionOption("", List.of());
    }

    public boolean configured() {
        return !label.isBlank();
    }

    public QuestionOption withLabel(String label) {
        return new QuestionOption(label, actions);
    }

    public QuestionOption withActions(List<BehaviourAction> actions) {
        return new QuestionOption(label, actions);
    }
}
