package dev.blockfolk.model;

import java.util.List;

/** One editable custom-event row, with up to seven actions. */
public record CustomBehaviourRow(String eventName, List<BehaviourAction> actions) {

    public CustomBehaviourRow {
        if (eventName == null || eventName.isBlank())
            throw new IllegalArgumentException("Custom event name is required");
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (actions.size() > BehaviourRow.MAX_ACTIONS)
            throw new IllegalArgumentException("A custom behaviour row may have at most seven actions");
    }
}
