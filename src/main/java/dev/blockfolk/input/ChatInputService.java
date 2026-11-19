package dev.blockfolk.input;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.util.UiText;

public final class ChatInputService implements Listener {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final Plugin plugin;
    private final int timeoutSeconds;
    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();
    private final Set<UUID> requestingInputs = new HashSet<>();
    private Consumer<Player> beforeRequest = ignored -> {
    };

    public ChatInputService(Plugin plugin, int timeoutSeconds) {
        this.plugin = plugin;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void request(Player player, String prompt, Consumer<String> consumer) {
        request(player, prompt, consumer, null);
    }

    public void request(Player player, String prompt, Consumer<String> consumer, Runnable onCancel) {
        UUID playerId = player.getUniqueId();
        requestingInputs.add(playerId);
        try {
            beforeRequest.accept(player);
            cancel(playerId, false);
            player.closeInventory();
            player.sendMessage(UiText.prompt(prompt));
            player.sendMessage(UiText.info("Type 'cancel' to stop."));
            BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pendingInputs.remove(playerId) != null && player.isOnline()) {
                    player.sendMessage(UiText.warning("Input timed out."));
                    if (onCancel != null)
                        onCancel.run();
                }
            }, timeoutSeconds * 20L);
            pendingInputs.put(playerId, new PendingInput(consumer, onCancel, timeout));
        } finally {
            requestingInputs.remove(playerId);
        }
    }

    /**
     * Like {@link #request(Player, String, Consumer, Runnable)}, but also lists
     * {@code choices} as clickable chat links. Clicking one answers the prompt as
     * if it had been typed; typing any other value still works.
     */
    public void request(Player player, String prompt, List<String> choices, Consumer<String> consumer,
            Runnable onCancel) {
        request(player, prompt, consumer, onCancel);
        PendingInput input = pendingInputs.get(player.getUniqueId());
        if (input == null || choices == null || choices.isEmpty())
            return;
        Component line = Component.text("Click to choose: ", NamedTextColor.GRAY);
        boolean first = true;
        for (String choice : choices) {
            if (!first)
                line = line.append(Component.space());
            first = false;
            line = line
                    .append(Component.text("[" + choice + "]", NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                            .hoverEvent(
                                    HoverEvent.showText(Component.text("Use '" + choice + "'", NamedTextColor.YELLOW)))
                            .clickEvent(ClickEvent.callback(audience -> {
                                if (audience instanceof Player clicked
                                        && clicked.getUniqueId().equals(player.getUniqueId()))
                                    Bukkit.getScheduler().runTask(plugin, () -> choose(clicked, input, choice));
                            })));
        }
        player.sendMessage(line);
    }

    /** Answers a still-open prompt with a clicked choice. */
    private void choose(Player player, PendingInput input, String choice) {
        if (!pendingInputs.remove(player.getUniqueId(), input))
            return;
        input.timeout.cancel();
        input.consumer.accept(choice);
    }

    public boolean isPending(Player player) {
        return pendingInputs.containsKey(player.getUniqueId()) || requestingInputs.contains(player.getUniqueId());
    }

    public void setBeforeRequest(Consumer<Player> beforeRequest) {
        this.beforeRequest = beforeRequest == null ? ignored -> {
        } : beforeRequest;
    }

    public void cancelAll() {
        for (PendingInput input : pendingInputs.values()) {
            input.timeout.cancel();
        }
        pendingInputs.clear();
        requestingInputs.clear();
    }

    public void cancel(Player player) {
        cancel(player.getUniqueId(), false);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        PendingInput input = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        input.timeout.cancel();
        String message = PLAIN_TEXT.serialize(event.message());
        if (message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                event.getPlayer().sendMessage(UiText.warning("Input cancelled."));
                if (input.onCancel != null)
                    input.onCancel.run();
            });
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

    private record PendingInput(Consumer<String> consumer, Runnable onCancel, BukkitTask timeout) {

    }
}
