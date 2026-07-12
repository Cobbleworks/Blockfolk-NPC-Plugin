package dev.blockfolk.model;

import java.util.Locale;

public enum BehaviourActionType {
    SEND_DIALOG("Send Dialog", true),
    SHOW_HOLO_DIALOG("Show Holo Dialog", true),
    SET_ROUTE("Set Route", true),
    RUN_CONSOLE_COMMAND("Run Console Command", true),
    START_COMBAT("Start Combat", false),
    START_NAVIGATION("Start Navigation", false),
    STOP_NAVIGATION("Stop Navigation", false),
    SET_WALK_SPEED("Set Walk Speed", true),
    MOVE_TO("Move To", true),
    TELEPORT_TO("Teleport To", true),
    WAIT("Wait", true),
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
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        // Sitting was briefly exposed before mannequin pose validation was
        // added. Treat stored actions as Standing instead of dropping them.
        return valueOf(normalized.equals("SIT") ? "STAND" : normalized);
    }
}
