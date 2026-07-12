package dev.blockfolk.model;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public final class NpcInstance {

    private final UUID id;
    private final String definitionKey;
    private Location location;
    private int entityId;
    private final ItemStack[] temporaryInventory = new ItemStack[27];

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
