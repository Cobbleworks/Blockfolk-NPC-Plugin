package dev.blockfolk.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRouteTest {
    @Test
    void ordersFromNearestPointThenNearestUnvisitedPoint() {
        NpcRoute route = NpcRoute.create("Patrol");
        RoutePoint ten = new RoutePoint("world", 10, 64, 0);
        RoutePoint zero = new RoutePoint("world", 0, 64, 0);
        RoutePoint five = new RoutePoint("world", 5, 64, 0);
        RoutePoint two = new RoutePoint("world", 2, 64, 0);
        route.setPoints(List.of(ten, zero, five, two));

        List<RoutePoint> ordered = route.logicallyOrdered(new Location(world("world"), 2.6, 65.0, 0.5));

        assertEquals(List.of(two, zero, five, ten), ordered);
    }

    @Test
    void ignoresDuplicatePointsAndRejectsCrossWorldPoints() {
        NpcRoute route = NpcRoute.create("Patrol");
        RoutePoint point = new RoutePoint("world", 1, 64, 1);

        assertTrue(route.addPoint(point));
        assertFalse(route.addPoint(point));
        assertThrows(IllegalArgumentException.class,
            () -> route.addPoint(new RoutePoint("nether", 1, 64, 1)));
    }

    @Test
    void identifiesPointsByBlockWhenWaitingMetadataChanges() {
        NpcRoute route = NpcRoute.create("Patrol");
        RoutePoint regular = new RoutePoint("world", 1, 64, 1);
        route.addPoint(regular);

        assertTrue(route.replacePoint(regular, regular.withWaitMillis(10_000L)));
        assertEquals(10_000L, route.findPoint(regular).orElseThrow().waitMillis());
        assertFalse(route.addPoint(regular));
        assertTrue(route.removePoint(regular));
    }

    @Test
    void waitingMetadataDoesNotChangeRouteOrdering() {
        NpcRoute route = NpcRoute.create("Patrol");
        RoutePoint nearWaiting = new RoutePoint("world", 2, 64, 0, 10_000L);
        RoutePoint middle = new RoutePoint("world", 5, 64, 0);
        RoutePoint far = new RoutePoint("world", 10, 64, 0);
        route.setPoints(List.of(far, nearWaiting, middle));

        assertEquals(
                List.of(nearWaiting, middle, far),
                route.logicallyOrdered(new Location(world("world"), 2.5, 65.0, 0.5))
        );
    }

    private World world(String name) {
        return (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[]{World.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getName", "toString" -> name;
                case "hashCode" -> name.hashCode();
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
