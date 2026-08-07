package dev.blockfolk.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import dev.blockfolk.model.NpcInstance;

/** Immutable bindings for the target aliases included in one AI request. */
public record AiTargetSnapshot(Map<String, UUID> entityIds, Map<String, UUID> npcInstanceIds,
        Map<String, Location> locations) {
    public AiTargetSnapshot {
        entityIds = Map.copyOf(entityIds);
        npcInstanceIds = Map.copyOf(npcInstanceIds);
        Map<String, Location> copiedLocations = new LinkedHashMap<>();
        locations.forEach((alias, location) -> copiedLocations.put(alias, location.clone()));
        locations = Map.copyOf(copiedLocations);
    }

    public Optional<UUID> entityId(String alias) {
        return Optional.ofNullable(entityIds.get(alias));
    }

    public Optional<UUID> npcInstanceId(String alias) {
        return Optional.ofNullable(npcInstanceIds.get(alias));
    }

    public Optional<Location> location(String alias) {
        return Optional.ofNullable(locations.get(alias)).map(Location::clone);
    }

    @Override
    public Map<String, Location> locations() {
        Map<String, Location> copiedLocations = new LinkedHashMap<>();
        locations.forEach((alias, location) -> copiedLocations.put(alias, location.clone()));
        return Map.copyOf(copiedLocations);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, UUID> entityIds = new LinkedHashMap<>();
        private final Map<String, UUID> npcInstanceIds = new LinkedHashMap<>();
        private final Map<String, Location> locations = new LinkedHashMap<>();

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

        AiTargetSnapshot build() {
            return new AiTargetSnapshot(entityIds, npcInstanceIds, locations);
        }
    }
}
