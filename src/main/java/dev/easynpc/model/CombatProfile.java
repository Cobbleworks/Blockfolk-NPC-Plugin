package dev.easynpc.model;

public record CombatProfile(boolean enabled) {
    public static CombatProfile disabled() {
        return new CombatProfile(false);
    }
}
