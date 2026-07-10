package dev.easynpc.model;

import java.util.Objects;

public record CombatProfile(int maxHealth, AggressionLevel aggressionLevel, String shoutout) {
    public static final int HEALTH_STEP = 5;
    public static final int MAX_HEALTH = 1024;

    public CombatProfile {
        maxHealth = Math.max(0, Math.min(MAX_HEALTH, maxHealth));
        aggressionLevel = Objects.requireNonNullElse(aggressionLevel, AggressionLevel.NONE);
        shoutout = shoutout == null || shoutout.isBlank() ? null : shoutout.trim();
    }

    public static CombatProfile disabled() {
        return new CombatProfile(0, AggressionLevel.NONE, null);
    }

    public boolean invulnerable() {
        return maxHealth == 0;
    }

    public CombatProfile withMaxHealth(int maxHealth) {
        return new CombatProfile(maxHealth, aggressionLevel, shoutout);
    }

    public CombatProfile withAggressionLevel(AggressionLevel aggressionLevel) {
        return new CombatProfile(maxHealth, aggressionLevel, shoutout);
    }

    public CombatProfile withShoutout(String shoutout) {
        return new CombatProfile(maxHealth, aggressionLevel, shoutout);
    }
}
