package dev.easynpc.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class NpcDefinition {
    private final String key;
    private String displayName;
    private String skinUrl;
    private Location spawnpoint;
    private ItemStack[] inventoryContents;
    private ItemStack[] armorContents;
    private ItemStack mainHand;
    private ItemStack offHand;
    private List<String> dialogLines;
    private int secondsPerDialogLine;
    private CombatProfile combatProfile;
    private MovementProfile movementProfile;

    public NpcDefinition(String key) {
        this.key = key;
        this.displayName = key;
        this.inventoryContents = new ItemStack[36];
        this.armorContents = new ItemStack[4];
        this.dialogLines = new ArrayList<>();
        this.secondsPerDialogLine = 3;
        this.combatProfile = CombatProfile.disabled();
        this.movementProfile = MovementProfile.disabled();
    }

    public static NpcDefinition create(String displayName) {
        NpcDefinition definition = new NpcDefinition(toKey(displayName));
        definition.setDisplayName(displayName);
        return definition;
    }

    public static String toKey(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        return sanitized.isBlank() ? "npc" : sanitized;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = Objects.requireNonNullElse(displayName, key);
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public void setSkinUrl(String skinUrl) {
        this.skinUrl = skinUrl == null || skinUrl.isBlank() ? null : skinUrl.trim();
    }

    public Location getSpawnpoint() {
        return spawnpoint == null ? null : spawnpoint.clone();
    }

    public void setSpawnpoint(Location spawnpoint) {
        this.spawnpoint = spawnpoint == null ? null : spawnpoint.clone();
    }

    public ItemStack[] getInventoryContents() {
        return cloneArray(inventoryContents, 36);
    }

    public void setInventoryContents(ItemStack[] inventoryContents) {
        this.inventoryContents = cloneArray(inventoryContents, 36);
    }

    public ItemStack[] getArmorContents() {
        return cloneArray(armorContents, 4);
    }

    public void setArmorContents(ItemStack[] armorContents) {
        this.armorContents = cloneArray(armorContents, 4);
    }

    public ItemStack getMainHand() {
        return mainHand == null ? null : mainHand.clone();
    }

    public void setMainHand(ItemStack mainHand) {
        this.mainHand = mainHand == null ? null : mainHand.clone();
    }

    public ItemStack getOffHand() {
        return offHand == null ? null : offHand.clone();
    }

    public void setOffHand(ItemStack offHand) {
        this.offHand = offHand == null ? null : offHand.clone();
    }

    public List<String> getDialogLines() {
        return new ArrayList<>(dialogLines);
    }

    public void setDialogLines(List<String> dialogLines) {
        this.dialogLines = dialogLines == null ? new ArrayList<>() : new ArrayList<>(dialogLines);
    }

    public int getSecondsPerDialogLine() {
        return secondsPerDialogLine;
    }

    public void setSecondsPerDialogLine(int secondsPerDialogLine) {
        this.secondsPerDialogLine = Math.max(1, secondsPerDialogLine);
    }

    public CombatProfile getCombatProfile() {
        return combatProfile;
    }

    public void setCombatProfile(CombatProfile combatProfile) {
        this.combatProfile = combatProfile == null ? CombatProfile.disabled() : combatProfile;
    }

    public MovementProfile getMovementProfile() {
        return movementProfile;
    }

    public void setMovementProfile(MovementProfile movementProfile) {
        this.movementProfile = movementProfile == null ? MovementProfile.disabled() : movementProfile;
    }

    private static ItemStack[] cloneArray(ItemStack[] source, int length) {
        ItemStack[] copy = new ItemStack[length];
        if (source == null) {
            return copy;
        }
        for (int index = 0; index < Math.min(source.length, length); index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }
}
