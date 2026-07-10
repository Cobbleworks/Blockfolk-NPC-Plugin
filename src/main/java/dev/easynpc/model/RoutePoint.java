package dev.easynpc.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

public record RoutePoint(String worldName, int x, int y, int z) {
    public RoutePoint {
        Objects.requireNonNull(worldName, "worldName");
    }

    public static RoutePoint fromBlock(Block block) {
        return new RoutePoint(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
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
