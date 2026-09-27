package dev.blockfolk.ai;

import java.util.List;

public record AiDecision(List<Action> actions) {
    public AiDecision {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public record Action(AiActionType type, String text, String target, String animation, String locationName) {
        public Action(AiActionType type, String text, String target, String animation) {
            this(type, text, target, animation, null);
        }
    }
}
