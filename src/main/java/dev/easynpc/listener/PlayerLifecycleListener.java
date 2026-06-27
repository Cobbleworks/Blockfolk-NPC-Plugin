package dev.easynpc.listener;

import dev.easynpc.runtime.NpcInstanceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public final class PlayerLifecycleListener implements Listener {
    private final Plugin plugin;
    private final NpcInstanceRegistry registry;

    public PlayerLifecycleListener(Plugin plugin, NpcInstanceRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> registry.renderFor(event.getPlayer()),
            20L
        );
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        registry.renderFor(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> registry.renderFor(event.getPlayer()),
            20L
        );
    }
}
