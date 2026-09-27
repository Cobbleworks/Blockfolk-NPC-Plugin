package dev.blockfolk.model;

public record AiMemory(String fact, Importance importance) {
    public enum Importance {
        CORE, MAJOR, MINOR
    }
}
