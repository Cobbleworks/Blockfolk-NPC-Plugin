package dev.blockfolk.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import dev.blockfolk.model.StoredLocation;

public final class LocationCodec {

    private LocationCodec() {
    }

    public static void write(ConfigurationSection section, Location location) {
        section.set("world", location.getWorld() == null ? null : location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    public static void write(ConfigurationSection section, StoredLocation location) {
        section.set("world", location.worldName());
        section.set("x", location.x());
        section.set("y", location.y());
        section.set("z", location.z());
        section.set("yaw", location.yaw());
        section.set("pitch", location.pitch());
    }

    public static StoredLocation readStored(ConfigurationSection section) {
        if (section == null) return null;
        String worldName = section.getString("world");
        if (worldName == null) return null;
        try {
            return new StoredLocation(worldName, section.getDouble("x"), section.getDouble("y"),
                    section.getDouble("z"), (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static Location read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }
        StoredLocation stored = readStored(section);
        if (stored == null) return null;
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : stored.toLocation();
    }
}
