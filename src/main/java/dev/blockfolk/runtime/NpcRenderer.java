package dev.blockfolk.runtime;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pose;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;

public interface NpcRenderer {

    void start();

    void stop();

    boolean spawn(NpcInstance instance, NpcDefinition definition);

    void destroy(NpcInstance instance);

    default void destroyPermanently(NpcInstance instance) {
        destroy(instance);
    }

    void refresh(NpcInstance instance, NpcDefinition definition);

    boolean move(NpcInstance instance, Location location);

    default Optional<Location> currentLocation(NpcInstance instance) {
        return Optional.empty();
    }

    Optional<LivingEntity> findLivingEntity(NpcInstance instance);

    /**
     * The minecart or boat the NPC is riding, if any. The sitting seat is not a
     * vehicle.
     */
    default Optional<Entity> vehicle(NpcInstance instance) {
        return Optional.empty();
    }

    /**
     * Seats the NPC in a minecart or boat. Returns false when the vehicle refused
     * it.
     */
    default boolean board(NpcInstance instance, Entity vehicle) {
        return false;
    }

    /** Leaves the ridden minecart or boat at a safe dismount spot. */
    default boolean leaveVehicle(NpcInstance instance) {
        return false;
    }

    void pose(NpcInstance instance, Pose pose);

    default void sit(NpcInstance instance) {
    }

    void stand(NpcInstance instance);

    void wave(NpcInstance instance);

    void jump(NpcInstance instance);

    void lookAt(NpcInstance instance, Location target);
}
