package dev.blockfolk.ai;

import java.util.EnumSet;
import java.util.Set;

public record AiControlSettings(String prompt, AiMode mode, Set<AiActionType> allowedActions) {

    public AiControlSettings {
        prompt = prompt == null ? "" : prompt.trim();
        mode = mode == null ? AiMode.RESPOND : mode;
        allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? AiActionType.safeDefaults() : EnumSet.copyOf(allowedActions);
        allowedActions = Set.copyOf(allowedActions);
    }

    public static AiControlSettings defaults() {
        return new AiControlSettings("", AiMode.RESPOND, AiActionType.safeDefaults());
    }

    public AiControlSettings withPrompt(String prompt) {
        return new AiControlSettings(prompt, mode, allowedActions);
    }

    public AiControlSettings withMode(AiMode mode) {
        return new AiControlSettings(prompt, mode, allowedActions);
    }

    public AiControlSettings toggle(AiActionType action) {
        EnumSet<AiActionType> updated = allowedActions.isEmpty()
                ? EnumSet.noneOf(AiActionType.class) : EnumSet.copyOf(allowedActions);
        if (!updated.remove(action)) updated.add(action);
        updated.add(AiActionType.DO_NOTHING);
        return new AiControlSettings(prompt, mode, updated);
    }
}
