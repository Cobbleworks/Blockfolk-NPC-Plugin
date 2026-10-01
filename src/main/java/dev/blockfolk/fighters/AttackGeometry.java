package dev.blockfolk.fighters;

import org.bukkit.util.Vector;

public final class AttackGeometry {
    private AttackGeometry() {
    }
    public static boolean contains(FighterAttack attack, Vector relative, Vector direction, double range) {
        if (attack.shape() == FighterAttack.Shape.SPHERE)
            return relative.lengthSquared() <= attack.size() * attack.size();
        if (attack.shape() == FighterAttack.Shape.TELEPORT)
            return false;
        double forward = relative.dot(direction);
        if (forward < 0 || forward > range)
            return false;
        if (attack.shape() == FighterAttack.Shape.CONE) {
            double distanceSquared = relative.lengthSquared();
            return distanceSquared <= range * range && (distanceSquared < 0.01
                    || forward / Math.sqrt(distanceSquared) >= Math.cos(Math.toRadians(attack.angle() / 2)));
        }
        double sidewaysSquared = Math.max(0, relative.lengthSquared() - forward * forward);
        return sidewaysSquared <= attack.size() * attack.size();
    }
}
