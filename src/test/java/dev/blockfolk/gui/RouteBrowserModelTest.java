package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;

class RouteBrowserModelTest {

    @Test
    void groupsRoutesByOwnerEvenWhenNoActionReferencesThem() {
        NpcRoute patrol = owned("Patrol", "guard");
        NpcRoute sharedName = owned("Village/Shared", "merchant");
        NpcRoute legacy = NpcRoute.create("Unused");
        NpcDefinition guard = NpcDefinition.create("Guard");
        NpcDefinition merchant = NpcDefinition.create("Merchant");
        NpcDefinition stationary = NpcDefinition.create("Stationary");

        List<RouteBrowserModel.Entry> root = RouteBrowserModel.entries(List.of(patrol, sharedName, legacy),
                List.of(guard, merchant, stationary), "");
        assertEquals(List.of("Guard", "Merchant", "unused"),
                root.stream().map(RouteBrowserModel.Entry::label).toList());
        assertTrue(root.get(0).npcFolder());
        assertTrue(root.get(1).npcFolder());
        assertFalse(root.get(2).folder());
        assertEquals(1, root.get(0).childCount());
        assertEquals(1, root.get(1).childCount());
    }

    @Test
    void eachNpcFolderOnlyContainsItsOwnRoutes() {
        NpcRoute patrol = owned("Patrol", "guard");
        NpcRoute market = owned("Market", "merchant");
        List<NpcDefinition> definitions = List.of(NpcDefinition.create("Guard"), NpcDefinition.create("Merchant"));

        assertEquals(List.of("patrol"), RouteBrowserModel.entries(List.of(patrol, market), definitions, "npc:guard")
                .stream().map(RouteBrowserModel.Entry::label).toList());
        assertEquals(List.of("market"), RouteBrowserModel.entries(List.of(patrol, market), definitions, "npc:merchant")
                .stream().map(RouteBrowserModel.Entry::label).toList());
    }

    private static NpcRoute owned(String name, String owner) {
        NpcRoute route = NpcRoute.create(name);
        route.setOwnerKey(owner);
        return route;
    }
}
