package dev.easynpc.model;

import java.util.Locale;

public enum BehaviourEvent {
    COMBAT_ENTERED("On Combat Entered"),
    LEFT_CLICK("On Left-Click"),
    RIGHT_CLICK("On Right-Click"),
    DEATH("On Death"),
    SPAWN("On Spawn"),
    DAMAGE_TAKEN("On Damage Taken"),
    LOW_HEALTH("On Low Health — 25% HP");

    private final String displayName;

    BehaviourEvent(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }

    public static BehaviourEvent fromStored(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
