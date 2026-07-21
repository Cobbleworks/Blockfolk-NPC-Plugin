package dev.blockfolk.ai;

import java.util.EnumSet;
import java.util.Set;

public record AiControlSettings(
        String identity,
        String behaviour,
        String likesDislikes,
        String goal,
        String information,
        Set<AiActionType> allowedActions,
        boolean enabled,
        boolean memoryEnabled,
        boolean inventoryEnabled
) {

    public AiControlSettings(String identity, String behaviour, String goal, String information,
            Set<AiActionType> allowedActions, boolean enabled) {
        this(identity, behaviour, "", goal, information, allowedActions, enabled, false, false);
    }

    public AiControlSettings {
        identity = normalize(identity);
        behaviour = normalize(behaviour);
        likesDislikes = normalize(likesDislikes);
        goal = normalize(goal);
        information = normalize(information);
        allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? AiActionType.safeDefaults() : EnumSet.copyOf(allowedActions);
        EnumSet<AiActionType> normalized = EnumSet.copyOf(allowedActions);
        normalized.remove(AiActionType.REMEMBER_FACT);
        normalized.remove(AiActionType.DROP_ITEM);
        normalized.add(AiActionType.DO_NOTHING);
        allowedActions = Set.copyOf(normalized);
    }

    public static AiControlSettings defaults() {
        return new AiControlSettings("", "", "", "", "", AiActionType.safeDefaults(), false, false, false);
    }

    public boolean hasContext() {
        return !identity.isBlank() || !behaviour.isBlank() || !likesDislikes.isBlank()
                || !goal.isBlank() || !information.isBlank();
    }

    public int configuredSectionCount() {
        return (identity.isBlank() ? 0 : 1) + (behaviour.isBlank() ? 0 : 1)
                + (likesDislikes.isBlank() ? 0 : 1) + (goal.isBlank() ? 0 : 1)
                + (information.isBlank() ? 0 : 1);
    }

    public String systemContext() {
        StringBuilder context = new StringBuilder();
        append(context, "Identity", identity);
        append(context, "Personality and behaviour", behaviour);
        append(context, "Likes and dislikes", likesDislikes);
        append(context, "Goal or role", goal);
        append(context, "Knowledge and information", information);
        return context.toString().trim();
    }

    public AiControlSettings withIdentity(String identity) {
        return withContext(identity, behaviour, likesDislikes, goal, information);
    }

    public AiControlSettings withBehaviour(String behaviour) {
        return withContext(identity, behaviour, likesDislikes, goal, information);
    }

    public AiControlSettings withLikesDislikes(String likesDislikes) {
        return withContext(identity, behaviour, likesDislikes, goal, information);
    }

    public AiControlSettings withGoal(String goal) {
        return withContext(identity, behaviour, likesDislikes, goal, information);
    }

    public AiControlSettings withInformation(String information) {
        return withContext(identity, behaviour, likesDislikes, goal, information);
    }

    private AiControlSettings withContext(String identity, String behaviour, String likesDislikes,
            String goal, String information) {
        boolean hadContext = hasContext();
        boolean hasUpdatedContext = !normalize(identity).isBlank() || !normalize(behaviour).isBlank()
                || !normalize(likesDislikes).isBlank() || !normalize(goal).isBlank()
                || !normalize(information).isBlank();
        boolean updatedEnabled = !hasUpdatedContext ? false : !hadContext || enabled;
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions,
                updatedEnabled, memoryEnabled, inventoryEnabled);
    }

    public AiControlSettings toggle(AiActionType action) {
        EnumSet<AiActionType> updated = EnumSet.copyOf(allowedActions);
        if (!updated.remove(action)) updated.add(action);
        updated.add(AiActionType.DO_NOTHING);
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, updated,
                enabled, memoryEnabled, inventoryEnabled);
    }

    public AiControlSettings withActionEnabled(AiActionType action) {
        if (allowedActions.contains(action)) return this;
        EnumSet<AiActionType> updated = EnumSet.copyOf(allowedActions);
        updated.add(action);
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, updated,
                enabled, memoryEnabled, inventoryEnabled);
    }

    public AiControlSettings withEnabled(boolean enabled) {
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions,
                enabled, memoryEnabled, inventoryEnabled);
    }

    public AiControlSettings withMemoryEnabled(boolean memoryEnabled) {
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions,
                enabled, memoryEnabled, inventoryEnabled);
    }

    public AiControlSettings withInventoryEnabled(boolean inventoryEnabled) {
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions,
                enabled, memoryEnabled, inventoryEnabled);
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
