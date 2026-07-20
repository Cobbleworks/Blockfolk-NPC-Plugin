package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MiningTargetTest {

    @Test
    void matchesSelectedOreFamilies() {
        assertTrue(MiningTarget.matches(Material.COAL_ORE, "coal,gold"));
        assertTrue(MiningTarget.matches(Material.DEEPSLATE_GOLD_ORE, "coal,gold"));
        assertFalse(MiningTarget.matches(Material.DIAMOND_ORE, "coal,gold"));
    }

    @Test
    void supportsSpecialResourcesAndRejectsUnknownTargets() {
        assertTrue(MiningTarget.matches(Material.NETHER_QUARTZ_ORE, "quartz"));
        assertTrue(MiningTarget.matches(Material.CRYING_OBSIDIAN, "obsidian"));
        assertTrue(MiningTarget.matches(Material.ANCIENT_DEBRIS, "resources"));
        assertFalse(MiningTarget.valid("bedrock"));
    }

    @Test
    void matchesWhitelistedTreeFamiliesWithoutAllowingArbitraryBlocks() {
        assertTrue(MiningTarget.matches(Material.OAK_LOG, "wood"));
        assertTrue(MiningTarget.matches(Material.OAK_LOG, "oak"));
        assertFalse(MiningTarget.matches(Material.DARK_OAK_LOG, "oak"));
        assertTrue(MiningTarget.matches(Material.STRIPPED_DARK_OAK_WOOD, "dark_oak"));
        assertTrue(MiningTarget.matches(Material.WARPED_STEM, "warped"));
        assertTrue(MiningTarget.matches(Material.BAMBOO_BLOCK, "bamboo"));
        assertFalse(MiningTarget.matches(Material.OAK_PLANKS, "wood"));
        assertFalse(MiningTarget.matches(Material.BEDROCK, "any"));
    }
}
