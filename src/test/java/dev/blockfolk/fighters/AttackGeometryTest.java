package dev.blockfolk.fighters;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import dev.blockfolk.fighters.FighterAttack.*;

class AttackGeometryTest {
    private FighterAttack attack(Shape shape) {
        return new FighterAttack("test", "Test", Origin.NPC, shape, 8, 1, 60, 0, 100, 4, Set.of(), 3, 1, 0,
                Visual.FLAME);
    }
    @Test
    void coneHitsInFrontAndRejectsBehindOutsideAngleAndBeyondRange() {
        FighterAttack cone = attack(Shape.CONE);
        Vector aim = new Vector(1, 0, 0);
        assertTrue(AttackGeometry.contains(cone, new Vector(4, 0, 1), aim, 8));
        assertTrue(AttackGeometry.contains(cone, new Vector(4, 1, 0), aim, 8));
        assertFalse(AttackGeometry.contains(cone, new Vector(-2, 0, 0), aim, 8));
        assertFalse(AttackGeometry.contains(cone, new Vector(4, 0, 4), aim, 8));
        assertFalse(AttackGeometry.contains(cone, new Vector(8, 0, 1), aim, 8));
    }
    @Test
    void coneLengthIsIndependentOfActivationRange() {
        FighterAttack cone = attack(Shape.CONE).withConeLength(3);
        Vector aim = new Vector(1, 0, 0);
        assertEquals(8, cone.range());
        assertTrue(AttackGeometry.contains(cone, new Vector(2, 0, 0), aim, cone.reach()));
        assertFalse(AttackGeometry.contains(cone, new Vector(4, 0, 0), aim, cone.reach()));
        FighterAttack longer = cone.withConeLength(12);
        assertEquals(8, longer.range());
        assertTrue(AttackGeometry.contains(longer, new Vector(10, 0, 0), aim, longer.reach()));
        assertEquals(12,
                longer.withGeometry(longer.origin(), longer.shape(), 20, longer.size(), longer.angle()).coneLength());
    }

    @Test
    void beamUsesRadiusAndStopsAtTheFirstObstacle() {
        FighterAttack beam = attack(Shape.BEAM);
        Vector aim = new Vector(1, 0, 0);
        assertTrue(AttackGeometry.contains(beam, new Vector(3, 0.5, 0.5), aim, 8));
        assertFalse(AttackGeometry.contains(beam, new Vector(3, 1, 1), aim, 8));
        assertFalse(AttackGeometry.contains(beam, new Vector(3, 0, 0), aim, 2));
        assertFalse(AttackGeometry.contains(beam, new Vector(-1, 0, 0), aim, 8));
    }
    @Test
    void spheresUseActualDistanceAndTeleportDoesNotHitVictims() {
        assertTrue(AttackGeometry.contains(attack(Shape.SPHERE), new Vector(0, 1, 0), new Vector(1, 0, 0), 8));
        assertFalse(AttackGeometry.contains(attack(Shape.SPHERE), new Vector(1, 1, 0), new Vector(1, 0, 0), 8));
        assertFalse(AttackGeometry.contains(attack(Shape.TELEPORT), new Vector(), new Vector(1, 0, 0), 8));
    }

    @Test
    void ringsSpareTheInnerAreaAndDashesUseTheirPath() {
        FighterAttack ring = FighterAttack.builder("ring", "Ring").shape(Shape.RING).size(5).innerRadius(2).build();
        Vector aim = new Vector(1, 0, 0);
        assertFalse(AttackGeometry.contains(ring, new Vector(1, 0, 0), aim, 8));
        assertTrue(AttackGeometry.contains(ring, new Vector(0, 0, 3), aim, 8));
        assertFalse(AttackGeometry.contains(ring, new Vector(6, 0, 0), aim, 8));
        FighterAttack dash = FighterAttack.builder("dash", "Dash").shape(Shape.DASH).size(1).build();
        assertTrue(AttackGeometry.contains(dash, new Vector(3, 0, 0.5), aim, 4));
        assertFalse(AttackGeometry.contains(dash, new Vector(5, 0, 0), aim, 4));
        assertFalse(AttackGeometry.contains(attack(Shape.CHAIN), new Vector(), aim, 8));
        assertFalse(AttackGeometry.contains(attack(Shape.SELF), new Vector(), aim, 8));
    }
}
