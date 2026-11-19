package dev.blockfolk.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;

/**
 * An ordered set of phases. Within each phase, NPCs run their actions in
 * parallel.
 */
public record RegionalEvent(UUID id, UUID regionId, String name, Trigger trigger, DayPeriod period, boolean enabled,
        List<Phase> phases) {

    private static final String REFERENCE_PREFIX = "regional:";

    public RegionalEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(regionId);
        Objects.requireNonNull(trigger);
        Objects.requireNonNull(period);
        name = requireName(name);
        phases = List.copyOf(phases);
        if (phases.isEmpty())
            throw new IllegalArgumentException("An event needs at least one phase.");
    }

    public String reference() {
        return REFERENCE_PREFIX + id;
    }

    public static boolean isReference(String value) {
        return value != null && value.startsWith(REFERENCE_PREFIX);
    }

    public static Optional<UUID> referenceId(String value) {
        if (!isReference(value))
            return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value.substring(REFERENCE_PREFIX.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public RegionalEvent withSettings(Trigger trigger, DayPeriod period, boolean enabled) {
        return new RegionalEvent(id, regionId, name, trigger, period, enabled, phases);
    }

    public RegionalEvent withPhases(List<Phase> phases) {
        return new RegionalEvent(id, regionId, name, trigger, period, enabled, phases);
    }

    private static String requireName(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 48)
            throw new IllegalArgumentException("Enter a name between 1 and 48 characters.");
        return trimmed;
    }

    public enum Trigger {
        MANUAL("Manual"), PLAYER_ENTER("Player Enters"), TIME_OF_DAY("Time of Day");

        private final String label;

        Trigger(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Phase(UUID id, Map<UUID, List<BehaviourAction>> actors) {
        public Phase {
            Objects.requireNonNull(id);
            Map<UUID, List<BehaviourAction>> copy = new LinkedHashMap<>();
            actors.forEach((actor, actions) -> copy.put(actor, List.copyOf(actions)));
            actors = Collections.unmodifiableMap(copy);
        }
    }

    /** A horizontal area spanning the world's full height. */
    public record Region(UUID id, String name, String world, int x1, int z1, int x2, int z2) {
        public Region {
            Objects.requireNonNull(id);
            Objects.requireNonNull(world);
            name = requireName(name);
        }

        public boolean contains(Location location) {
            if (location.getWorld() == null || !world.equals(location.getWorld().getName()))
                return false;
            int x = location.getBlockX();
            int z = location.getBlockZ();
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }
}
