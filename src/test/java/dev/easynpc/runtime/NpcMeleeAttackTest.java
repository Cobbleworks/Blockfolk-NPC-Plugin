package dev.easynpc.runtime;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcMeleeAttackTest {
    @Test
    void usesVanillaMeleeDamageForCommonWeapons() {
        assertEquals(4.0, NpcMeleeAttack.baseDamage(Material.WOODEN_SWORD));
        assertEquals(6.0, NpcMeleeAttack.baseDamage(Material.IRON_SWORD));
        assertEquals(8.0, NpcMeleeAttack.baseDamage(Material.NETHERITE_SWORD));
        assertEquals(10.0, NpcMeleeAttack.baseDamage(Material.NETHERITE_AXE));
    }

    @Test
    void fallsBackToUnarmedDamageForNonWeapons() {
        assertEquals(2.0, NpcMeleeAttack.baseDamage(Material.AIR));
        assertEquals(2.0, NpcMeleeAttack.baseDamage(Material.STICK));
    }
}
