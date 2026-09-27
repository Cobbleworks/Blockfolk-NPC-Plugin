package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.CustomEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;

final class CustomEventBrowserModel {
    private static final String NPC_PREFIX = "npc:";
    private static final String UNASSIGNED = "unassigned:";

    private CustomEventBrowserModel() {
    }

    static List<Entry> entries(Collection<CustomEvent> events, Collection<NpcDefinition> definitions,
            Collection<NpcRoute> routes, String folder) {
        Map<String, Set<String>> emitters = emitters(definitions, routes);
        List<CustomEvent> orderedEvents = List.copyOf(events);
        if (folder.isEmpty()) {
            List<Entry> result = new ArrayList<>();
            for (NpcDefinition definition : definitions) {
                int count = (int) orderedEvents.stream()
                        .filter(event -> emitters.getOrDefault(event.getName(), Set.of()).contains(definition.getKey()))
                        .count();
                if (count > 0)
                    result.add(new Entry(true, true, NPC_PREFIX + definition.getKey(), definition.getDisplayName(),
                            count, null));
            }
            int unassigned = (int) orderedEvents.stream()
                    .filter(event -> emitters.getOrDefault(event.getName(), Set.of()).isEmpty()).count();
            if (unassigned > 0)
                result.add(new Entry(true, true, UNASSIGNED, "Unassigned", unassigned, null));
            return result;
        }
        int separator = folder.indexOf('|');
        String group = separator < 0 ? folder : folder.substring(0, separator);
        String path = separator < 0 ? "" : folder.substring(separator + 1);
        String prefix = path.isEmpty() ? "" : path + "/";
        Map<String, Entry> result = new LinkedHashMap<>();
        for (CustomEvent event : orderedEvents) {
            Set<String> owners = emitters.getOrDefault(event.getName(), Set.of());
            boolean included = group.equals(UNASSIGNED)
                    ? owners.isEmpty()
                    : group.startsWith(NPC_PREFIX) && owners.contains(group.substring(NPC_PREFIX.length()));
            if (!included || !event.getName().startsWith(prefix))
                continue;
            String rest = event.getName().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String label = rest.substring(0, slash);
                String child = group + "|" + prefix + label;
                Entry previous = result.get(child);
                result.put(child,
                        new Entry(true, false, child, label, previous == null ? 1 : previous.childCount() + 1, null));
            } else {
                result.put(event.getName(), new Entry(false, false, event.getName(), rest, 0, event));
            }
        }
        return new ArrayList<>(result.values());
    }

    static String parent(String folder) {
        int separator = folder.indexOf('|');
        if (separator < 0)
            return "";
        String group = folder.substring(0, separator);
        String path = folder.substring(separator + 1);
        int slash = path.lastIndexOf('/');
        return slash < 0 ? group : group + "|" + path.substring(0, slash);
    }

    static Map<String, Set<String>> emitters(Collection<NpcDefinition> definitions, Collection<NpcRoute> routes) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (NpcDefinition definition : definitions) {
            Set<String> names = new LinkedHashSet<>();
            for (BehaviourEvent event : BehaviourEvent.values())
                collect(definition.getBehaviourActions(event), names);
            for (String eventName : definition.getCustomEventNames())
                collect(definition.getCustomEventActions(eventName), names);
            for (NpcRoute route : routes) {
                if (route.isOwnedBy(definition.getKey()))
                    route.getPoints().forEach(point -> collect(point.actions(), names));
            }
            for (String name : names)
                result.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(definition.getKey());
        }
        return result;
    }

    private static void collect(List<BehaviourAction> actions, Set<String> names) {
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.EMIT_EVENT && action.value() != null)
                names.add(action.value());
            if (action.type() == BehaviourActionType.ASK_QUESTION && action.question() != null) {
                action.question().options().forEach(option -> collect(option.actions(), names));
                collect(action.question().cancelActions(), names);
            }
        }
    }

    record Entry(boolean folder, boolean npcFolder, String path, String label, int childCount, CustomEvent event) {
    }
}
