package dev.blockfolk.combat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Selects combat behavior from the item currently held by an NPC.
 */
public final class NpcAttackSelector {

    private static final NpcAttack MELEE = new MeleeNpcAttack();
    private static final NpcAttack BOW = new ArrowNpcAttack(false);
    private static final NpcAttack CROSSBOW = new ArrowNpcAttack(true);
    private static final NpcAttack SPLASH_POTION = new SplashPotionNpcAttack();

    public NpcAttack select(ItemStack item) {
        return select(item == null ? Material.AIR : item.getType());
    }

    NpcAttack select(Material material) {
        return switch (material) {
            case BOW ->
                BOW;
            case CROSSBOW ->
                CROSSBOW;
            case SPLASH_POTION ->
                SPLASH_POTION;
            default ->
                MELEE;
        };
    }
}
