package dev.blockfolk.combat;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class MeleeNpcAttack implements NpcAttack {

    private static final double UNARMED_DAMAGE = 2.0;

    @Override
    public double rangeSquared() {
        return 3.0 * 3.0;
    }

    @Override
    public int cooldownTicks() {
        return 20;
    }

    @Override
    public void execute(LivingEntity attacker, LivingEntity target) {
        attacker.swingMainHand();
        PotionEffect weakness = attacker.getPotionEffect(PotionEffectType.WEAKNESS);
        double damage = weakenedDamage(damage(attacker.getEquipment().getItemInMainHand()),
                weakness == null ? -1 : weakness.getAmplifier());
        if (damage > 0)
            target.damage(damage, attacker);
    }

    static double weakenedDamage(double damage, int weaknessAmplifier) {
        return weaknessAmplifier < 0 ? damage : Math.max(0.0, damage - 4.0 * (weaknessAmplifier + 1.0));
    }

    static double damage(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return UNARMED_DAMAGE;
        }
        int sharpness = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        double sharpnessBonus = sharpness == 0 ? 0.0 : 0.5 * sharpness + 0.5;
        return baseDamage(weapon.getType()) + sharpnessBonus;
    }

    static double baseDamage(Material material) {
        return switch (material) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD, MACE -> 6.0;
            case DIAMOND_SWORD, WOODEN_AXE, GOLDEN_AXE -> 7.0;
            case NETHERITE_SWORD -> 8.0;
            case STONE_AXE, IRON_AXE, DIAMOND_AXE, TRIDENT -> 9.0;
            case NETHERITE_AXE -> 10.0;
            case WOODEN_PICKAXE, GOLDEN_PICKAXE -> 2.0;
            case STONE_PICKAXE -> 3.0;
            case IRON_PICKAXE -> 4.0;
            case DIAMOND_PICKAXE -> 5.0;
            case NETHERITE_PICKAXE -> 6.0;
            case WOODEN_SHOVEL, GOLDEN_SHOVEL -> 2.5;
            case STONE_SHOVEL -> 3.5;
            case IRON_SHOVEL -> 4.5;
            case DIAMOND_SHOVEL -> 5.5;
            case NETHERITE_SHOVEL -> 6.5;
            default -> UNARMED_DAMAGE;
        };
    }
}
