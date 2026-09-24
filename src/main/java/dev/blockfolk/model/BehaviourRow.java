package dev.blockfolk.model;

import java.util.List;
import java.util.Objects;

/** One editable event row, with up to seven actions. */
public record BehaviourRow(BehaviourEvent event, List<BehaviourAction> actions) {

    public static final int MAX_ACTIONS = 7;

    public BehaviourRow {
        Objects.requireNonNull(event, "event");
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (actions.size() > MAX_ACTIONS)
            throw new IllegalArgumentException("A behaviour row may have at most seven actions");
    }
}
