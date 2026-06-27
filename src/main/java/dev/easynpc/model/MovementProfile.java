package dev.easynpc.model;

public record MovementProfile(boolean enabled) {
    public static MovementProfile disabled() {
        return new MovementProfile(false);
    }
}
