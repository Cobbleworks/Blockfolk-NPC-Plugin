package dev.blockfolk.ai;

import java.util.List;

public record AiDecision(List<Action> actions) {
    public AiDecision {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /**
     * @param item
     *            for container interactions, the material id to move (such as
     *            {@code oak_log}); null moves any item
     * @param amount
     *            for container interactions, how many of {@code item} to move;
     *            null moves all of it
     */
    public record Action(AiActionType type, String text, String target, String animation, String locationName,
            String item, Integer amount) {
        public Action(AiActionType type, String text, String target, String animation, String locationName) {
            this(type, text, target, animation, locationName, null, null);
        }

        public Action(AiActionType type, String text, String target, String animation) {
            this(type, text, target, animation, null, null, null);
        }
    }
}
