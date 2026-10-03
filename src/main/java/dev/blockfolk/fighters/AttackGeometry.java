package dev.blockfolk.fighters;

import org.bukkit.util.Vector;

public final class AttackGeometry {
    private AttackGeometry() {
    }
    public static boolean contains(FighterAttack attack, Vector relative, Vector direction, double range) {
        return switch (attack.shape()) {
            case SPHERE -> relative.lengthSquared() <= attack.size() * attack.size();
            case RING -> {
                double distanceSquared = relative.lengthSquared();
                yield distanceSquared <= attack.size() * attack.size()
                        && distanceSquared >= attack.innerRadius() * attack.innerRadius();
            }
            case CONE -> cone(attack, relative, direction, Math.min(range, attack.coneLength()));
            case BEAM, DASH -> beam(attack, relative, direction, range);
            // Chains pick victims hop by hop; teleport and self never hit others.
            case CHAIN, TELEPORT, SELF -> false;
        };
    }
    private static boolean cone(FighterAttack attack, Vector relative, Vector direction, double range) {
        double forward = relative.dot(direction);
        if (forward < 0 || forward > range)
            return false;
        double distanceSquared = relative.lengthSquared();
        return distanceSquared <= range * range && (distanceSquared < 0.01
                || forward / Math.sqrt(distanceSquared) >= Math.cos(Math.toRadians(attack.angle() / 2)));
    }
    private static boolean beam(FighterAttack attack, Vector relative, Vector direction, double range) {
        double forward = relative.dot(direction);
        if (forward < 0 || forward > range)
            return false;
        double sidewaysSquared = Math.max(0, relative.lengthSquared() - forward * forward);
        return sidewaysSquared <= attack.size() * attack.size();
    }
}
