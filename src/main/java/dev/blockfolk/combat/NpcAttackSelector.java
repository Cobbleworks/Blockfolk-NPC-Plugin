package dev.blockfolk.combat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Selects combat behavior from the item currently held by an NPC.
 */
public final class NpcAttackSelector {

    private static final double MELEE_RANGE_SQUARED = 3.0 * 3.0;

    private static final NpcAttack MELEE = new MeleeNpcAttack();
    private static final NpcAttack BOW = new ArrowNpcAttack(false);
    private static final NpcAttack CROSSBOW = new ArrowNpcAttack(true);
    private static final NpcAttack SPLASH_POTION = new SplashPotionNpcAttack();

    public NpcAttack select(ItemStack item) {
        return select(item == null ? Material.AIR : item.getType());
    }

    NpcAttack select(Material material) {
        return switch (material) {
            case BOW -> BOW;
            case CROSSBOW -> CROSSBOW;
            case SPLASH_POTION -> SPLASH_POTION;
            default -> MELEE;
        };
    }

    /** Selects which configured hand should be used as the main hand in combat. */
    public boolean useOffHand(ItemStack mainHand, ItemStack offHand, double distanceSquared) {
        return useOffHand(material(mainHand), material(offHand), distanceSquared);
    }

    boolean useOffHand(Material main, Material off, double distanceSquared) {
        if (isMeleeWeapon(main) && isBow(off)) {
            return distanceSquared > MELEE_RANGE_SQUARED;
        }
        if (isBow(main) && isMeleeWeapon(off)) {
            return distanceSquared <= MELEE_RANGE_SQUARED;
        }
        return false;
    }

    private static Material material(ItemStack item) {
        return item == null ? Material.AIR : item.getType();
    }

    private static boolean isBow(Material material) {
        return material == Material.BOW || material == Material.CROSSBOW;
    }

    private static boolean isMeleeWeapon(Material material) {
        return switch (material) {
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD,
                    WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE,
                    WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, GOLDEN_PICKAXE, DIAMOND_PICKAXE,
                    NETHERITE_PICKAXE, WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, GOLDEN_SHOVEL,
                    DIAMOND_SHOVEL, NETHERITE_SHOVEL, TRIDENT, MACE -> true;
            default -> false;
        };
    }
}
