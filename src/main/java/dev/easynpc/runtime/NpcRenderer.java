package dev.easynpc.runtime;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;

public interface NpcRenderer {

    void start();

    void stop();

    void spawn(NpcInstance instance, NpcDefinition definition);

    void destroy(NpcInstance instance);

    void refresh(NpcInstance instance, NpcDefinition definition);

    boolean move(NpcInstance instance, Location location);

    Optional<LivingEntity> findLivingEntity(NpcInstance instance);
}
