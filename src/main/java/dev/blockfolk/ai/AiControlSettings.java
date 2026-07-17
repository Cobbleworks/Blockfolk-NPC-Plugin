package dev.blockfolk.ai;

import java.util.EnumSet;
import java.util.Set;

public record AiControlSettings(
        String identity,
        String behaviour,
        String goal,
        String information,
        Set<AiActionType> allowedActions,
        boolean enabled,
        boolean greetOnApproach,
        boolean respondToChat
) {

    public AiControlSettings {
        identity = normalize(identity);
        behaviour = normalize(behaviour);
        goal = normalize(goal);
        information = normalize(information);
        allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? AiActionType.safeDefaults() : EnumSet.copyOf(allowedActions);
        EnumSet<AiActionType> normalized = EnumSet.copyOf(allowedActions);
        normalized.add(AiActionType.SAY);
        normalized.add(AiActionType.DO_NOTHING);
        allowedActions = Set.copyOf(normalized);
    }

    public static AiControlSettings defaults() {
        return new AiControlSettings("", "", "", "", AiActionType.safeDefaults(), false, true, true);
    }

    public boolean hasContext() {
        return !identity.isBlank() || !behaviour.isBlank() || !goal.isBlank() || !information.isBlank();
    }

    public int configuredSectionCount() {
        return (identity.isBlank() ? 0 : 1) + (behaviour.isBlank() ? 0 : 1)
                + (goal.isBlank() ? 0 : 1) + (information.isBlank() ? 0 : 1);
    }

    public String systemContext() {
        StringBuilder context = new StringBuilder();
        append(context, "Identity", identity);
        append(context, "Personality and behaviour", behaviour);
        append(context, "Goal or role", goal);
        append(context, "Knowledge and information", information);
        return context.toString().trim();
    }

    public AiControlSettings withIdentity(String identity) {
        return withContext(identity, behaviour, goal, information);
    }

    public AiControlSettings withBehaviour(String behaviour) {
        return withContext(identity, behaviour, goal, information);
    }

    public AiControlSettings withGoal(String goal) {
        return withContext(identity, behaviour, goal, information);
    }

    public AiControlSettings withInformation(String information) {
        return withContext(identity, behaviour, goal, information);
    }

    private AiControlSettings withContext(String identity, String behaviour, String goal, String information) {
        boolean hadContext = hasContext();
        boolean hasUpdatedContext = !normalize(identity).isBlank() || !normalize(behaviour).isBlank()
                || !normalize(goal).isBlank() || !normalize(information).isBlank();
        boolean updatedEnabled = !hasUpdatedContext ? false : !hadContext || enabled;
        return new AiControlSettings(identity, behaviour, goal, information, allowedActions,
                updatedEnabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings toggle(AiActionType action) {
        EnumSet<AiActionType> updated = EnumSet.copyOf(allowedActions);
        if (!updated.remove(action)) updated.add(action);
        updated.add(AiActionType.DO_NOTHING);
        return new AiControlSettings(identity, behaviour, goal, information, updated,
                enabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings withEnabled(boolean enabled) {
        return new AiControlSettings(identity, behaviour, goal, information, allowedActions,
                enabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings withGreetOnApproach(boolean greetOnApproach) {
        return new AiControlSettings(identity, behaviour, goal, information, allowedActions,
                enabled, greetOnApproach, respondToChat);
    }

    public AiControlSettings withRespondToChat(boolean respondToChat) {
        return new AiControlSettings(identity, behaviour, goal, information, allowedActions,
                enabled, greetOnApproach, respondToChat);
    }

    private static void append(StringBuilder target, String heading, String value) {
        if (value.isBlank()) return;
        if (!target.isEmpty()) target.append("\n\n");
        target.append(heading).append(":\n").append(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
