package dev.blockfolk.ai;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum AiActionType {
    SAY("Speak"),
    PLAY_ANIMATION("Play Animation"),
    START_COMBAT("Start Combat"),
    STOP_COMBAT("Stop Combat"),
    FLEE_FROM("Flee"),
    FOLLOW("Follow"),
    UNFOLLOW("Unfollow"),
    INTERACT("Interact"),
    MOVE_TO("Move To"),
    RETURN_HOME("Return Home"),
    START_ROUTE("Start Route"),
    PAUSE_ROUTE("Pause Route"),
    REMEMBER_FACT("Remember Fact"),
    DROP_ITEM("Drop Item"),
    MINE_BLOCKS("Mine Resources"),
    DO_NOTHING("Do Nothing");

    private final String displayName;

    AiActionType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    public static AiActionType fromModel(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public static Set<AiActionType> safeDefaults() {
        return EnumSet.of(SAY, PLAY_ANIMATION, DO_NOTHING);
    }
}
