package dev.blockfolk.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import dev.blockfolk.runtime.NativeNpcNavigationService;
import dev.blockfolk.runtime.NpcRenderer;

/**
 * Plugin-owned NPCs with no saved presets, automatic attacks, loot, or respawn.
 * Call on the server thread.
 */
public final class TransientNpcService implements Listener {

    private final Plugin plugin;
    private final NpcRenderer renderer;
    private final NativeNpcNavigationService navigation;
    private final Map<UUID, TransientNpc> npcs = new LinkedHashMap<>();

    public TransientNpcService(Plugin plugin, NpcRenderer renderer, NativeNpcNavigationService navigation) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.navigation = navigation;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> new ArrayList<>(npcs.values()).forEach(TransientNpc::tick), 1L, 1L);
    }

    public TransientNpc create(Plugin owner, String name) {
        requireMainThread();
        if (owner == null || !owner.isEnabled())
            throw new IllegalArgumentException("The NPC owner must be enabled.");
        TransientNpc npc = new TransientNpc(this, owner, name, renderer, navigation);
        npcs.put(npc.getId(), npc);
        return npc;
    }

    public int size() {
        return npcs.size();
    }

    boolean contains(TransientNpc npc) {
        return npcs.get(npc.getId()) == npc;
    }

    void remove(TransientNpc npc) {
        npcs.remove(npc.getId());
    }

    Plugin plugin() {
        return plugin;
    }

    static void requireMainThread() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Blockfolk NPC operations require the server thread.");
    }

    @EventHandler
    public void onOwnerDisabled(PluginDisableEvent event) {
        new ArrayList<>(npcs.values()).stream().filter(npc -> npc.owner() == event.getPlugin())
                .forEach(TransientNpc::destroy);
    }

    public void shutdown() {
        new ArrayList<>(npcs.values()).forEach(TransientNpc::destroy);
    }
}
