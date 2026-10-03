package dev.blockfolk.combat;

import java.util.function.DoubleSupplier;

import dev.blockfolk.fighters.FighterAttack;

/**
 * Battlefield facts used to choose an automatic ability. Health fractions are
 * read lazily because most abilities never need them.
 */
public record CastContext(double distanceSquared, DoubleSupplier casterHealth, DoubleSupplier targetHealth) {
    public static CastContext atDistance(double distanceSquared) {
        return new CastContext(distanceSquared, () -> 1, () -> 1);
    }

    public boolean inRange(FighterAttack attack) {
        return distanceSquared <= attack.range() * attack.range()
                && distanceSquared >= attack.minRange() * attack.minRange();
    }

    public boolean allows(FighterAttack attack) {
        return inRange(attack) && attack.condition().test(casterHealth, targetHealth);
    }
}
