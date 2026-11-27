package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.Scene;
import dev.blockfolk.model.SceneBeat;
import dev.blockfolk.model.SceneRole;

/**
 * Points the shared action editor at an action row other than preset
 * behaviour: a scene role's actions in one beat, its wrap-up, or one of its
 * reactions, or a variant's own row for one event. Beats are named by their
 * stable id, so the row stays right when beats are moved. (Preset behaviour
 * rows have no lane.)
 */
record ActionLaneRef(String sceneKey, String roleId, Part part, String beatId, BehaviourEvent event,
        UUID variantId) {

    enum Part {
        BEAT, WRAP_UP, REACTION, VARIANT
    }

    static ActionLaneRef beat(String sceneKey, String roleId, String beatId) {
        return new ActionLaneRef(sceneKey, roleId, Part.BEAT, beatId, null, null);
    }

    static ActionLaneRef wrapUp(String sceneKey, String roleId) {
        return new ActionLaneRef(sceneKey, roleId, Part.WRAP_UP, null, null, null);
    }

    static ActionLaneRef reaction(String sceneKey, String roleId, BehaviourEvent event) {
        return new ActionLaneRef(sceneKey, roleId, Part.REACTION, null, event, null);
    }

    /** A variant's own row for {@code event}, edited in place of the preset's. */
    static ActionLaneRef variant(UUID variantId, BehaviourEvent event) {
        return new ActionLaneRef(null, null, Part.VARIANT, null, event, variantId);
    }

    boolean isVariant() {
        return part == Part.VARIANT;
    }

    /**
     * Rows of the role editor, top to bottom: wrap-up, then reactions. Beat rows
     * are edited one at a time from the script.
     */
    static List<ActionLaneRef> rows(Scene scene, String roleId) {
        List<ActionLaneRef> rows = new ArrayList<>();
        rows.add(wrapUp(scene.getKey(), roleId));
        SceneRole.REACTION_EVENTS.forEach(event -> rows.add(reaction(scene.getKey(), roleId, event)));
        return List.copyOf(rows);
    }

    List<BehaviourAction> actions(Scene scene, SceneRole role) {
        return switch (part) {
            case BEAT -> scene.findBeat(beatId).map(beat -> beat.getActions(role.getId())).orElseGet(ArrayList::new);
            case WRAP_UP -> role.getWrapUp();
            case REACTION -> role.getReaction(event);
            case VARIANT -> throw new IllegalStateException("Variant rows belong to a variant, not a scene");
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
            case VARIANT -> throw new IllegalStateException("Variant rows belong to a variant, not a scene");
        }
    }
}
