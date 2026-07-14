package dev.blockfolk.model;

public record BehaviourAction(BehaviourActionType type, String value, NpcQuestion question) {

    public BehaviourAction(BehaviourActionType type, String value) {
        this(type, value, null);
    }

    public static BehaviourAction ask(NpcQuestion question) {
        return new BehaviourAction(BehaviourActionType.ASK_QUESTION, null, question);
    }

    public BehaviourAction {
        if (type == null) {
            throw new IllegalArgumentException("Behaviour action type is required");
        }
        value = value == null || value.isBlank() ? null : value.trim();
        if (type == BehaviourActionType.ASK_QUESTION && question == null) {
            throw new IllegalArgumentException("Ask Question requires question data");
        }
        if (type != BehaviourActionType.ASK_QUESTION && question != null) {
            throw new IllegalArgumentException("Only Ask Question may contain question data");
        }
        if (type == BehaviourActionType.ASK_QUESTION) {
            value = null;
        }
    }
}
