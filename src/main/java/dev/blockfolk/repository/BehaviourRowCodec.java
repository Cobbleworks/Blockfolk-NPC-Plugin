package dev.blockfolk.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.BehaviourRow;
import dev.blockfolk.model.CustomBehaviourRow;

final class BehaviourRowCodec {

    private BehaviourRowCodec() {
    }

    static List<Map<String, Object>> encode(List<BehaviourRow> rows) {
        List<Map<String, Object>> storedRows = new ArrayList<>();
        for (BehaviourRow row : rows) {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("event", row.event().name().toLowerCase(Locale.ROOT));
            stored.put("actions", BehaviourActionCodec.encodeList(row.actions()));
            storedRows.add(stored);
        }
        return storedRows;
    }

    static List<BehaviourRow> decode(Object raw, Consumer<String> warn) {
        if (!(raw instanceof List<?> storedRows))
            return List.of();
        List<BehaviourRow> rows = new ArrayList<>();
        for (Object rawRow : storedRows) {
            if (!(rawRow instanceof Map<?, ?> storedRow))
                continue;
            Object rawEvent = storedRow.get("event");
            if (rawEvent == null)
                continue;
            BehaviourEvent event;
            try {
                event = BehaviourEvent.valueOf(rawEvent.toString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                warn.accept("Ignoring unknown behaviour event '" + rawEvent + "'");
                continue;
            }
            List<BehaviourAction> actions = new ArrayList<>();
            if (storedRow.get("actions") instanceof List<?> storedActions) {
                for (Object rawAction : storedActions) {
                    if (!(rawAction instanceof Map<?, ?> entry))
                        continue;
                    try {
                        actions.add(BehaviourActionCodec.decode(entry));
                    } catch (IllegalArgumentException exception) {
                        warn.accept("Ignoring invalid action in behaviour row for " + event.name());
                    }
                }
            }
            for (int offset = 0; offset < Math.max(1, actions.size()); offset += BehaviourRow.MAX_ACTIONS) {
                int end = Math.min(offset + BehaviourRow.MAX_ACTIONS, actions.size());
                rows.add(new BehaviourRow(event, actions.subList(offset, end)));
            }
        }
        return rows;
    }

    static List<Map<String, Object>> encodeCustom(List<CustomBehaviourRow> rows) {
        List<Map<String, Object>> storedRows = new ArrayList<>();
        for (CustomBehaviourRow row : rows) {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("event", row.eventName());
            stored.put("actions", BehaviourActionCodec.encodeList(row.actions()));
            storedRows.add(stored);
        }
        return storedRows;
    }

    static List<CustomBehaviourRow> decodeCustom(Object raw, Consumer<String> warn) {
        if (!(raw instanceof List<?> storedRows))
            return List.of();
        List<CustomBehaviourRow> rows = new ArrayList<>();
        for (Object rawRow : storedRows) {
            if (!(rawRow instanceof Map<?, ?> storedRow))
                continue;
            Object rawEvent = storedRow.get("event");
            if (!(rawEvent instanceof String eventName) || eventName.isBlank())
                continue;
            List<BehaviourAction> actions = new ArrayList<>();
            if (storedRow.get("actions") instanceof List<?> storedActions) {
                for (Object rawAction : storedActions) {
                    if (!(rawAction instanceof Map<?, ?> entry))
                        continue;
                    try {
                        actions.add(BehaviourActionCodec.decode(entry));
                    } catch (IllegalArgumentException exception) {
                        warn.accept("Ignoring invalid action in custom behaviour row for " + eventName);
                    }
                }
            }
            for (int offset = 0; offset < Math.max(1, actions.size()); offset += BehaviourRow.MAX_ACTIONS) {
                int end = Math.min(offset + BehaviourRow.MAX_ACTIONS, actions.size());
                rows.add(new CustomBehaviourRow(eventName, actions.subList(offset, end)));
            }
        }
        return rows;
    }
}
