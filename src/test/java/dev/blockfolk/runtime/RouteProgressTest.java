package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.RoutePoint;

class RouteProgressTest {

    private final RoutePoint first = new RoutePoint("world", 0, 64, 0);
    private final RoutePoint middle = new RoutePoint("world", 10, 64, 0);
    private final RoutePoint last = new RoutePoint("world", 20, 64, 0);
    private final List<RoutePoint> points = List.of(first, middle, last);

    @Test
    void reachableClosingLegContinuesTheLoop() {
        RouteProgress progress = new RouteProgress("route", points, points);

        progress.arrived();
        assertEquals(middle, progress.targetPoint());
        progress.arrived();
        assertEquals(last, progress.targetPoint());
        progress.arrived();
        assertEquals(first, progress.targetPoint());
        progress.arrived();
        assertEquals(middle, progress.targetPoint());
    }

    @Test
    void failedClosingLegReturnsThroughNearestWaypoint() {
        RouteProgress progress = new RouteProgress("route", points, points);
        progress.arrived();
        progress.arrived();
        progress.arrived();

        progress.stalled(at(20));
        assertEquals(middle, progress.targetPoint());
        progress.arrived();
        assertEquals(first, progress.targetPoint());
        progress.arrived();
        assertEquals(last, progress.targetPoint());

        progress.stalled(at(0));
        assertEquals(middle, progress.targetPoint());
        progress.arrived();
        assertEquals(last, progress.targetPoint());
    }

    @Test
    void failedFirstTargetChoosesNextNearestPoint() {
        RouteProgress progress = new RouteProgress("route", points, points);

        progress.stalled(at(1));

        assertEquals(middle, progress.targetPoint());
    }

    @Test
    void exhaustsAlternativesBeforeWaitingToRetry() {
        RouteProgress progress = new RouteProgress("route", points, points);
        progress.arrived();

        progress.stalled(at(0));
        assertEquals(last, progress.targetPoint());
        progress.stalled(at(0));
        for (int tick = 0; tick < 99; tick++) {
            assertFalse(progress.ready(at(0)));
        }
        assertTrue(progress.ready(at(0)));
        assertEquals(middle, progress.targetPoint());
    }

    private Location at(double x) {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName", "toString" -> "world";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new Location(world, x + 0.5, 65, 0.5);
    }
}
