package dev.blockfolk.model;

import java.util.Locale;

public enum BehaviourEvent {
    SPAWN("On Spawn"),
    IDLE("On Idle"),
    LEFT_CLICK("On Left-Click"),
    RIGHT_CLICK("On Right-Click"),
    PLAYER_APPROACH("On Player Approach"),
    PLAYER_LEAVES("On Player Leaves"),
    DEATH("On Death"),
    DAMAGE_TAKEN("On Damage Taken"),
    LOW_HEALTH("On Low Health — 25% HP"),
    HEAL("On Heal — 2+ Hearts"),
    DAWN("At Dawn"),
    MORNING("In the Morning"),
    COMBAT_ENTERED("On Combat Entered"),
    COMBAT_EXITED("On Combat Exited");

    private final String displayName;

    BehaviourEvent(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }

    public static BehaviourEvent fromStored(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
