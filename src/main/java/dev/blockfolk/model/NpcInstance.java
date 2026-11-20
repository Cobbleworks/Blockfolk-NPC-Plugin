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
    // Variant overrides: null inherits the preset's value.
    private String label;
    private String skinUrl;
    private String skinTextureValue;
    private String skinTextureSignature;
    private final java.util.Map<String, String> routeSwaps = new java.util.LinkedHashMap<>();

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

    public StoredLocation getStoredLocation() {
        return location;
    }
    public StoredLocation getStoredSpawnLocation() {
        return spawnLocation;
    }

    public boolean isAwaitingRespawn() {
        return respawnAtEpochMillis > 0L;
    }
    public long getRespawnAtEpochMillis() {
        return respawnAtEpochMillis;
    }
    public void setRespawnAtEpochMillis(long value) {
        respawnAtEpochMillis = Math.max(0L, value);
    }

    public void returnToSpawn() {
        location = spawnLocation;
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
        if (contents == null)
            return;
        for (int slot = 0; slot < Math.min(contents.length, temporaryInventory.length); slot++) {
            temporaryInventory[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
    }

    /** Admin-only name that tells variants of one preset apart; null if unset. */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = blankToNull(label);
    }

    /** The variant's own skin source; null inherits the preset's skin. */
    public String getSkinUrl() {
        return skinUrl;
    }

    public String getSkinTextureValue() {
        return skinTextureValue;
    }

    public String getSkinTextureSignature() {
        return skinTextureSignature;
    }

    public boolean hasSkinOverride() {
        return skinUrl != null || skinTextureValue != null;
    }

    /** Sets an unresolved skin override; null clears it. */
    public void setSkinUrl(String skinUrl) {
        setResolvedSkin(skinUrl, null, null);
    }

    public void setResolvedSkin(String skinUrl, String textureValue, String textureSignature) {
        this.skinUrl = blankToNull(skinUrl);
        this.skinTextureValue = blankToNull(textureValue);
        this.skinTextureSignature = this.skinTextureValue == null ? null : blankToNull(textureSignature);
    }

    public void clearSkin() {
        setResolvedSkin(null, null, null);
    }

    /**
     * Routes this variant walks in place of the preset's: preset route key to
     * replacement route key. Both belong to the same preset.
     */
    public java.util.Map<String, String> getRouteSwaps() {
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(routeSwaps));
    }

    /** The route walked in place of {@code presetRouteKey}; null if not swapped. */
    public String getRouteSwap(String presetRouteKey) {
        return presetRouteKey == null ? null : routeSwaps.get(NpcRoute.normalizeKey(presetRouteKey));
    }

    /**
     * Walks {@code replacementKey} wherever the preset walks
     * {@code presetRouteKey}. A null or identical replacement removes the swap.
     */
    public void setRouteSwap(String presetRouteKey, String replacementKey) {
        String from = NpcRoute.normalizeKey(presetRouteKey);
        String to = blankToNull(replacementKey) == null ? null : NpcRoute.normalizeKey(replacementKey);
        if (to == null || to.equals(from))
            routeSwaps.remove(from);
        else
            routeSwaps.put(from, to);
    }

    /** The route this variant actually walks when told to walk {@code routeKey}. */
    public String resolveRoute(String routeKey) {
        if (routeKey == null)
            return null;
        String swapped = getRouteSwap(routeKey);
        return swapped == null ? NpcRoute.normalizeKey(routeKey) : swapped;
    }

    /**
     * Drops every swap from or to {@code routeKey}; returns whether any existed.
     */
    public boolean removeRouteReferences(String routeKey) {
        String key = NpcRoute.normalizeKey(routeKey);
        return routeSwaps.entrySet().removeIf(entry -> entry.getKey().equals(key) || entry.getValue().equals(key));
    }

    public boolean hasOverrides() {
        return label != null || hasSkinOverride() || !routeSwaps.isEmpty();
    }

    /** Skin source URL this instance renders with: its own, else the preset's. */
    public String resolvedSkinUrl(NpcDefinition definition) {
        return hasSkinOverride() ? skinUrl : definition.getSkinUrl();
    }

    public String resolvedSkinTextureValue(NpcDefinition definition) {
        return hasSkinOverride() ? skinTextureValue : definition.getSkinTextureValue();
    }

    public String resolvedSkinTextureSignature(NpcDefinition definition) {
        return hasSkinOverride() ? skinTextureSignature : definition.getSkinTextureSignature();
    }

    /**
     * The preset's default movement with this variant's route swap applied.
     */
    public MovementProfile defaultMovement(NpcDefinition definition) {
        MovementProfile preset = definition.getMovementProfile();
        return preset.enabled()
                ? MovementProfile.routing(resolveRoute(preset.routeKey()), preset.walkingSpeed())
                : preset;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
