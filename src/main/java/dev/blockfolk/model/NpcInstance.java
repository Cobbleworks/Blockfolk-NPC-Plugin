package dev.blockfolk.model;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public final class NpcInstance {

    private final UUID id;
    private final String definitionKey;
    private StoredLocation location;
    private StoredLocation spawnLocation;
    private int entityId;
    private long respawnAtEpochMillis;
    private final ItemStack[] temporaryInventory = new ItemStack[27];

    public NpcInstance(UUID id, String definitionKey, Location location) {
        this(id, definitionKey, location, location);
    }

    public NpcInstance(UUID id, String definitionKey, Location location, Location spawnLocation) {
        this(id, definitionKey, StoredLocation.from(location), StoredLocation.from(spawnLocation), 0L);
    }

    public NpcInstance(UUID id, String definitionKey, StoredLocation location, StoredLocation spawnLocation,
            long respawnAtEpochMillis) {
        this.id = id;
        this.definitionKey = definitionKey;
        this.location = java.util.Objects.requireNonNull(location, "location");
        this.spawnLocation = java.util.Objects.requireNonNull(spawnLocation, "spawnLocation");
        this.respawnAtEpochMillis = Math.max(0L, respawnAtEpochMillis);
    }

    public UUID getId() {
        return id;
    }

    public String getDefinitionKey() {
        return definitionKey;
    }

    public Location getLocation() {
        return location.toLocation();
    }

    public void setLocation(Location location) {
        this.location = StoredLocation.from(location);
    }

    public Location getSpawnLocation() {
        return spawnLocation.toLocation();
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = StoredLocation.from(spawnLocation);
    }

    public StoredLocation getStoredLocation() { return location; }
    public StoredLocation getStoredSpawnLocation() { return spawnLocation; }

    public boolean isAwaitingRespawn() { return respawnAtEpochMillis > 0L; }
    public long getRespawnAtEpochMillis() { return respawnAtEpochMillis; }
    public void setRespawnAtEpochMillis(long value) { respawnAtEpochMillis = Math.max(0L, value); }

    public void returnToSpawn() { location = spawnLocation; }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public ItemStack[] getTemporaryInventoryContents() {
        ItemStack[] copy = new ItemStack[temporaryInventory.length];
        for (int slot = 0; slot < temporaryInventory.length; slot++) {
            copy[slot] = temporaryInventory[slot] == null ? null : temporaryInventory[slot].clone();
        }
        return copy;
    }

    public void setTemporaryInventoryContents(ItemStack[] contents) {
        java.util.Arrays.fill(temporaryInventory, null);
        if (contents == null) return;
        for (int slot = 0; slot < Math.min(contents.length, temporaryInventory.length); slot++) {
            temporaryInventory[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
    }
}
