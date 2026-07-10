package dev.easynpc.model;

public record MovementProfile(boolean enabled, String routeKey) {
    public MovementProfile(boolean enabled) {
        this(enabled, null);
    }

    public MovementProfile {
        routeKey = routeKey == null || routeKey.isBlank() ? null : NpcDefinition.toKey(routeKey);
        enabled = enabled && routeKey != null;
    }

    public static MovementProfile assigned(String routeKey) {
        return new MovementProfile(true, routeKey);
    }

    public static MovementProfile disabled() {
        return new MovementProfile(false, null);
    }
}
