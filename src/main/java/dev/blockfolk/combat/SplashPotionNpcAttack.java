package dev.blockfolk.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

final class SplashPotionNpcAttack implements NpcAttack {

    private static final double RANGE = 12.0;
    private static final double MINIMUM_RANGE = 5.0;

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
        return 30;
    }

    @Override
    public void execute(LivingEntity attacker, LivingEntity target) {
        ItemStack potionItem = attacker.getEquipment().getItemInMainHand().asOne();
        Vector destination = target.getLocation().add(0.0, 0.6, 0.0).toVector();
        ThrownPotion potion = attacker.launchProjectile(
                ThrownPotion.class,
                // Potions need a flatter path than arrows. A slow, high lob hits
                // low ceilings directly above the thrower before moving forward.
                ArrowNpcAttack.aimedVelocity(attacker.getEyeLocation().toVector(), destination, 1.2, 0.05)
        );
        potion.setItem(potionItem);
        attacker.swingMainHand();
    }
}
