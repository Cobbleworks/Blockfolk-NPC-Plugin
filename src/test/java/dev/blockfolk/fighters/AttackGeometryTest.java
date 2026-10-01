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
}
