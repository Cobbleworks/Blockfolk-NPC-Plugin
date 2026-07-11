package dev.easynpc.combat;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

final class ArrowNpcAttack implements NpcAttack {

    private static final double RANGE = 16.0;
    private static final double MINIMUM_RANGE = 6.0;
    private final boolean crossbow;

    ArrowNpcAttack(boolean crossbow) {
        this.crossbow = crossbow;
    }

    @Override
    public double rangeSquared() {
        return RANGE * RANGE;
    }

    @Override
    public double minimumRangeSquared() {
        return MINIMUM_RANGE * MINIMUM_RANGE;
    }

    @Override
    public int cooldownTicks() {
        return crossbow ? 25 : 20;
    }

    @Override
    public void execute(LivingEntity attacker, LivingEntity target) {
        ItemStack weapon = attacker.getEquipment().getItemInMainHand();
        double speed = crossbow ? 3.15 : 3.0;
        Arrow arrow = attacker.launchProjectile(Arrow.class, aimedVelocity(attacker, target, speed, 0.045));
        int power = weapon.getEnchantmentLevel(Enchantment.POWER);
        arrow.setDamage(2.0 + (power == 0 ? 0.0 : 0.5 * power + 0.5));
        arrow.setKnockbackStrength(weapon.getEnchantmentLevel(Enchantment.PUNCH));
        arrow.setFireTicks(weapon.containsEnchantment(Enchantment.FLAME) ? 100 : 0);
        arrow.setPierceLevel(crossbow ? weapon.getEnchantmentLevel(Enchantment.PIERCING) : 0);
        arrow.setShotFromCrossbow(crossbow);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setWeapon(weapon.clone());
        attacker.swingMainHand();
    }

    static Vector aimedVelocity(LivingEntity attacker, LivingEntity target, double speed, double gravity) {
        return aimedVelocity(
                attacker.getEyeLocation().toVector(),
                target.getEyeLocation().toVector(),
                speed,
                gravity
        );
    }

    static Vector aimedVelocity(Vector origin, Vector destination, double speed, double gravity) {
        Vector delta = destination.clone().subtract(origin);
        double flightTicks = Math.max(1.0, delta.length() / speed);
        delta.setY(delta.getY() + 0.5 * gravity * flightTicks * flightTicks);
        return delta.normalize().multiply(speed);
    }
}
