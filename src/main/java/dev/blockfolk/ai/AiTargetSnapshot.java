package dev.blockfolk.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import dev.blockfolk.model.NpcInstance;

/**
 * Immutable bindings for the target aliases included in one AI request.
 * Synonyms map the loose names models tend to use (a location's name, an NPC's
 * display name, a mob type) to the canonical alias shown in context.
 */
public record AiTargetSnapshot(Map<String, UUID> entityIds, Map<String, UUID> npcInstanceIds,
        Map<String, Location> locations, Map<String, String> synonyms, Location origin) {

    /**
     * Block coordinates are accepted for MOVE_TO within this distance of the NPC.
     */
    static final double MAX_COORDINATE_DISTANCE = 128.0;
    private static final Pattern COORDINATES = Pattern
            .compile("^(-?\\d{1,8})(?:\\.\\d+)?[ ,]+(-?\\d{1,4})(?:\\.\\d+)?[ ,]+(-?\\d{1,8})(?:\\.\\d+)?$");
    private static final Pattern RESERVED = Pattern.compile(
            "^(nearby_|nearest_|triggering_|take_from_|store_in_|inventory_slot_).*|^current_target$|^nearest_switch$");

    public AiTargetSnapshot {
        entityIds = Map.copyOf(entityIds);
        npcInstanceIds = Map.copyOf(npcInstanceIds);
        Map<String, Location> copiedLocations = new LinkedHashMap<>();
        locations.forEach((alias, location) -> copiedLocations.put(alias, location.clone()));
        locations = Map.copyOf(copiedLocations);
        synonyms = synonyms == null ? Map.of() : Map.copyOf(synonyms);
        origin = origin == null ? null : origin.clone();
    }

    public AiTargetSnapshot(Map<String, UUID> entityIds, Map<String, UUID> npcInstanceIds,
            Map<String, Location> locations) {
        this(entityIds, npcInstanceIds, locations, Map.of(), null);
    }

    public Optional<UUID> entityId(String alias) {
        return Optional.ofNullable(entityIds.get(alias));
    }

    public Optional<UUID> npcInstanceId(String alias) {
        return Optional.ofNullable(npcInstanceIds.get(alias));
    }

    public Optional<Location> location(String alias) {
        Location bound = locations.get(alias);
        if (bound != null)
            return Optional.of(bound.clone());
        return coordinates(alias);
    }

    @Override
    public Map<String, Location> locations() {
        Map<String, Location> copiedLocations = new LinkedHashMap<>();
        locations.forEach((alias, location) -> copiedLocations.put(alias, location.clone()));
        return Map.copyOf(copiedLocations);
    }

    @Override
    public Location origin() {
        return origin == null ? null : origin.clone();
    }

    /**
     * Maps a model-supplied target to the canonical alias it most likely means.
     * Unknown values are returned normalized so validation can explain them.
     */
    public String canonical(String raw) {
        if (raw == null)
            return null;
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("^[\"'`]+|[\"'`]+$", "").trim();
        Matcher coordinates = COORDINATES.matcher(value.replaceAll("[()\\[\\]]", "").trim());
        if (coordinates.matches())
            return coordinates.group(1) + "," + coordinates.group(2) + "," + coordinates.group(3);
        // "nearby_location_1: Market" or "nearby_location_1 (Market)" ->
        // nearby_location_1
        int cut = indexOfAny(value, ':', '(');
        if (cut > 0 && known(value.substring(0, cut).trim()))
            return value.substring(0, cut).trim();
        for (String candidate : List.of(value, slug(value))) {
            String synonym = synonyms.get(candidate);
            if (synonym != null)
                return synonym;
            if (known(candidate))
                return candidate;
            // Models often drop the prefix: "location_1", "npc_mira", "player_2".
            if (known("nearby_" + candidate))
                return "nearby_" + candidate;
        }
        return value;
    }

    /** Canonical aliases (no synonyms) a model may use, for correction hints. */
    public List<String> aliases() {
        List<String> aliases = new ArrayList<>();
        entityIds.keySet().stream().filter(alias -> !synonyms.containsKey(alias)).sorted().forEach(aliases::add);
        npcInstanceIds.keySet().stream().sorted().forEach(aliases::add);
        locations.keySet().stream().sorted().forEach(aliases::add);
        return List.copyOf(aliases);
    }

    static boolean isCoordinates(String value) {
        return value != null && value.matches("-?\\d{1,8},-?\\d{1,4},-?\\d{1,8}");
    }

    private Optional<Location> coordinates(String alias) {
        if (origin == null || origin.getWorld() == null || !isCoordinates(alias))
            return Optional.empty();
        String[] parts = alias.split(",");
        Location target = new Location(origin.getWorld(), Integer.parseInt(parts[0]) + 0.5, Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]) + 0.5);
        if (target.distanceSquared(origin) > MAX_COORDINATE_DISTANCE * MAX_COORDINATE_DISTANCE)
            return Optional.empty();
        return Optional.of(target);
    }

    private boolean known(String alias) {
        return entityIds.containsKey(alias) || npcInstanceIds.containsKey(alias) || locations.containsKey(alias);
    }

    private static int indexOfAny(String value, char... characters) {
        int best = -1;
        for (char character : characters) {
            int index = value.indexOf(character);
            if (index >= 0 && (best < 0 || index < best))
                best = index;
        }
        return best;
    }

    static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("[^a-z0-9/]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, UUID> entityIds = new LinkedHashMap<>();
        private final Map<String, UUID> npcInstanceIds = new LinkedHashMap<>();
        private final Map<String, Location> locations = new LinkedHashMap<>();
        private final Map<String, String> synonyms = new LinkedHashMap<>();
        private final Set<String> ambiguous = new HashSet<>();
        private Location origin;

        void bindEntity(String alias, Entity entity) {
            if (entity != null)
                entityIds.put(alias, entity.getUniqueId());
        }

        void bindNpc(String alias, NpcInstance instance) {
            if (instance != null)
                npcInstanceIds.put(alias, instance.getId());
        }

        void bindLocation(String alias, Location location) {
            if (location != null)
                locations.put(alias, location.clone());
        }

        void origin(Location origin) {
            this.origin = origin == null ? null : origin.clone();
        }

        /** Adds a synonym; a name claimed by two different targets is dropped. */
        void synonym(String name, String alias) {
            String key = name == null ? "" : slug(name);
            if (key.isEmpty() || RESERVED.matcher(key).matches() || ambiguous.contains(key))
                return;
            String previous = synonyms.putIfAbsent(key, alias);
            if (previous != null && !previous.equals(alias)) {
                synonyms.remove(key);
                ambiguous.add(key);
            }
        }

        /** Adds a synonym where the first (nearest) target wins, e.g. a mob type. */
        void preferredSynonym(String name, String alias) {
            String key = name == null ? "" : slug(name);
            if (!key.isEmpty() && !RESERVED.matcher(key).matches() && !ambiguous.contains(key))
                synonyms.putIfAbsent(key, alias);
        }

        AiTargetSnapshot build() {
            return new AiTargetSnapshot(entityIds, npcInstanceIds, locations, synonyms, origin);
        }
    }
}
