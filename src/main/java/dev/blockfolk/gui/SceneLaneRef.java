package dev.blockfolk.gui;

import java.util.List;

import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.SceneRole;

/**
 * Points the shared action editor at one action row of a scene role: its lane,
 * its wrap-up, or one of its reactions.
 */
record SceneLaneRef(String sceneKey, String roleId, Part part, BehaviourEvent event) {

    enum Part {
        LANE, WRAP_UP, REACTION
    }

    static SceneLaneRef lane(String sceneKey, String roleId) {
        return new SceneLaneRef(sceneKey, roleId, Part.LANE, null);
    }

    static SceneLaneRef wrapUp(String sceneKey, String roleId) {
        return new SceneLaneRef(sceneKey, roleId, Part.WRAP_UP, null);
    }

    static SceneLaneRef reaction(String sceneKey, String roleId, BehaviourEvent event) {
        return new SceneLaneRef(sceneKey, roleId, Part.REACTION, event);
    }

    /** Rows of the role editor, top to bottom. */
    static List<SceneLaneRef> rows(String sceneKey, String roleId) {
        List<SceneLaneRef> rows = new java.util.ArrayList<>();
        rows.add(lane(sceneKey, roleId));
        rows.add(wrapUp(sceneKey, roleId));
        SceneRole.REACTION_EVENTS.forEach(event -> rows.add(reaction(sceneKey, roleId, event)));
        return List.copyOf(rows);
    }

    List<dev.blockfolk.model.BehaviourAction> actions(SceneRole role) {
        return switch (part) {
            case LANE -> role.getLane();
            case WRAP_UP -> role.getWrapUp();
            case REACTION -> role.getReaction(event);
        };
    }

    void store(SceneRole role, List<dev.blockfolk.model.BehaviourAction> actions) {
        switch (part) {
            case LANE -> role.setLane(actions);
            case WRAP_UP -> role.setWrapUp(actions);
            case REACTION -> role.setReaction(event, actions);
        }
    }
}
