package dev.blockfolk.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Words a change to an NPC's temporary inventory for its AI: what moved, how
 * much, and how.
 */
public final class InventoryChange {

    /** How items entered or left the inventory. */
    public enum Cause {
        /** Picked up an item lying on the ground. */
        PICKED_UP,
        /** A player edited the inventory directly. */
        EDITED_BY_PLAYER,
        /** Collected the drops of blocks the NPC mined. */
        MINED,
        /** Took items from a container or from someone's inventory. */
        TAKEN,
        /** Put items into a container. */
        STORED,
        /** Dropped items on the ground. */
        DROPPED,
        /** Emptied the inventory into nearby containers, dropping what did not fit. */
        EMPTIED,
        /** Harvested crops and planted seeds. */
        FARMED,
        /** Anything else. */
        OTHER
    }

    /** An item kind and the amount that moved. */
    public record Moved(String name, int amount) {
    }

    private InventoryChange() {
    }

    /**
     * Describes items that entered the inventory; {@code source} names who or what
     * they came from and may be null.
     */
    public static String describeReceived(Cause cause, String source, List<Moved> items) {
        String list = join(items);
        if (list.isEmpty())
            return null;
        return switch (cause) {
            case PICKED_UP -> "You picked up " + list + " from the ground.";
            case EDITED_BY_PLAYER -> (source == null ? "Someone" : source) + " put " + list + " into your inventory.";
            case MINED -> "You mined and collected " + list + ".";
            case TAKEN -> "You took " + list + (source == null ? "" : " from " + source) + ".";
            case FARMED -> "You harvested " + list + ".";
            case STORED, DROPPED, EMPTIED, OTHER ->
                "You received " + list + (source == null ? "" : " from " + source) + ".";
        };
    }

    /**
     * Describes items that left the inventory; {@code destination} names who or
     * what they went to and may be null.
     */
    public static String describeLost(Cause cause, String destination, List<Moved> items) {
        String list = join(items);
        if (list.isEmpty())
            return null;
        return switch (cause) {
            case EDITED_BY_PLAYER ->
                (destination == null ? "Someone" : destination) + " took " + list + " out of your inventory.";
            case STORED -> "You stored " + list + (destination == null ? "" : " in " + destination) + ".";
            case DROPPED -> "You dropped " + list + " on the ground.";
            case EMPTIED ->
                "You emptied your inventory into nearby containers, dropping what did not fit: " + list + ".";
            case FARMED -> "You planted " + list + ".";
            case PICKED_UP, MINED, TAKEN, OTHER -> "You no longer carry " + list + ".";
        };
    }

    /**
     * Merges entries of the same kind, keeping first-seen order: "3 bread, 1 iron
     * ingot".
     */
    static String join(List<Moved> items) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Moved item : items) {
            if (item != null && item.amount() > 0 && item.name() != null && !item.name().isBlank())
                merged.merge(item.name(), item.amount(), Integer::sum);
        }
        return merged.entrySet().stream().map(entry -> entry.getValue() + " " + entry.getKey())
                .collect(Collectors.joining(", "));
    }
}
