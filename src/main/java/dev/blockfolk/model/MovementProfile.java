package dev.blockfolk.model;

import java.util.Objects;

public record MovementProfile(boolean enabled, String routeKey, WalkingSpeed walkingSpeed) {

    public MovementProfile {
        routeKey = routeKey == null || routeKey.isBlank() ? null : NpcRoute.normalizeKey(routeKey);
        enabled = enabled && routeKey != null;
        walkingSpeed = Objects.requireNonNullElse(walkingSpeed, WalkingSpeed.NORMAL);
    }

    public static MovementProfile disabled() {
        return new MovementProfile(false, null, WalkingSpeed.NORMAL);
    }

    public static MovementProfile routing(String routeKey, WalkingSpeed walkingSpeed) {
        return new MovementProfile(true, routeKey, walkingSpeed);
    }

    public MovementProfile withWalkingSpeed(WalkingSpeed walkingSpeed) {
        return new MovementProfile(enabled, routeKey, walkingSpeed);
    }
}
