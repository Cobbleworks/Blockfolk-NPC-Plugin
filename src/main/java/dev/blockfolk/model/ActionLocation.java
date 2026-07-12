package dev.blockfolk.model;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A compact, YAML-friendly location stored as a behaviour action value. */
public record ActionLocation(String worldName, double x, double y, double z) {

    public ActionLocation {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("World name is required");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Location coordinates must be finite");
        }
    }

    public static ActionLocation above(Block block) {
        return new ActionLocation(block.getWorld().getName(),
                block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5);
    }

    public String serialize() {
        return worldName + "|" + x + "|" + y + "|" + z;
    }

    public static Optional<ActionLocation> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ActionLocation(parts[0], Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    public String display() {
        return worldName + " (" + coordinate(x) + ", " + coordinate(y) + ", " + coordinate(z) + ")";
    }

    private static String coordinate(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }
}
