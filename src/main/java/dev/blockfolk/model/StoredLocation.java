package dev.blockfolk.model;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;

/** A location that retains its world name while that world is unloaded. */
public record StoredLocation(String worldName, double x, double y, double z, float yaw, float pitch) {

    public StoredLocation {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("World name is required");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Location coordinates and rotation must be finite");
        }
        worldName = worldName.trim();
    }

    public static StoredLocation from(Location location) {
        Objects.requireNonNull(location, "location");
        String worldName = location.getWorld() == null ? "__unknown__" : location.getWorld().getName();
        return new StoredLocation(worldName, location.getX(), location.getY(), location.getZ(), location.getYaw(),
                location.getPitch());
    }

    public Location toLocation() {
        org.bukkit.World world = Bukkit.getServer() == null || worldName.equals("__unknown__")
                ? null
                : Bukkit.getWorld(worldName);
        return new Location(world, x, y, z, yaw, pitch);
    }
}
