package dev.blockfolk.model;

import java.util.List;

public record QuestionOption(String label, List<BehaviourAction> actions) {

    public QuestionOption {
        label = label == null ? "" : label.trim();
        if (label.isBlank()) {
            throw new IllegalArgumentException("Answer label is required");
        }
        actions = NpcQuestion.validateBranch(actions);
    }

    public QuestionOption withLabel(String label) {
        return new QuestionOption(label, actions);
    }

    public QuestionOption withActions(List<BehaviourAction> actions) {
        return new QuestionOption(label, actions);
    }
}
