package dev.blockfolk.model;

import java.util.Objects;

public record CombatProfile(int maxHealth, int respawnSeconds, AttackReaction attackReaction, boolean targetMobs,
        boolean targetAnimals, boolean targetPlayers, boolean targetNpcs, String alliance, boolean showBossBar,
        int droppedExperience) {

    public static final int HEALTH_STEP = 5;
    public static final int RESPAWN_STEP_SECONDS = 10;
    public static final int EXPERIENCE_STEP = 5;
    public static final int MAX_HEALTH = 1024;

    public CombatProfile {
        maxHealth = Math.max(0, Math.min(MAX_HEALTH, maxHealth));
        respawnSeconds = Math.max(0, respawnSeconds);
        droppedExperience = Math.max(0, droppedExperience);
        attackReaction = Objects.requireNonNullElse(attackReaction, AttackReaction.IGNORE);
        alliance = normalizeOptionalText(alliance);
    }

    public static CombatProfile disabled() {
        return new CombatProfile(0, 0, AttackReaction.IGNORE, false, false, false, false, null, false, 0);
    }

    public boolean invulnerable() {
        return maxHealth == 0;
    }

    public CombatProfile withMaxHealth(int maxHealth) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withRespawnSeconds(int respawnSeconds) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withAttackReaction(AttackReaction attackReaction) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withTargetMobs(boolean targetMobs) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withTargetAnimals(boolean targetAnimals) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withTargetPlayers(boolean targetPlayers) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withTargetNpcs(boolean targetNpcs) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withAlliance(String alliance) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withShowBossBar(boolean showBossBar) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public CombatProfile withDroppedExperience(int droppedExperience) {
        return new CombatProfile(maxHealth, respawnSeconds, attackReaction, targetMobs, targetAnimals, targetPlayers,
                targetNpcs, alliance, showBossBar, droppedExperience);
    }

    public boolean alliedWith(CombatProfile other) {
        return other != null && alliance != null && alliance.equals(other.alliance);
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
