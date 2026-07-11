package dev.blockfolk.model;

import org.bukkit.Material;

public enum LootTier {
    COMMON("Common Loot", Material.COPPER_INGOT, 1.0),
    UNCOMMON("Uncommon Loot", Material.IRON_INGOT, 0.5),
    RARE("Rare Loot", Material.GOLD_INGOT, 0.25),
    LEGENDARY("Legendary Loot", Material.DIAMOND, 0.1);

    public static final int SLOTS_PER_TIER = 9;

    private final String displayName;
    private final Material icon;
    private final double dropChance;

    LootTier(String displayName, Material icon, double dropChance) {
        this.displayName = displayName;
        this.icon = icon;
        this.dropChance = dropChance;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public double dropChance() {
        return dropChance;
    }

    public int dropChancePercent() {
        return (int) Math.round(dropChance * 100);
    }

    public boolean shouldDrop(double roll) {
        return roll >= 0.0 && roll < dropChance;
    }

    public int rowStarterSlot() {
        return ordinal() * SLOTS_PER_TIER;
    }

    public static boolean isRowStarterSlot(int slot) {
        return slot >= 0
                && slot < values().length * SLOTS_PER_TIER
                && slot % SLOTS_PER_TIER == 0;
    }

    public static LootTier forInventorySlot(int slot) {
        if (slot < 0 || slot >= values().length * SLOTS_PER_TIER) {
            throw new IllegalArgumentException("Loot inventory slot must be between 0 and 35: " + slot);
        }
        return values()[slot / SLOTS_PER_TIER];
    }
}
