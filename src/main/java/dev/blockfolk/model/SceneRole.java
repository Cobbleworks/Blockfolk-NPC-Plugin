package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One part in a scene, played by every free instance of an NPC preset. NPCs
 * play themselves, so a role has no name of its own. What a role does in each
 * step lives in the scene's {@link SceneBeat beats}; the role itself holds what
 * it does when the scene ends (its wrap-up) and reactions that replace the
 * preset's own behaviour while the scene runs.
 */
public final class SceneRole {

    public static final int MAX_ACTIONS = 7;
    /** Events a role may react to differently while its scene runs. */
    public static final List<BehaviourEvent> REACTION_EVENTS = List.of(BehaviourEvent.IDLE, BehaviourEvent.RIGHT_CLICK,
            BehaviourEvent.PLAYER_APPROACH, BehaviourEvent.PLAYER_LEAVES, BehaviourEvent.LEFT_CLICK,
            BehaviourEvent.RECEIVE_ITEM, BehaviourEvent.NPC_NEARBY, BehaviourEvent.DAMAGE_TAKEN);

    private final String id;
    private String presetKey;
    private List<BehaviourAction> wrapUp = new ArrayList<>();
    private final Map<BehaviourEvent, List<BehaviourAction>> reactions = new EnumMap<>(BehaviourEvent.class);

    public SceneRole(String id, String presetKey) {
        this.id = normalizeId(id);
        setPresetKey(presetKey);
    }

    public static String normalizeId(String value) {
        String key = NpcDefinition.toKey(Objects.requireNonNull(value, "role id"));
        return key.toLowerCase(Locale.ROOT);
    }

    public String getId() {
        return id;
    }

    public String getPresetKey() {
        return presetKey;
    }

    public void setPresetKey(String presetKey) {
        this.presetKey = presetKey == null || presetKey.isBlank() ? null : NpcDefinition.toKey(presetKey);
    }

    public List<BehaviourAction> getWrapUp() {
        return new ArrayList<>(wrapUp);
    }

    public void setWrapUp(List<BehaviourAction> actions) {
        wrapUp = limited(actions);
    }

    public List<BehaviourAction> getReaction(BehaviourEvent event) {
        return new ArrayList<>(reactions.getOrDefault(event, List.of()));
    }

    public void setReaction(BehaviourEvent event, List<BehaviourAction> actions) {
        if (!REACTION_EVENTS.contains(event))
            throw new IllegalArgumentException(event.displayName() + " cannot be changed by a scene.");
        if (actions == null || actions.isEmpty())
            reactions.remove(event);
        else
            reactions.put(event, limited(actions));
    }

    public Map<BehaviourEvent, List<BehaviourAction>> getReactions() {
        Map<BehaviourEvent, List<BehaviourAction>> copy = new EnumMap<>(BehaviourEvent.class);
        reactions.forEach((event, actions) -> copy.put(event, List.copyOf(actions)));
        return copy;
    }

    /** Rewrites every action, including question branches. */
    public boolean mapActions(java.util.function.UnaryOperator<BehaviourAction> mapper) {
        boolean changed = false;
        List<BehaviourAction> mappedWrapUp = BehaviourActions.map(wrapUp, mapper);
        if (!mappedWrapUp.equals(wrapUp)) {
            wrapUp = new ArrayList<>(mappedWrapUp);
            changed = true;
        }
        for (Map.Entry<BehaviourEvent, List<BehaviourAction>> entry : reactions.entrySet()) {
            List<BehaviourAction> mapped = BehaviourActions.map(entry.getValue(), mapper);
            if (!mapped.equals(entry.getValue())) {
                entry.setValue(new ArrayList<>(mapped));
                changed = true;
            }
        }
        return changed;
    }

    /** Drops every matching action, including inside question branches. */
    public boolean removeActions(java.util.function.Predicate<BehaviourAction> filter) {
        int before = countAll();
        wrapUp = new ArrayList<>(BehaviourActions.removeIf(wrapUp, filter));
        reactions.replaceAll((event, actions) -> new ArrayList<>(BehaviourActions.removeIf(actions, filter)));
        reactions.values().removeIf(List::isEmpty);
        return countAll() != before;
    }

    private int countAll() {
        int[] count = {0};
        forEachAction(action -> count[0]++);
        return count[0];
    }

    /** Visits every action, including question branches. */
    public void forEachAction(java.util.function.Consumer<BehaviourAction> visitor) {
        BehaviourActions.forEach(wrapUp, visitor);
        reactions.values().forEach(actions -> BehaviourActions.forEach(actions, visitor));
    }

    static List<BehaviourAction> limited(List<BehaviourAction> actions) {
        if (actions == null)
            return new ArrayList<>();
        return new ArrayList<>(actions.subList(0, Math.min(actions.size(), MAX_ACTIONS)));
    }
}
