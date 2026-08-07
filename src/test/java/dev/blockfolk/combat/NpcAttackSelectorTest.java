package dev.blockfolk.combat;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.bukkit.util.Vector;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcAttackSelectorTest {
    private final NpcAttackSelector selector = new NpcAttackSelector();

    @Test
    void selectsRangedAttacksFromHeldItem() {
        assertInstanceOf(ArrowNpcAttack.class, selector.select(Material.BOW));
        assertInstanceOf(ArrowNpcAttack.class, selector.select(Material.CROSSBOW));
        assertInstanceOf(SplashPotionNpcAttack.class, selector.select(Material.SPLASH_POTION));
    }

    @Test
    void defaultsToMelee() {
        assertInstanceOf(MeleeNpcAttack.class, selector.select(Material.DIAMOND_SWORD));
        assertInstanceOf(MeleeNpcAttack.class, selector.select(Material.AIR));
    }

    @Test
    void rangedAttacksRequireRoomAroundTheNpc() {
        assertTrue(selector.select(Material.BOW).minimumRangeSquared() >= 36.0);
        assertTrue(selector.select(Material.SPLASH_POTION).minimumRangeSquared() >= 25.0);
    }

    @Test
    void potionTrajectoryStaysFlatEnoughForLowCeilings() {
        Vector velocity = ArrowNpcAttack.aimedVelocity(new Vector(0.0, 1.6, 0.0), new Vector(10.0, 0.6, 0.0), 1.2,
                0.05);

        assertTrue(velocity.getX() > 1.0);
        assertTrue(velocity.getY() < 0.15);
    }
}
