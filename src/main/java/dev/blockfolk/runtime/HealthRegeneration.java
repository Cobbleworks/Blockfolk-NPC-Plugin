package dev.blockfolk.runtime;

/**
 * Out-of-combat healing for damaged NPCs, from {@code health-regeneration} in
 * config.yml. Like a player with a full hunger bar, an NPC that has neither
 * taken damage nor fought for a while slowly heals back to full. The rate
 * scales with maximum health, so every NPC needs the same time to heal
 * completely.
 */
public record HealthRegeneration(boolean enabled, int delayTicks, int secondsToFull) {

    /** Healing is applied once per second. */
    public static final int INTERVAL_TICKS = 20;
    public static final int DEFAULT_DELAY_SECONDS = 10;
    public static final int DEFAULT_SECONDS_TO_FULL = 40;

    public HealthRegeneration {
        delayTicks = Math.max(0, delayTicks);
        secondsToFull = Math.max(1, secondsToFull);
    }

    public static HealthRegeneration defaults() {
        return fromConfig(true, DEFAULT_DELAY_SECONDS, DEFAULT_SECONDS_TO_FULL);
    }

    public static HealthRegeneration fromConfig(boolean enabled, int delaySeconds, int secondsToFull) {
        return new HealthRegeneration(enabled, Math.max(0, delaySeconds) * 20, secondsToFull);
    }

    /**
     * Health restored this interval, or 0 when none should be: regeneration is off,
     * the NPC fought or was hurt too recently, or it is dead or already at full
     * health.
     */
    public double amount(double health, double maxHealth, long ticksSinceCombat) {
        if (!enabled || ticksSinceCombat < delayTicks || !(health > 0.0) || !(maxHealth > 0.0) || health >= maxHealth) {
            return 0.0;
        }
        return Math.min(maxHealth - health, maxHealth / secondsToFull);
    }
}
