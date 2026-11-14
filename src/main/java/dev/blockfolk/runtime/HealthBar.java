package dev.blockfolk.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The segmented health bar floating above a damaged NPC: {@code [■■■■□□□□□□]}.
 */
public final class HealthBar {

    static final int SEGMENTS = 10;
    /** Returned by {@link #filledSegments} when no bar should be shown. */
    static final int HIDDEN = -1;
    public static final double DEFAULT_VIEW_DISTANCE = 16.0;

    private static final TextColor FILLED_COLOR = TextColor.color(0xC8323C);

    /** Global settings from {@code health-bars} in config.yml. */
    public record Settings(boolean enabled, double viewDistance) {
    }

    private HealthBar() {
    }

    /**
     * Filled segments for the given health, or {@link #HIDDEN} at full health or
     * when dead. A damaged NPC never shows a full bar and a living one never shows
     * an empty bar.
     */
    static int filledSegments(double health, double maxHealth) {
        if (!(maxHealth > 0.0) || !(health > 0.0) || health >= maxHealth)
            return HIDDEN;
        int filled = (int) Math.round(health / maxHealth * SEGMENTS);
        return Math.max(1, Math.min(SEGMENTS - 1, filled));
    }

    static Component text(int filled) {
        return Component.text().append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("■".repeat(filled), FILLED_COLOR))
                .append(Component.text("□".repeat(SEGMENTS - filled), NamedTextColor.GRAY))
                .append(Component.text("]", NamedTextColor.DARK_GRAY)).build();
    }
}
