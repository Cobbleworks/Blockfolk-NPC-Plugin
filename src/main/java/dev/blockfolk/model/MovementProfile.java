package dev.blockfolk.model;

import java.util.Objects;

public record MovementProfile(boolean enabled, String routeKey, WalkingSpeed walkingSpeed) {

    public MovementProfile(boolean enabled) {
        this(enabled, null, WalkingSpeed.NORMAL);
    }

    public MovementProfile(boolean enabled, String routeKey) {
        this(enabled, routeKey, WalkingSpeed.NORMAL);
    }

    public MovementProfile {
        routeKey = routeKey == null || routeKey.isBlank() ? null : NpcRoute.normalizeKey(routeKey);
        enabled = enabled && routeKey != null;
        walkingSpeed = Objects.requireNonNullElse(walkingSpeed, WalkingSpeed.NORMAL);
    }

    public static MovementProfile assigned(String routeKey) {
        return new MovementProfile(true, routeKey, WalkingSpeed.NORMAL);
    }

    public static MovementProfile disabled() {
        return new MovementProfile(false, null, WalkingSpeed.NORMAL);
    }

    public MovementProfile withRoute(String routeKey) {
        return new MovementProfile(true, routeKey, walkingSpeed);
    }

    public MovementProfile withoutRoute() {
        return new MovementProfile(false, null, walkingSpeed);
    }

    public MovementProfile withWalkingSpeed(WalkingSpeed walkingSpeed) {
        return new MovementProfile(enabled, routeKey, walkingSpeed);
    }
}
