package dev.easynpc.combat;

import org.bukkit.entity.LivingEntity;

/** A single, selectable NPC attack implementation. */
public interface NpcAttack {
    double rangeSquared();

    /** Distance the NPC tries to keep so projectiles do not immediately collide nearby. */
    default double minimumRangeSquared() {
        return 0.0;
    }

    int cooldownTicks();

    void execute(LivingEntity attacker, LivingEntity target);
}
