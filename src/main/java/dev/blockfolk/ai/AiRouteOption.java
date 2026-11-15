package dev.blockfolk.ai;

/** One route assigned to (owned by) an NPC, as offered to its AI. */
public record AiRouteOption(String key, String displayName, int waypoints) {
}
