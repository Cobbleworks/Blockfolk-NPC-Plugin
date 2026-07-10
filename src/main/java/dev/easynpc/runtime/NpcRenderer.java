package dev.easynpc.runtime;

import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import org.bukkit.Location;

public interface NpcRenderer {
    void start();

    void stop();

    void spawn(NpcInstance instance, NpcDefinition definition);

    void destroy(NpcInstance instance);

    void refresh(NpcInstance instance, NpcDefinition definition);

    boolean move(NpcInstance instance, Location location);
}
