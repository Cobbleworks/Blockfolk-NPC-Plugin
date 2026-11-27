package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.RoutePoint;

class RouteRepositoryTest {

    @TempDir
    File dataFolder;

    private RouteRepository repository() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RouteRepositoryTest"));
        return new RouteRepository(plugin);
    }

    private static NpcRoute route(String key, String owner, List<BehaviourAction> pointActions) {
        NpcRoute route = new NpcRoute(key);
        route.setOwnerKey(owner);
        route.addPoint(new RoutePoint("world", 0, 64, 0, pointActions));
        return route;
    }

    @Test
    void detachingBakesRouteSwapsIntoTheCopiesEvenWhenTheySwapBothWays() {
        RouteRepository routes = repository();
        routes.save(route("day", "guard", List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "night"))));
        routes.save(route("night", "guard", List.of()));
        NpcDefinition guard = new NpcDefinition("guard");
        guard.setBehaviourActions(BehaviourEvent.MORNING,
                List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "day")));
        guard.setBehaviourActions(BehaviourEvent.EVENING,
                List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "night")));
        NpcDefinition detached = guard.copyAs("Night Guard");
        Map<String, String> swaps = Map.of("day", "night", "night", "day");

        routes.copyOwnedRoutes(guard, detached, key -> swaps.getOrDefault(key, key));

        assertEquals("night-night-guard", detached.getBehaviourActions(BehaviourEvent.MORNING).getFirst().value());
        assertEquals("day-night-guard", detached.getBehaviourActions(BehaviourEvent.EVENING).getFirst().value());
        // A waypoint in the copied day route that switched to night now switches to day.
        assertEquals("day-night-guard",
                routes.find("day-night-guard").orElseThrow().getPoints().getFirst().actions().getFirst().value());
        // The preset keeps its own routes untouched.
        assertEquals("day", guard.getBehaviourActions(BehaviourEvent.MORNING).getFirst().value());
    }

    @Test
    void plainDuplicationPointsAtTheMatchingCopies() {
        RouteRepository routes = repository();
        routes.save(route("patrol", "guard", List.of()));
        NpcDefinition guard = new NpcDefinition("guard");
        guard.setBehaviourActions(BehaviourEvent.SPAWN,
                List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "patrol")));
        NpcDefinition copy = guard.copyAs("Guard Two");

        routes.copyOwnedRoutes(guard, copy);

        assertEquals("patrol-guard-two", copy.getBehaviourActions(BehaviourEvent.SPAWN).getFirst().value());
    }
}
