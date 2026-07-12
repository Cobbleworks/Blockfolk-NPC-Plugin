package dev.blockfolk.combat;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeleeNpcAttackTest {
    @Test
    void usesVanillaMeleeDamageForCommonWeapons() {
        assertEquals(4.0, MeleeNpcAttack.baseDamage(Material.WOODEN_SWORD));
        assertEquals(6.0, MeleeNpcAttack.baseDamage(Material.IRON_SWORD));
        assertEquals(8.0, MeleeNpcAttack.baseDamage(Material.NETHERITE_SWORD));
        assertEquals(10.0, MeleeNpcAttack.baseDamage(Material.NETHERITE_AXE));
    }

    @Test
    void fallsBackToUnarmedDamageForNonWeapons() {
        assertEquals(2.0, MeleeNpcAttack.baseDamage(Material.AIR));
        assertEquals(2.0, MeleeNpcAttack.baseDamage(Material.STICK));
    }
}
