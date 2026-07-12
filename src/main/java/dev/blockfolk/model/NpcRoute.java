package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public final class NpcRoute {

    private static final Comparator<RoutePoint> POINT_ORDER = Comparator
            .comparing(RoutePoint::worldName)
            .thenComparingInt(RoutePoint::x)
            .thenComparingInt(RoutePoint::y)
            .thenComparingInt(RoutePoint::z);

    private final String key;
    private String displayName;
    private ItemStack icon;
    private final List<RoutePoint> points = new ArrayList<>();

    public NpcRoute(String key) {
        this.key = NpcDefinition.toKey(key);
        this.displayName = this.key;
    }

    public static NpcRoute create(String displayName) {
        NpcRoute route = new NpcRoute(displayName);
        route.setDisplayName(displayName);
        return route;
    }

    public String getKey() {
        return key;
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

    public void setPoints(List<RoutePoint> points) {
        this.points.clear();
        if (points == null) {
            return;
        }
        for (RoutePoint point : points) {
            addPoint(point);
        }
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
        RoutePoint current = remaining.stream()
                .min(Comparator.comparingDouble((RoutePoint point) -> point.distanceSquared(origin)).thenComparing(POINT_ORDER))
                .orElseThrow();
        List<RoutePoint> ordered = new ArrayList<>();
        ordered.add(current);
        remaining.remove(current);
        while (!remaining.isEmpty()) {
            RoutePoint previous = current;
            current = remaining.stream()
                    .min(Comparator.comparingDouble((RoutePoint point) -> previous.distanceSquared(point)).thenComparing(POINT_ORDER))
                    .orElseThrow();
            ordered.add(current);
            remaining.remove(current);
        }
        return ordered;
    }
}
