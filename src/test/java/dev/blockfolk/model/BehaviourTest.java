package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviourTest {

    @Test
    void parsesNewBehaviourTypesFromStoredNames() {
        assertEquals(BehaviourActionType.SHOW_HOLO_DIALOG,
            BehaviourActionType.fromStored("show-holo-dialog"));
        assertEquals(BehaviourActionType.STAND, BehaviourActionType.fromStored("sit"));
        assertEquals(BehaviourActionType.FALL_FLY, BehaviourActionType.fromStored("fall-fly"));
        assertEquals(BehaviourActionType.UNFOLLOW, BehaviourActionType.fromStored("unfollow"));
        assertEquals(BehaviourEvent.HEAL, BehaviourEvent.fromStored("heal"));
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
