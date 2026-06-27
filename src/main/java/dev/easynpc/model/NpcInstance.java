package dev.easynpc.model;

import org.bukkit.Location;

import java.util.UUID;

public final class NpcInstance {
    private final UUID id;
    private final String definitionKey;
    private Location location;
    private int entityId;

    public NpcInstance(UUID id, String definitionKey, Location location) {
        this.id = id;
        this.definitionKey = definitionKey;
        this.location = location.clone();
    }

    public UUID getId() {
        return id;
    }

    public String getDefinitionKey() {
        return definitionKey;
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }
}
