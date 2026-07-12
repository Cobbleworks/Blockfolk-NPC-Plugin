package dev.blockfolk.model;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public record RoutePoint(String worldName, int x, int y, int z, List<BehaviourAction> actions) {

    public RoutePoint {
        Objects.requireNonNull(worldName, "worldName");
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public RoutePoint(String worldName, int x, int y, int z) {
        this(worldName, x, y, z, List.of());
    }

    public static RoutePoint fromBlock(Block block) {
        return new RoutePoint(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public boolean isSameBlock(RoutePoint other) {
        return worldName.equals(other.worldName) && x == other.x && y == other.y && z == other.z;
    }

    public RoutePoint withActions(List<BehaviourAction> actions) {
        return new RoutePoint(worldName, x, y, z, actions);
    }

    public Location toWalkingLocation() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }

    public double distanceSquared(Location location) {
        if (location.getWorld() == null || !worldName.equals(location.getWorld().getName())) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x + 0.5 - location.getX();
        double dy = y + 1.0 - location.getY();
        double dz = z + 0.5 - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceSquared(RoutePoint other) {
        if (!worldName.equals(other.worldName)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
