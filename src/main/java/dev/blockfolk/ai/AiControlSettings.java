package dev.blockfolk.ai;

import java.util.EnumSet;
import java.util.Set;

public record AiControlSettings(
        String prompt,
        AiMode mode,
        Set<AiActionType> allowedActions,
        boolean enabled,
        boolean greetOnApproach,
        boolean respondToChat
) {

    public AiControlSettings {
        prompt = prompt == null ? "" : prompt.trim();
        mode = mode == null ? AiMode.RESPOND : mode;
        allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? AiActionType.safeDefaults() : EnumSet.copyOf(allowedActions);
        EnumSet<AiActionType> normalized = EnumSet.copyOf(allowedActions);
        normalized.add(AiActionType.SAY);
        normalized.add(AiActionType.DO_NOTHING);
        allowedActions = Set.copyOf(normalized);
    }

    public AiControlSettings(String prompt, AiMode mode, Set<AiActionType> allowedActions) {
        this(prompt, mode, allowedActions, !normalizePrompt(prompt).isBlank(), true, true);
    }

    public static AiControlSettings defaults() {
        return new AiControlSettings("", AiMode.RESPOND, AiActionType.safeDefaults(), false, true, true);
    }

    public AiControlSettings withPrompt(String prompt) {
        String normalized = normalizePrompt(prompt);
        boolean updatedEnabled = normalized.isBlank() ? false : this.prompt.isBlank() ? true : enabled;
        return new AiControlSettings(normalized, mode, allowedActions,
                updatedEnabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings withMode(AiMode mode) {
        return new AiControlSettings(prompt, mode, allowedActions, enabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings toggle(AiActionType action) {
        EnumSet<AiActionType> updated = allowedActions.isEmpty()
                ? EnumSet.noneOf(AiActionType.class) : EnumSet.copyOf(allowedActions);
        if (!updated.remove(action)) updated.add(action);
        updated.add(AiActionType.DO_NOTHING);
        return new AiControlSettings(prompt, mode, updated, enabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings withEnabled(boolean enabled) {
        return new AiControlSettings(prompt, mode, allowedActions, enabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings withGreetOnApproach(boolean greetOnApproach) {
        return new AiControlSettings(prompt, mode, allowedActions, enabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings withRespondToChat(boolean respondToChat) {
        return new AiControlSettings(prompt, mode, allowedActions, enabled, greetOnApproach, respondToChat);
    }

    private static String normalizePrompt(String prompt) {
        return prompt == null ? "" : prompt.trim();
    }
}
