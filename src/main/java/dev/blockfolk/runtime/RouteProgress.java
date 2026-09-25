package dev.blockfolk.runtime;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.bukkit.Location;

import dev.blockfolk.model.RoutePoint;

/**
 * Tracks a route's next target and chooses a new approach after a failed leg.
 */
final class RouteProgress {

    private static final int RETRY_TICKS = 5 * 20;

    private final String routeKey;
    private final List<RoutePoint> sourcePoints;
    private final List<RoutePoint> orderedPoints;
    private final Set<Integer> failedTargets = new HashSet<>();
    private int targetIndex;
    private int lastReachedIndex = -1;
    private int direction = 1;
    private int retryTicks;
    private boolean targetHandled;

    RouteProgress(String routeKey, List<RoutePoint> sourcePoints, List<RoutePoint> orderedPoints) {
        if (orderedPoints.isEmpty()) {
            throw new IllegalArgumentException("A route needs at least one point");
        }
        this.routeKey = routeKey;
        this.sourcePoints = List.copyOf(sourcePoints);
        this.orderedPoints = List.copyOf(orderedPoints);
    }

    boolean matches(String candidateRouteKey, List<RoutePoint> candidatePoints) {
        return routeKey.equals(candidateRouteKey) && sourcePoints.equals(candidatePoints);
    }

    RoutePoint targetPoint() {
        return orderedPoints.get(targetIndex);
    }

    boolean targetHandled() {
        return targetHandled;
    }

    void setTargetPending() {
        targetHandled = false;
    }

    boolean ready(Location current) {
        if (retryTicks == 0) {
            return true;
        }
        if (--retryTicks > 0) {
            return false;
        }
        failedTargets.clear();
        chooseNearest(current);
        return true;
    }

    void arrived() {
        lastReachedIndex = targetIndex;
        failedTargets.clear();
        targetIndex = Math.floorMod(targetIndex + direction, orderedPoints.size());
        targetHandled = targetIndex == lastReachedIndex;
    }

    void stalled(Location current) {
        failedTargets.add(targetIndex);
        if (!chooseNearest(current)) {
            // All alternatives failed. Wait before trying them again, so an
            // obstructed route cannot request a new path every server tick.
            retryTicks = RETRY_TICKS;
        }
    }

    private boolean chooseNearest(Location current) {
        int previous = lastReachedIndex;
        int candidate = IntStream.range(0, orderedPoints.size())
                .filter(index -> index != previous && !failedTargets.contains(index)).boxed()
                .min(Comparator.comparingDouble((Integer index) -> orderedPoints.get(index).distanceSquared(current))
                        .thenComparingInt(Integer::intValue))
                .orElse(-1);
        if (candidate < 0) {
            return false;
        }
        if (previous >= 0) {
            int count = orderedPoints.size();
            int forward = Math.floorMod(candidate - previous, count);
            int backward = Math.floorMod(previous - candidate, count);
            if (forward < backward) {
                direction = 1;
            } else if (backward < forward) {
                direction = -1;
            }
        }
        targetIndex = candidate;
        targetHandled = false;
        return true;
    }
}
