package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One step of a scene. Every role plays its actions for the beat at the same
 * time; once the beat's advance rule is met, the next beat starts. A role
 * without actions in a beat holds still while the others play.
 */
public final class SceneBeat {

    public static final int DEFAULT_SECONDS = 10;
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 60 * 60;

    /**
     * Stable while beats are added, moved, or removed, so open menus stay on it.
     */
    private final String id;
    private SceneBeatAdvance advance = SceneBeatAdvance.EVERYONE_DONE;
    private int seconds = DEFAULT_SECONDS;
    private String advanceRoleId;
    private final Map<String, List<BehaviourAction>> actions = new LinkedHashMap<>();

    public SceneBeat() {
        this(null);
    }

    public SceneBeat(String id) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString().substring(0, 8) : id.trim();
    }

    public String getId() {
        return id;
    }

    public SceneBeatAdvance getAdvance() {
        return advance;
    }

    public void setAdvance(SceneBeatAdvance advance) {
        this.advance = advance == null ? SceneBeatAdvance.EVERYONE_DONE : advance;
    }

    /**
     * Seconds before the beat moves on, for {@link SceneBeatAdvance#AFTER_TIME}.
     */
    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = Math.clamp(seconds, MIN_SECONDS, MAX_SECONDS);
    }

    /**
     * The role whose actions end the beat, for {@link SceneBeatAdvance#ROLE_DONE}.
     */
    public String getAdvanceRoleId() {
        return advanceRoleId;
    }

    public void setAdvanceRoleId(String roleId) {
        this.advanceRoleId = roleId == null || roleId.isBlank() ? null : SceneRole.normalizeId(roleId);
    }

    public List<BehaviourAction> getActions(String roleId) {
        return new ArrayList<>(actions.getOrDefault(roleId, List.of()));
    }

    public void setActions(String roleId, List<BehaviourAction> roleActions) {
        if (roleActions == null || roleActions.isEmpty())
            actions.remove(roleId);
        else
            actions.put(roleId, SceneRole.limited(roleActions));
    }

    public boolean hasActions(String roleId) {
        return actions.containsKey(roleId);
    }

    /** Role ids with actions in this beat, in the order they were added. */
    public List<String> actingRoles() {
        return List.copyOf(actions.keySet());
    }

    /** Forgets a removed role: its actions, and the advance rule naming it. */
    void forgetRole(String roleId) {
        actions.remove(roleId);
        if (roleId.equals(advanceRoleId)) {
            advanceRoleId = null;
            if (advance == SceneBeatAdvance.ROLE_DONE)
                advance = SceneBeatAdvance.EVERYONE_DONE;
        }
    }

    /** A copy with a new id, for duplicating beats. */
    public SceneBeat copy() {
        SceneBeat copy = new SceneBeat();
        copy.advance = advance;
        copy.seconds = seconds;
        copy.advanceRoleId = advanceRoleId;
        actions.forEach((roleId, roleActions) -> copy.actions.put(roleId, new ArrayList<>(roleActions)));
        return copy;
    }

    boolean mapActions(java.util.function.UnaryOperator<BehaviourAction> mapper) {
        boolean changed = false;
        for (Map.Entry<String, List<BehaviourAction>> entry : actions.entrySet()) {
            List<BehaviourAction> mapped = BehaviourActions.map(entry.getValue(), mapper);
            if (!mapped.equals(entry.getValue())) {
                entry.setValue(new ArrayList<>(mapped));
                changed = true;
            }
        }
        return changed;
    }

    boolean removeActions(java.util.function.Predicate<BehaviourAction> filter) {
        boolean changed = false;
        for (Map.Entry<String, List<BehaviourAction>> entry : actions.entrySet()) {
            List<BehaviourAction> kept = BehaviourActions.removeIf(entry.getValue(), filter);
            if (!kept.equals(entry.getValue())) {
                entry.setValue(new ArrayList<>(kept));
                changed = true;
            }
        }
        actions.values().removeIf(List::isEmpty);
        return changed;
    }

    void forEachAction(java.util.function.Consumer<BehaviourAction> visitor) {
        actions.values().forEach(roleActions -> BehaviourActions.forEach(roleActions, visitor));
    }
}
