package dev.blockfolk.ai;

import java.util.EnumSet;
import java.util.Set;

public record AiControlSettings(String identity, String behaviour, String likesDislikes, String goal,
        String information, Set<AiActionType> allowedActions, boolean enabled, boolean respondToChat,
        boolean memoryEnabled, boolean inventoryEnabled, boolean sharedConversation) {

    public AiControlSettings {
        identity = normalize(identity);
        behaviour = normalize(behaviour);
        likesDislikes = normalize(likesDislikes);
        goal = normalize(goal);
        information = normalize(information);
        allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? AiActionType.safeDefaults()
                : EnumSet.copyOf(allowedActions);
        EnumSet<AiActionType> normalized = EnumSet.copyOf(allowedActions);
        normalized.remove(AiActionType.REMEMBER_FACT);
        normalized.remove(AiActionType.DROP_ITEM);
        normalized.add(AiActionType.SAY);
        normalized.add(AiActionType.DO_NOTHING);
        allowedActions = Set.copyOf(normalized);
    }

    public static AiControlSettings defaults() {
        return new AiControlSettings("", "", "", "", "", AiActionType.safeDefaults(), false, true, false, false, false);
    }

    public boolean hasContext() {
        return !identity.isBlank() || !behaviour.isBlank() || !likesDislikes.isBlank() || !goal.isBlank()
                || !information.isBlank();
    }

    public int configuredSectionCount() {
        return (identity.isBlank() ? 0 : 1) + (behaviour.isBlank() ? 0 : 1) + (likesDislikes.isBlank() ? 0 : 1)
                + (goal.isBlank() ? 0 : 1) + (information.isBlank() ? 0 : 1);
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

    private AiControlSettings withContext(String identity, String behaviour, String likesDislikes, String goal,
            String information) {
        boolean hadContext = hasContext();
        boolean hasUpdatedContext = !normalize(identity).isBlank() || !normalize(behaviour).isBlank()
                || !normalize(likesDislikes).isBlank() || !normalize(goal).isBlank()
                || !normalize(information).isBlank();
        boolean updatedEnabled = !hasUpdatedContext ? false : !hadContext || enabled;
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions,
                updatedEnabled, respondToChat, memoryEnabled, inventoryEnabled, sharedConversation);
    }

    public AiControlSettings toggle(AiActionType action) {
        EnumSet<AiActionType> updated = EnumSet.copyOf(allowedActions);
        if (!updated.remove(action))
            updated.add(action);
        updated.add(AiActionType.DO_NOTHING);
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, updated, enabled,
                respondToChat, memoryEnabled, inventoryEnabled, sharedConversation);
    }

    public AiControlSettings withEnabled(boolean enabled) {
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions, enabled,
                respondToChat, memoryEnabled, inventoryEnabled, sharedConversation);
    }

    public AiControlSettings withRespondToChat(boolean respondToChat) {
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions, enabled,
                respondToChat, memoryEnabled, inventoryEnabled, sharedConversation);
    }

    public AiControlSettings withMemoryEnabled(boolean memoryEnabled) {
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions, enabled,
                respondToChat, memoryEnabled, inventoryEnabled, sharedConversation);
    }

    public AiControlSettings withInventoryEnabled(boolean inventoryEnabled) {
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions, enabled,
                respondToChat, memoryEnabled, inventoryEnabled, sharedConversation);
    }

    public AiControlSettings withSharedConversation(boolean sharedConversation) {
        return new AiControlSettings(identity, behaviour, likesDislikes, goal, information, allowedActions, enabled,
                respondToChat, memoryEnabled, inventoryEnabled, sharedConversation);
    }

    private static void append(StringBuilder target, String heading, String value) {
        if (value.isBlank())
            return;
        if (!target.isEmpty())
            target.append("\n\n");
        target.append(heading).append(":\n").append(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
