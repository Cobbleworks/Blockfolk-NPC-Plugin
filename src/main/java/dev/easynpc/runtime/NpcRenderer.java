package dev.easynpc.runtime;

import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import org.bukkit.entity.Player;

public interface NpcRenderer {
    void start();

    void stop();

    void spawnFor(Player player, NpcInstance instance, NpcDefinition definition);

    void destroyFor(Player player, NpcInstance instance);

    void destroyForAll(NpcInstance instance);

    void refresh(NpcInstance instance, NpcDefinition definition);
}
