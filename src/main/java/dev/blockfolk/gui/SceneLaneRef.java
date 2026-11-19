package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.List;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.Scene;
import dev.blockfolk.model.SceneBeat;
import dev.blockfolk.model.SceneRole;

/**
 * Points the shared action editor at one action row of a scene role: its
 * actions in one beat, its wrap-up, or one of its reactions. Beats are named by
 * their stable id, so the row stays right when beats are moved.
 */
record SceneLaneRef(String sceneKey, String roleId, Part part, String beatId, BehaviourEvent event) {

    enum Part {
        BEAT, WRAP_UP, REACTION
    }

    static SceneLaneRef beat(String sceneKey, String roleId, String beatId) {
        return new SceneLaneRef(sceneKey, roleId, Part.BEAT, beatId, null);
    }

    static SceneLaneRef wrapUp(String sceneKey, String roleId) {
        return new SceneLaneRef(sceneKey, roleId, Part.WRAP_UP, null, null);
    }

    static SceneLaneRef reaction(String sceneKey, String roleId, BehaviourEvent event) {
        return new SceneLaneRef(sceneKey, roleId, Part.REACTION, null, event);
    }

    /**
     * Rows of the role editor, top to bottom: wrap-up, then reactions. Beat rows
     * are edited one at a time from the script.
     */
    static List<SceneLaneRef> rows(Scene scene, String roleId) {
        List<SceneLaneRef> rows = new ArrayList<>();
        rows.add(wrapUp(scene.getKey(), roleId));
        SceneRole.REACTION_EVENTS.forEach(event -> rows.add(reaction(scene.getKey(), roleId, event)));
        return List.copyOf(rows);
    }

    List<BehaviourAction> actions(Scene scene, SceneRole role) {
        return switch (part) {
            case BEAT -> scene.findBeat(beatId).map(beat -> beat.getActions(role.getId())).orElseGet(ArrayList::new);
            case WRAP_UP -> role.getWrapUp();
            case REACTION -> role.getReaction(event);
        };
    }

    void store(Scene scene, SceneRole role, List<BehaviourAction> actions) {
        switch (part) {
            case BEAT -> {
                SceneBeat beat = scene.findBeat(beatId).orElse(null);
                if (beat != null)
                    beat.setActions(role.getId(), actions);
            }
            case WRAP_UP -> role.setWrapUp(actions);
            case REACTION -> role.setReaction(event, actions);
        }
    }
}
