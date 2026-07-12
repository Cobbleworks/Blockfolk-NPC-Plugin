package dev.blockfolk.model;

public record BehaviourAction(BehaviourActionType type, String value) {

    public BehaviourAction {
        if (type == null) {
            throw new IllegalArgumentException("Behaviour action type is required");
        }
        value = value == null || value.isBlank() ? null : value.trim();
    }
}
