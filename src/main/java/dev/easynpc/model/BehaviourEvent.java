package dev.easynpc.model;

import java.util.Locale;

public enum BehaviourEvent {
    COMBAT_ENTERED("On Combat Entered"),
    COMBAT_EXITED("On Combat Exited"),
    PLAYER_APPROACH("On Player Approach"),
    PLAYER_LEAVES("On Player Leaves"),
    LEFT_CLICK("On Left-Click"),
    RIGHT_CLICK("On Right-Click"),
    DEATH("On Death"),
    SPAWN("On Spawn"),
    DAMAGE_TAKEN("On Damage Taken"),
    HEAL("On Heal — 2+ Hearts"),
    LOW_HEALTH("On Low Health — 25% HP");

    private final String displayName;

    BehaviourEvent(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }

    public static BehaviourEvent fromStored(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
