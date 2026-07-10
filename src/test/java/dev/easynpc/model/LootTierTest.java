package dev.easynpc.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTierTest {
    @Test
    void mapsEachInventoryRowToItsLootTier() {
        assertEquals(LootTier.COMMON, LootTier.forInventorySlot(0));
        assertEquals(LootTier.COMMON, LootTier.forInventorySlot(8));
        assertEquals(LootTier.UNCOMMON, LootTier.forInventorySlot(9));
        assertEquals(LootTier.UNCOMMON, LootTier.forInventorySlot(17));
        assertEquals(LootTier.RARE, LootTier.forInventorySlot(18));
        assertEquals(LootTier.RARE, LootTier.forInventorySlot(26));
        assertEquals(LootTier.LEGENDARY, LootTier.forInventorySlot(27));
        assertEquals(LootTier.LEGENDARY, LootTier.forInventorySlot(35));
    }

    @Test
    void rejectsSlotsOutsideTheLootInventory() {
        assertThrows(IllegalArgumentException.class, () -> LootTier.forInventorySlot(-1));
        assertThrows(IllegalArgumentException.class, () -> LootTier.forInventorySlot(36));
    }

    @Test
    void exposesTheRequestedIconsAndChances() {
        assertEquals(Material.COPPER_INGOT, LootTier.COMMON.icon());
        assertEquals(Material.IRON_INGOT, LootTier.UNCOMMON.icon());
        assertEquals(Material.GOLD_INGOT, LootTier.RARE.icon());
        assertEquals(Material.DIAMOND, LootTier.LEGENDARY.icon());
        assertEquals(100, LootTier.COMMON.dropChancePercent());
        assertEquals(50, LootTier.UNCOMMON.dropChancePercent());
        assertEquals(25, LootTier.RARE.dropChancePercent());
        assertEquals(10, LootTier.LEGENDARY.dropChancePercent());
    }

    @Test
    void identifiesTheTierIconAtTheStartOfEachRow() {
        assertEquals(0, LootTier.COMMON.rowStarterSlot());
        assertEquals(9, LootTier.UNCOMMON.rowStarterSlot());
        assertEquals(18, LootTier.RARE.rowStarterSlot());
        assertEquals(27, LootTier.LEGENDARY.rowStarterSlot());
        assertTrue(LootTier.isRowStarterSlot(0));
        assertTrue(LootTier.isRowStarterSlot(9));
        assertTrue(LootTier.isRowStarterSlot(18));
        assertTrue(LootTier.isRowStarterSlot(27));
        assertFalse(LootTier.isRowStarterSlot(1));
        assertFalse(LootTier.isRowStarterSlot(36));
    }

    @Test
    void appliesChanceBoundaries() {
        assertTrue(LootTier.COMMON.shouldDrop(0.999999));
        assertTrue(LootTier.UNCOMMON.shouldDrop(0.499999));
        assertFalse(LootTier.UNCOMMON.shouldDrop(0.5));
        assertTrue(LootTier.RARE.shouldDrop(0.249999));
        assertFalse(LootTier.RARE.shouldDrop(0.25));
        assertTrue(LootTier.LEGENDARY.shouldDrop(0.099999));
        assertFalse(LootTier.LEGENDARY.shouldDrop(0.1));
    }
}
