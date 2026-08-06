package dev.blockfolk.runtime;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pose;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;

public interface NpcRenderer {

    void start();

    void stop();

    boolean spawn(NpcInstance instance, NpcDefinition definition);

    void destroy(NpcInstance instance);

    default void destroyPermanently(NpcInstance instance) { destroy(instance); }

    void refresh(NpcInstance instance, NpcDefinition definition);

    boolean move(NpcInstance instance, Location location);

    default Optional<Location> currentLocation(NpcInstance instance) {
        return Optional.empty();
    }

    Optional<LivingEntity> findLivingEntity(NpcInstance instance);

    void pose(NpcInstance instance, Pose pose);

    void stand(NpcInstance instance);

    void wave(NpcInstance instance);

    void jump(NpcInstance instance);

    void lookAt(NpcInstance instance, Location target);
}
