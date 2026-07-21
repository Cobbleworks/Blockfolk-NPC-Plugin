package dev.blockfolk.model;

import java.util.Locale;

public enum BehaviourActionType {
    TRIGGER_AI("Trigger AI", false),
    SEND_DIALOG("Send Dialog", true),
    SHOW_HOLO_DIALOG("Show Holo Dialog", true),
    ASK_QUESTION("Ask Question", false),
    SET_ROUTE("Set Route", true),
    RUN_CONSOLE_COMMAND("Run Console Command", true),
    START_COMBAT("Start Combat", false),
    CHANGE_FIGHT_OPTIONS("Change Fight Options", true),
    START_NAVIGATION("Start Navigation", false),
    STOP_NAVIGATION("Stop Navigation", false),
    SET_WALK_SPEED("Set Walk Speed", true),
    MOVE_TO("Move To", true),
    TELEPORT_TO("Teleport To", true),
    WAIT("Wait", true),
    INTERACT("Interact", false),
    GATHER_BLOCKS("Gather Resources", true),
    TAKE_ITEM("Take Item", false),
    SHOW_INVENTORY("Show Inventory", false),
    DROP_INVENTORY("Drop Inventory", false),
    HARVEST("Harvest", false),
    EMIT_EVENT("Emit Custom Event", true),
    SLEEP("Sleeping", false),
    SWIM("Swimming", false),
    FALL_FLY("Fall Flying", false),
    STAND("Standing", false),
    SNEAK("Sneaking", false),
    WAVE("Wave", false),
    JUMP("Jump", false),
    FOLLOW("Follow", false),
    UNFOLLOW("Unfollow", false);

    private final String displayName;
    private final boolean requiresValue;

    BehaviourActionType(String displayName, boolean requiresValue) {
        this.displayName = displayName;
        this.requiresValue = requiresValue;
    }

    public String displayName() {
        return displayName;
    }

    public boolean requiresValue() {
        return requiresValue;
    }

    public static BehaviourActionType fromStored(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("AI_CONTROL")) return TRIGGER_AI;
        if (normalized.equals("MINE_BLOCKS")) return GATHER_BLOCKS;
        return valueOf(normalized);
    }
}
