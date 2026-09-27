package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.CustomEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.RoutePoint;

class CustomEventBrowserModelTest {
    @Test
    void groupsEventsUnderEveryNpcThatEmitsThemIncludingRouteActions() {
        NpcDefinition guard = NpcDefinition.create("Guard");
        NpcDefinition scout = NpcDefinition.create("Scout");
        BehaviourAction emitAlarm = new BehaviourAction(BehaviourActionType.EMIT_EVENT, "town/alarm");
        guard.setBehaviourActions(BehaviourEvent.SPAWN, List.of(emitAlarm));
        NpcRoute route = new NpcRoute("scout/patrol");
        route.setOwnerKey(scout.getKey());
        route.addPoint(new RoutePoint("world", 0, 0, 0, List.of(emitAlarm)));
        List<CustomEvent> events = List.of(new CustomEvent("town/alarm"), new CustomEvent("town/quiet"));

        assertEquals(List.of("Guard", "Scout", "Unassigned"),
                CustomEventBrowserModel.entries(events, List.of(guard, scout), List.of(route), "").stream()
                        .map(CustomEventBrowserModel.Entry::label).toList());
        assertEquals(List.of("town"),
                CustomEventBrowserModel.entries(events, List.of(guard, scout), List.of(route), "npc:guard").stream()
                        .map(CustomEventBrowserModel.Entry::label).toList());
        assertEquals(List.of("alarm"),
                CustomEventBrowserModel.entries(events, List.of(guard, scout), List.of(route), "npc:scout|town")
                        .stream().map(CustomEventBrowserModel.Entry::label).toList());
        assertEquals(List.of("quiet"),
                CustomEventBrowserModel.entries(events, List.of(guard, scout), List.of(route), "unassigned:|town")
                        .stream().map(CustomEventBrowserModel.Entry::label).toList());
    }
}
