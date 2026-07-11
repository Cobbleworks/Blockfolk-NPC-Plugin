package dev.easynpc.model;

import java.util.Locale;

public enum BehaviourActionType {
    SEND_DIALOG("Send Dialog", true),
    SHOW_HOLO_DIALOG("Show Holo Dialog", true),
    SET_ROUTE("Set Route", true),
    RUN_CONSOLE_COMMAND("Run Console Command", true),
    START_COMBAT("Start Combat", false),
    START_NAVIGATION("Start Navigation", false),
    STOP_NAVIGATION("Stop Navigation", false),
    SET_WALK_SPEED("Set Walk Speed", true);

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
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
