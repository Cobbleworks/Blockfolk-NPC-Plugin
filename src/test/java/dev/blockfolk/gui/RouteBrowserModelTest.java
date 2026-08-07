package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;

class RouteBrowserModelTest {

    @Test
    void groupsUsedRoutesByNpcAndLeavesUnusedRoutesAtTheRoot() {
        NpcRoute patrol = NpcRoute.create("Patrol");
        NpcRoute shared = NpcRoute.create("Village/Shared");
        NpcRoute unused = NpcRoute.create("Unused");
        NpcRoute groupedUnused = NpcRoute.create("Wilderness/Loop");

        NpcDefinition guard = npcUsing("Guard", "patrol", "village/shared");
        NpcDefinition merchant = npcUsing("Merchant", "village/shared");
        NpcDefinition stationary = NpcDefinition.create("Stationary");

        List<RouteBrowserModel.Entry> root = RouteBrowserModel.entries(List.of(patrol, shared, unused, groupedUnused),
                List.of(guard, merchant, stationary), "");

        assertEquals(List.of("Guard", "Merchant", "unused", "wilderness/loop"),
                root.stream().map(RouteBrowserModel.Entry::label).toList());
        assertTrue(root.get(0).npcFolder());
        assertTrue(root.get(1).npcFolder());
        assertFalse(root.get(2).folder());
        assertEquals(2, root.get(0).childCount());
        assertEquals(1, root.get(1).childCount());
    }

    @Test
    void showsSharedRoutesDirectlyInEveryNpcFolder() {
        NpcRoute patrol = NpcRoute.create("Patrol");
        NpcRoute shared = NpcRoute.create("Village/Shared");
        NpcDefinition guard = npcUsing("Guard", "patrol", "village/shared");
        NpcDefinition merchant = npcUsing("Merchant", "village/shared");

        List<RouteBrowserModel.Entry> guardRoutes = RouteBrowserModel.entries(List.of(patrol, shared),
                List.of(guard, merchant), "npc:guard");
        List<RouteBrowserModel.Entry> merchantRoutes = RouteBrowserModel.entries(List.of(patrol, shared),
                List.of(guard, merchant), "npc:merchant");
        assertEquals(List.of("patrol", "village/shared"),
                guardRoutes.stream().map(RouteBrowserModel.Entry::label).toList());
        assertEquals(List.of("village/shared"), merchantRoutes.stream().map(RouteBrowserModel.Entry::label).toList());
    }

    private static NpcDefinition npcUsing(String name, String... routeKeys) {
        NpcDefinition definition = NpcDefinition.create(name);
        definition.setBehaviourActions(BehaviourEvent.SPAWN, java.util.Arrays.stream(routeKeys)
                .map(key -> new BehaviourAction(BehaviourActionType.SET_ROUTE, key)).toList());
        return definition;
    }
}
