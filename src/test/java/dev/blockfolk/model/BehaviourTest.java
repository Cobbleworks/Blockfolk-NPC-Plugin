package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviourTest {
    @Test
    void idleIsStoredAndDisplayedAsABuiltInEvent() {
        assertEquals("On Idle", BehaviourEvent.IDLE.displayName());
        assertEquals(BehaviourEvent.IDLE, BehaviourEvent.values()[1]);
    }


    @Test
    void parsesNewBehaviourTypesFromStoredNames() {
        assertEquals(BehaviourActionType.SHOW_HOLO_DIALOG,
            BehaviourActionType.fromStored("show_holo_dialog"));
        assertEquals(BehaviourActionType.ASK_QUESTION, BehaviourActionType.fromStored("ask_question"));
        assertEquals(BehaviourActionType.FALL_FLY, BehaviourActionType.fromStored("fall_fly"));
        assertEquals(BehaviourActionType.UNFOLLOW, BehaviourActionType.fromStored("unfollow"));
        assertEquals(BehaviourActionType.MOVE_TO, BehaviourActionType.fromStored("move_to"));
        assertEquals(BehaviourActionType.TELEPORT_TO, BehaviourActionType.fromStored("teleport_to"));
        assertEquals(BehaviourActionType.WAIT, BehaviourActionType.fromStored("wait"));
        assertEquals(BehaviourActionType.INTERACT, BehaviourActionType.fromStored("interact"));
        assertEquals(BehaviourActionType.GATHER_BLOCKS, BehaviourActionType.fromStored("gather_blocks"));
        assertEquals(BehaviourActionType.GATHER_BLOCKS, BehaviourActionType.fromStored("mine_blocks"));
        assertEquals(BehaviourActionType.TRIGGER_AI, BehaviourActionType.fromStored("ai_control"));
        assertEquals(BehaviourActionType.TAKE_ITEM, BehaviourActionType.fromStored("take_item"));
        assertEquals(BehaviourActionType.SHOW_INVENTORY, BehaviourActionType.fromStored("show_inventory"));
        assertEquals(BehaviourActionType.DROP_INVENTORY, BehaviourActionType.fromStored("drop_inventory"));
        assertEquals(BehaviourActionType.HARVEST, BehaviourActionType.fromStored("harvest"));
        assertEquals(BehaviourActionType.CHANGE_FIGHT_OPTIONS,
                BehaviourActionType.fromStored("change_fight_options"));
        assertEquals("At Sunrise", BehaviourEvent.SUNRISE.displayName());
        assertEquals("At Noon", BehaviourEvent.NOON.displayName());
        assertEquals("At Sunset", BehaviourEvent.SUNSET.displayName());
    }

    @Test
    void actionsRemainOrderedAndAreDefensivelyCopied() {
        NpcDefinition definition = new NpcDefinition("guard");
        definition.addBehaviourAction(BehaviourEvent.SPAWN,
            new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Ready."));
        definition.addBehaviourAction(BehaviourEvent.SPAWN,
            new BehaviourAction(BehaviourActionType.RUN_CONSOLE_COMMAND, "time set night"));

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
    }
}
