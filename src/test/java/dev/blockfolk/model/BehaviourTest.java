package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviourTest {
    @Test
    void idleIsStoredAndDisplayedAsABuiltInEvent() {
        assertEquals("On Idle", BehaviourEvent.IDLE.displayName());
        assertEquals(BehaviourEvent.IDLE, BehaviourEvent.values()[1]);
    }

    @Test
    void parsesNewBehaviourTypesFromStoredNames() {
        assertEquals(BehaviourActionType.SHOW_HOLO_DIALOG, BehaviourActionType.fromStored("show_holo_dialog"));
        assertEquals(BehaviourActionType.ASK_QUESTION, BehaviourActionType.fromStored("ask_question"));
        assertEquals(BehaviourActionType.FALL_FLY, BehaviourActionType.fromStored("fall_fly"));
        assertEquals(BehaviourActionType.UNFOLLOW, BehaviourActionType.fromStored("unfollow"));
        assertEquals(BehaviourActionType.MOVE_TO, BehaviourActionType.fromStored("move_to"));
        assertEquals(BehaviourActionType.TELEPORT_TO, BehaviourActionType.fromStored("teleport_to"));
        assertEquals(BehaviourActionType.WAIT, BehaviourActionType.fromStored("wait"));
        assertEquals(BehaviourActionType.INTERACT, BehaviourActionType.fromStored("interact"));
        assertEquals(BehaviourActionType.MINE_BLOCKS, BehaviourActionType.fromStored("mine_blocks"));
        assertEquals(BehaviourActionType.TAKE_ITEM, BehaviourActionType.fromStored("take_item"));
        assertEquals(BehaviourActionType.SHOW_INVENTORY, BehaviourActionType.fromStored("show_inventory"));
        assertEquals(BehaviourActionType.DROP_INVENTORY, BehaviourActionType.fromStored("drop_inventory"));
        assertEquals(BehaviourActionType.HARVEST, BehaviourActionType.fromStored("harvest"));
        assertEquals(BehaviourActionType.CHANGE_FIGHT_OPTIONS, BehaviourActionType.fromStored("change_fight_options"));
        assertEquals("At Sunrise", BehaviourEvent.SUNRISE.displayName());
        assertEquals("At Noon", BehaviourEvent.NOON.displayName());
        assertEquals("At Sunset", BehaviourEvent.SUNSET.displayName());
    }

    @Test
    void actionsRemainOrderedAndAreDefensivelyCopied() {
        NpcDefinition definition = new NpcDefinition("guard");
        definition.setBehaviourActions(BehaviourEvent.SPAWN,
                List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Ready."),
                        new BehaviourAction(BehaviourActionType.RUN_CONSOLE_COMMAND, "time set night")));

        List<BehaviourAction> actions = definition.getBehaviourActions(BehaviourEvent.SPAWN);
        assertEquals(BehaviourActionType.SEND_DIALOG, actions.get(0).type());
        assertEquals(BehaviourActionType.RUN_CONSOLE_COMMAND, actions.get(1).type());
        actions.clear();
        assertEquals(2, definition.getBehaviourActions(BehaviourEvent.SPAWN).size());
    }

    @Test
    void removingLastActionRemovesSequence() {
        NpcDefinition definition = new NpcDefinition("guard");
        definition.setBehaviourActions(BehaviourEvent.DEATH,
                List.of(new BehaviourAction(BehaviourActionType.STOP_NAVIGATION, null)));
        definition.removeBehaviourAction(BehaviourEvent.DEATH, 0);
        assertTrue(definition.getBehaviourActions(BehaviourEvent.DEATH).isEmpty());
        assertTrue(definition.getBehaviourRows().isEmpty());
    }

    @Test
    void repeatedEventRowsKeepTheirOwnActionsAndRunInRowOrder() {
        NpcDefinition definition = new NpcDefinition("guard");
        BehaviourAction first = new BehaviourAction(BehaviourActionType.SEND_DIALOG, "First");
        BehaviourAction second = new BehaviourAction(BehaviourActionType.WAVE, null);
        BehaviourAction third = new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Third");
        int firstRow = definition.addBehaviourRow(BehaviourEvent.RIGHT_CLICK);
        definition.addBehaviourRow(BehaviourEvent.SPAWN, List.of(second));
        int thirdRow = definition.addBehaviourRow(BehaviourEvent.RIGHT_CLICK, List.of(third));
        definition.setBehaviourRowActions(firstRow, List.of(first));

        assertEquals(List.of(first, third), definition.getBehaviourActions(BehaviourEvent.RIGHT_CLICK));
        assertEquals(List.of(second), definition.getBehaviourActions(BehaviourEvent.SPAWN));
        assertEquals(List.of(BehaviourEvent.RIGHT_CLICK, BehaviourEvent.SPAWN, BehaviourEvent.RIGHT_CLICK),
                definition.getBehaviourRows().stream().map(BehaviourRow::event).toList());

        definition.removeBehaviourRowAction(thirdRow, 0);
        assertTrue(definition.getBehaviourRows().get(thirdRow).actions().isEmpty());
        definition.removeBehaviourRow(firstRow);
        assertTrue(definition.getBehaviourActions(BehaviourEvent.RIGHT_CLICK).isEmpty());
    }

    @Test
    void rowHoldsAtMostSevenActionsButEventMayHaveMoreRows() {
        NpcDefinition definition = new NpcDefinition("guard");
        List<BehaviourAction> eight = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Line " + index)).toList();

        assertThrows(IllegalArgumentException.class, () -> definition.addBehaviourRow(BehaviourEvent.SPAWN, eight));
        definition.setBehaviourActions(BehaviourEvent.SPAWN, eight);
        assertEquals(2, definition.getBehaviourRows().size());
        assertEquals(7, definition.getBehaviourRows().getFirst().actions().size());
        assertEquals(eight, definition.getBehaviourActions(BehaviourEvent.SPAWN));
        assertEquals(definition.getBehaviourRows(), definition.copyAs("Copy").getBehaviourRows());
    }
}
