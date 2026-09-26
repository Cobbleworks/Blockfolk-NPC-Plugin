package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;

import org.bukkit.inventory.ItemStack;

public final class NpcRoute {

    private static final Comparator<RoutePoint> POINT_ORDER = Comparator.comparing(RoutePoint::worldName)
            .thenComparingInt(RoutePoint::x).thenComparingInt(RoutePoint::y).thenComparingInt(RoutePoint::z);

    private final String key;
    private String displayName;
    private String ownerKey;
    private ItemStack icon;
    private final List<RoutePoint> points = new ArrayList<>();

    public NpcRoute(String key) {
        this.key = normalizeKey(key);
        this.displayName = this.key;
    }

    public static NpcRoute create(String displayName) {
        NpcRoute route = new NpcRoute(displayName);
        route.setDisplayName(displayName);
        return route;
    }

    public static String normalizeKey(String value) {
        String normalized = Objects.requireNonNull(value, "route name").trim();
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.endsWith("/")
                || normalized.contains("//")) {
            throw new IllegalArgumentException(
                    "Route names may contain letters, numbers, _ and -, with / between groups");
        }
        List<String> groups = new ArrayList<>();
        for (String group : normalized.split("/")) {
            String trimmed = group.trim();
            if (trimmed.equals(".") || trimmed.equals("..")) {
                throw new IllegalArgumentException(
                        "Route names may contain letters, numbers, _ and -, with / between groups");
            }
            groups.add(NpcDefinition.toKey(trimmed));
        }
        return String.join("/", groups);
    }

    public String getKey() {
        return key;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public void setOwnerKey(String ownerKey) {
        this.ownerKey = ownerKey == null ? null : NpcDefinition.toKey(ownerKey);
    }

    public boolean isOwnedBy(String npcKey) {
        return ownerKey != null && ownerKey.equals(NpcDefinition.toKey(npcKey));
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = Objects.requireNonNullElse(displayName, key).trim();
        if (this.displayName.isBlank()) {
            this.displayName = key;
        }
    }

    public ItemStack getIcon() {
        return icon == null ? null : icon.clone();
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon == null || icon.getType().isAir() ? null : icon.clone();
    }

    public List<RoutePoint> getPoints() {
        return List.copyOf(points);
    }

    public Set<String> getReferencedRouteKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (RoutePoint point : points)
            collectRouteKeys(point.actions(), keys);
        return Set.copyOf(keys);
    }

    private static void collectRouteKeys(List<BehaviourAction> actions, Set<String> keys) {
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.SET_ROUTE && action.value() != null) {
                try {
                    keys.add(normalizeKey(action.value()));
                } catch (IllegalArgumentException ignored) {
                }
            } else if (action.type() == BehaviourActionType.ASK_QUESTION && action.question() != null) {
                action.question().options().forEach(option -> collectRouteKeys(option.actions(), keys));
                collectRouteKeys(action.question().cancelActions(), keys);
            }
        }
    }

    public void replaceRouteReferences(String oldKey, String newKey) {
        for (int index = 0; index < points.size(); index++) {
            RoutePoint point = points.get(index);
            points.set(index, point.withActions(replaceRoute(point.actions(), oldKey, newKey)));
        }
    }

    private static boolean matchesRoute(String value, String key) {
        try {
            return NpcRoute.normalizeKey(value).equals(key);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static List<BehaviourAction> replaceRoute(List<BehaviourAction> actions, String oldKey, String newKey) {
        List<BehaviourAction> result = new ArrayList<>();
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.SET_ROUTE && action.value() != null
                    && matchesRoute(action.value(), oldKey)) {
                result.add(new BehaviourAction(action.type(), newKey));
            } else if (action.type() == BehaviourActionType.ASK_QUESTION && action.question() != null) {
                NpcQuestion question = action.question();
                List<QuestionOption> options = question.options().stream()
                        .map(option -> option.withActions(replaceRoute(option.actions(), oldKey, newKey))).toList();
                result.add(BehaviourAction.ask(new NpcQuestion(question.id(), question.prompt(), options,
                        replaceRoute(question.cancelActions(), oldKey, newKey))));
            } else {
                result.add(action);
            }
        }
        return result;
    }

    public boolean addPoint(RoutePoint point) {
        Objects.requireNonNull(point, "point");
        if (!points.isEmpty() && !points.getFirst().worldName().equals(point.worldName())) {
            throw new IllegalArgumentException("All route points must be in the same world.");
        }
        if (findPoint(point).isPresent()) {
            return false;
        }
        points.add(point);
        return true;
    }

    public boolean removePoint(RoutePoint point) {
        return points.removeIf(existing -> existing.isSameBlock(point));
    }

    public Optional<RoutePoint> findPoint(RoutePoint point) {
        return points.stream().filter(existing -> existing.isSameBlock(point)).findFirst();
    }

    public boolean replacePoint(RoutePoint existing, RoutePoint replacement) {
        int index = points.indexOf(existing);
        if (index < 0 || !existing.isSameBlock(replacement)) {
            return false;
        }
        points.set(index, replacement);
        return true;
    }

    /**
     * Computes an order without relying on placement order: start at the point
     * nearest the NPC, then repeatedly visit the nearest unvisited point. The
     * movement task closes the loop from the last result back to the first.
     */
    public List<RoutePoint> logicallyOrdered(Location origin) {
        if (points.isEmpty()) {
            return List.of();
        }
        List<RoutePoint> remaining = new ArrayList<>(points);
        RoutePoint current = remaining.stream().min(Comparator
                .comparingDouble((RoutePoint point) -> point.distanceSquared(origin)).thenComparing(POINT_ORDER))
                .orElseThrow();
        List<RoutePoint> ordered = new ArrayList<>();
        ordered.add(current);
        remaining.remove(current);
        while (!remaining.isEmpty()) {
            RoutePoint previous = current;
            current = remaining.stream().min(Comparator
                    .comparingDouble((RoutePoint point) -> previous.distanceSquared(point)).thenComparing(POINT_ORDER))
                    .orElseThrow();
            ordered.add(current);
            remaining.remove(current);
        }
        return ordered;
    }
}
