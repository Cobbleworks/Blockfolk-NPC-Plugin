package dev.easynpc.model;

import java.util.Objects;

public record CombatProfile(int maxHealth, int respawnSeconds, AggressionLevel aggressionLevel, String shoutout) {

    public static final int HEALTH_STEP = 5;
    public static final int RESPAWN_STEP_SECONDS = 10;
    public static final int MAX_HEALTH = 1024;

    public CombatProfile {
        maxHealth = Math.max(0, Math.min(MAX_HEALTH, maxHealth));
        respawnSeconds = Math.max(0, respawnSeconds);
        aggressionLevel = Objects.requireNonNullElse(aggressionLevel, AggressionLevel.NONE);
        shoutout = shoutout == null || shoutout.isBlank() ? null : shoutout.trim();
    }

    public static CombatProfile disabled() {
        return new CombatProfile(0, 0, AggressionLevel.NONE, null);
    }

    public boolean invulnerable() {
        return maxHealth == 0;
    }

    public CombatProfile withMaxHealth(int maxHealth) {
        return new CombatProfile(maxHealth, respawnSeconds, aggressionLevel, shoutout);
    }

    public CombatProfile withRespawnSeconds(int respawnSeconds) {
        return new CombatProfile(maxHealth, respawnSeconds, aggressionLevel, shoutout);
    }

    public CombatProfile withAggressionLevel(AggressionLevel aggressionLevel) {
        return new CombatProfile(maxHealth, respawnSeconds, aggressionLevel, shoutout);
    }

    public CombatProfile withShoutout(String shoutout) {
        return new CombatProfile(maxHealth, respawnSeconds, aggressionLevel, shoutout);
    }
}
