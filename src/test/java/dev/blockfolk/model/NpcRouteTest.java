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
        List.of(ten, zero, five, two).forEach(route::addPoint);
        World world = world("world");

        List<RoutePoint> ordered = route.logicallyOrdered(new Location(world, 2.6, 65.0, 0.5));

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
    void identifiesPointsByBlockWhenActionMetadataChanges() {
        NpcRoute route = NpcRoute.create("Patrol");
        RoutePoint regular = new RoutePoint("world", 1, 64, 1);
        route.addPoint(regular);

        RoutePoint withAction = regular.withActions(List.of(
                new BehaviourAction(BehaviourActionType.WAIT, "10.0")));
        assertTrue(route.replacePoint(regular, withAction));
        assertEquals(withAction.actions(), route.findPoint(regular).orElseThrow().actions());
        assertFalse(route.addPoint(regular));
        assertTrue(route.removePoint(regular));
    }

    @Test
    void actionMetadataDoesNotChangeRouteOrdering() {
        NpcRoute route = NpcRoute.create("Patrol");
        RoutePoint nearWaiting = new RoutePoint("world", 2, 64, 0, List.of(
                new BehaviourAction(BehaviourActionType.WAIT, "10.0")));
        RoutePoint middle = new RoutePoint("world", 5, 64, 0);
        RoutePoint far = new RoutePoint("world", 10, 64, 0);
        List.of(far, nearWaiting, middle).forEach(route::addPoint);
        World world = world("world");

        assertEquals(
                List.of(nearWaiting, middle, far),
                route.logicallyOrdered(new Location(world, 2.5, 65.0, 0.5))
        );
    }

    @Test
    void routePointDefensivelyCopiesWaypointActions() {
        List<BehaviourAction> source = new java.util.ArrayList<>();
        source.add(new BehaviourAction(BehaviourActionType.WAIT, "2.5"));
        RoutePoint point = new RoutePoint("world", 1, 64, 1, source);

        source.clear();

        assertEquals(List.of(new BehaviourAction(BehaviourActionType.WAIT, "2.5")), point.actions());
        assertThrows(UnsupportedOperationException.class,
                () -> point.actions().add(new BehaviourAction(BehaviourActionType.JUMP, null)));
    }

    @Test
    void acceptsGroupedRouteNamesAndRejectsMalformedGroups() {
        NpcRoute route = NpcRoute.create("Village/Night_Patrol");

        assertEquals("village/night_patrol", route.getKey());
        assertEquals("Village/Night_Patrol", route.getDisplayName());
        assertThrows(IllegalArgumentException.class, () -> NpcRoute.create("village//patrol"));
        assertThrows(IllegalArgumentException.class, () -> NpcRoute.create("../patrol"));
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
