package dev.blockfolk.ai;

/**
 * Server-wide limits for NPC-to-NPC conversations, read from
 * {@code ai-control.npc-conversations} in config.yml.
 *
 * @param maxReplies
 *            response rounds one conversation may trigger before it ends
 * @param maxDurationSeconds
 *            hard time limit for one conversation
 * @param cooldownSeconds
 *            how long two NPCs wait before they can start another
 *            conversation, or greet each other again with On Nearby NPC
 * @param replyDelaySeconds
 *            pause after a line before listeners respond, so players can read
 *            it and lines spoken together are answered together
 * @param hearingRange
 *            blocks within which other NPCs hear a spoken line
 * @param playerRange
 *            a player must be within this many blocks for NPCs to talk
 */
public record NpcConversationSettings(int maxReplies, int maxDurationSeconds, int cooldownSeconds,
        double replyDelaySeconds, double hearingRange, double playerRange) {

    public static final int DEFAULT_MAX_REPLIES = 6;
    public static final int DEFAULT_MAX_DURATION_SECONDS = 120;
    public static final int DEFAULT_COOLDOWN_SECONDS = 120;
    public static final double DEFAULT_REPLY_DELAY_SECONDS = 2.0;
    public static final double DEFAULT_HEARING_RANGE = 12.0;
    public static final double DEFAULT_PLAYER_RANGE = 32.0;

    public NpcConversationSettings {
        maxReplies = Math.max(0, maxReplies);
        maxDurationSeconds = Math.max(1, maxDurationSeconds);
        cooldownSeconds = Math.max(0, cooldownSeconds);
        replyDelaySeconds = finiteAtLeast(replyDelaySeconds, 0.0, DEFAULT_REPLY_DELAY_SECONDS);
        hearingRange = finiteAtLeast(hearingRange, 1.0, DEFAULT_HEARING_RANGE);
        playerRange = finiteAtLeast(playerRange, 1.0, DEFAULT_PLAYER_RANGE);
    }

    public static NpcConversationSettings defaults() {
        return new NpcConversationSettings(DEFAULT_MAX_REPLIES, DEFAULT_MAX_DURATION_SECONDS,
                DEFAULT_COOLDOWN_SECONDS, DEFAULT_REPLY_DELAY_SECONDS, DEFAULT_HEARING_RANGE, DEFAULT_PLAYER_RANGE);
    }

    /** A cap of zero turns NPC-to-NPC replies off entirely. */
    public boolean enabled() {
        return maxReplies > 0;
    }

    public long replyDelayTicks() {
        return Math.max(1L, Math.round(replyDelaySeconds * 20.0));
    }

    private static double finiteAtLeast(double value, double minimum, double fallback) {
        return Double.isFinite(value) ? Math.max(minimum, value) : fallback;
    }
}
