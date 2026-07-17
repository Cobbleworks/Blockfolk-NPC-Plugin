package dev.blockfolk.ai;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum AiActionType {
    SAY("Speak", AiMode.RESPOND),
    PLAY_ANIMATION("Play Animation", AiMode.RESPOND),
    START_COMBAT("Start Combat", AiMode.REACT),
    STOP_COMBAT("Stop Combat", AiMode.REACT),
    FLEE_FROM("Flee", AiMode.REACT),
    FOLLOW("Follow", AiMode.REACT),
    RETURN_HOME("Return Home", AiMode.DECIDE),
    START_ROUTE("Start Route", AiMode.DECIDE),
    PAUSE_ROUTE("Pause Route", AiMode.DECIDE),
    DO_NOTHING("Do Nothing", AiMode.RESPOND);

    private final String displayName;
    private final AiMode minimumMode;

    AiActionType(String displayName, AiMode minimumMode) {
        this.displayName = displayName;
        this.minimumMode = minimumMode;
    }

    public String displayName() { return displayName; }
    public boolean permittedBy(AiMode mode) { return mode.ordinal() >= minimumMode.ordinal(); }

    public static AiActionType fromModel(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public static Set<AiActionType> safeDefaults() {
        return EnumSet.of(SAY, PLAY_ANIMATION, DO_NOTHING);
    }
}
