package dev.blockfolk.ai;

import java.util.EnumSet;
import java.util.Set;

public record AiControlSettings(
        String prompt,
        AiMode mode,
        Set<AiActionType> allowedActions,
        boolean enabled,
        boolean greetOnApproach
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
        this(prompt, mode, allowedActions, !normalizePrompt(prompt).isBlank(), true);
    }

    public static AiControlSettings defaults() {
        return new AiControlSettings("", AiMode.RESPOND, AiActionType.safeDefaults(), false, true);
    }

    public AiControlSettings withPrompt(String prompt) {
        String normalized = normalizePrompt(prompt);
        return new AiControlSettings(normalized, mode, allowedActions,
                !normalized.isBlank(), greetOnApproach);
    }

    public AiControlSettings withMode(AiMode mode) {
        return new AiControlSettings(prompt, mode, allowedActions, enabled, greetOnApproach);
    }

    public AiControlSettings toggle(AiActionType action) {
        EnumSet<AiActionType> updated = allowedActions.isEmpty()
                ? EnumSet.noneOf(AiActionType.class) : EnumSet.copyOf(allowedActions);
        if (!updated.remove(action)) updated.add(action);
        updated.add(AiActionType.DO_NOTHING);
        return new AiControlSettings(prompt, mode, updated, enabled, greetOnApproach);
    }

    public AiControlSettings withEnabled(boolean enabled) {
        return new AiControlSettings(prompt, mode, allowedActions, enabled, greetOnApproach);
    }

    public AiControlSettings withGreetOnApproach(boolean greetOnApproach) {
        return new AiControlSettings(prompt, mode, allowedActions, enabled, greetOnApproach);
    }

    private static String normalizePrompt(String prompt) {
        return prompt == null ? "" : prompt.trim();
    }
}
