package dev.easynpc.input;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;

public final class ChatInputService implements Listener {

    private final Plugin plugin;
    private final int timeoutSeconds;
    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();

    public ChatInputService(Plugin plugin, int timeoutSeconds) {
        this.plugin = plugin;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void request(Player player, String prompt, Consumer<String> consumer) {
        cancel(player.getUniqueId(), false);
        player.closeInventory();
        player.sendMessage(Component.text(prompt));
        player.sendMessage(Component.text("Type cancel to stop."));
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInputs.remove(player.getUniqueId()) != null && player.isOnline()) {
                player.sendMessage(Component.text("Input timed out."));
            }
        }, timeoutSeconds * 20L);
        pendingInputs.put(player.getUniqueId(), new PendingInput(consumer, timeout));
    }

    public void cancelAll() {
        for (PendingInput input : pendingInputs.values()) {
            input.timeout.cancel();
        }
        pendingInputs.clear();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput input = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        input.timeout.cancel();
        String message = event.getMessage();
        if (message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().sendMessage(Component.text("Input cancelled.")));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> input.consumer.accept(message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), true);
    }

    private void cancel(UUID playerId, boolean silent) {
        PendingInput existing = pendingInputs.remove(playerId);
        if (existing != null) {
            existing.timeout.cancel();
        }
    }

    private record PendingInput(Consumer<String> consumer, BukkitTask timeout) {

    }
}
