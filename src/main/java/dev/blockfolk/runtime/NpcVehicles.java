package dev.blockfolk.runtime;

import java.util.Locale;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.RideableMinecart;

/**
 * Vehicles an NPC can ride: rideable minecarts and every boat or raft. The
 * invisible armor stand used for the sitting pose is deliberately excluded.
 */
public final class NpcVehicles {

    private NpcVehicles() {
    }

    public static boolean isTransport(Entity entity) {
        return entity instanceof RideableMinecart || entity instanceof Boat;
    }

    /** Whether an auto-boarding NPC may take this vehicle: valid and unoccupied. */
    public static boolean isBoardable(Entity entity) {
        return isTransport(entity) && entity.isValid() && entity.getPassengers().isEmpty();
    }

    /** A readable kind such as "minecart", "oak boat", or "bamboo chest raft". */
    public static String describe(Entity entity) {
        return readable(entity.getType().name());
    }

    static String readable(String typeName) {
        return typeName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
