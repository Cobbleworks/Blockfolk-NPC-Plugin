package dev.blockfolk.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.NpcQuestion;
import dev.blockfolk.model.QuestionOption;
import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.util.UiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class NpcQuestionService implements Listener {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private static final double RANGE_SQUARED = 16.0 * 16.0;
    private final Plugin plugin;
    private final NpcInstanceRegistry instances;
    private final ChatInputService chatInput;
    private final int timeoutSeconds;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final java.util.Set<UUID> consumingChat = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private BukkitTask rangeTask;

    public NpcQuestionService(Plugin plugin, NpcInstanceRegistry instances, ChatInputService chatInput,
            int timeoutSeconds) {
        this.plugin = plugin;
        this.instances = instances;
        this.chatInput = chatInput;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public void start() {
        stop();
        rangeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkActiveRanges, 10L, 10L);
    }

    public void stop() {
        if (rangeTask != null) rangeTask.cancel();
        rangeTask = null;
        for (PlayerState state : states.values()) {
            if (state.timeout != null) state.timeout.cancel();
        }
        states.clear();
        consumingChat.clear();
    }

    /** Queues a question. False means an identical active/queued/resolving request was deduplicated. */
    public boolean enqueue(Player player, NpcInstance instance, String npcName, NamedTextColor npcColor,
            NpcQuestion question,
            BiConsumer<List<BehaviourAction>, Runnable> resolver) {
        PlayerState state = states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
        QuestionKey key = new QuestionKey(instance.getId(), question.id());
        if (!state.questions.offer(new Request(player.getUniqueId(), instance, npcName, npcColor, question, key,
                UUID.randomUUID(), resolver))) return false;
        activateNext(player.getUniqueId(), state);
        return true;
    }

    public void forget(NpcInstance instance) {
        for (Map.Entry<UUID, PlayerState> entry : new ArrayList<>(states.entrySet())) {
            PlayerState state = entry.getValue();
            state.questions.removeIf(request -> request.instance.getId().equals(instance.getId()));
            if (state.active != null && state.active.instance.getId().equals(instance.getId())) {
                resolve(entry.getKey(), state, state.active.question.cancelActions());
            }
            removeIfEmpty(entry.getKey(), state);
        }
    }

    public void cancelForAdminInput(Player player) {
        cancelActive(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        PlayerState state = states.get(event.getPlayer().getUniqueId());
        Request active = state == null ? null : state.active;
        if (active == null) return;
        UUID playerId = event.getPlayer().getUniqueId();
        consumingChat.add(playerId);
        event.setCancelled(true);
        String input = PLAIN_TEXT.serialize(event.message()).trim();
        UUID token = active.token;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (input.equalsIgnoreCase("cancel")) {
                    cancel(playerId, token);
                    return;
                }
                try {
                    select(playerId, token, Integer.parseInt(input) - 1);
                } catch (NumberFormatException exception) {
                    event.getPlayer().sendMessage(Component.text("Type an answer number or 'cancel'.",
                            NamedTextColor.RED));
                }
            } finally {
                consumingChat.remove(playerId);
            }
        });
    }

    /** Safe to query from Paper's asynchronous chat event thread. */
    public boolean isConsumingChat(UUID playerId) {
        return consumingChat.contains(playerId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelActive(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cancelActive(event.getPlayer().getUniqueId());
    }

    private void activateNext(UUID playerId, PlayerState state) {
        if (state.active != null || state.resolving) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && chatInput.isPending(player)) return;
        Request request = state.questions.poll();
        if (request == null) {
            removeIfEmpty(playerId, state);
            return;
        }
        if (!valid(request, player) || request.question.configuredOptions().isEmpty()) {
            state.active = request;
            resolve(playerId, state, request.question.cancelActions());
            return;
        }
        state.active = request;
        show(player, request);
        state.timeout = Bukkit.getScheduler().runTaskLater(plugin,
                () -> cancel(playerId, request.token), timeoutSeconds * 20L);
    }

    private void show(Player player, Request request) {
        player.sendMessage(UiText.npcDialog(request.npcName, request.question.prompt(), request.npcColor));
        List<QuestionOption> configuredOptions = request.question.configuredOptions();
        for (int index = 0; index < configuredOptions.size(); index++) {
            int optionIndex = index;
            String label = configuredOptions.get(index).label();
            ClickCallback<net.kyori.adventure.audience.Audience> callback = audience -> {
                if (audience instanceof Player clicked && clicked.getUniqueId().equals(player.getUniqueId())) {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> select(player.getUniqueId(), request.token, optionIndex));
                }
            };
            Component option = Component.text("[" + (index + 1) + "] " + label, NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.callback(callback, options -> options.uses(1)
                            .lifetime(Duration.ofSeconds(timeoutSeconds))))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to answer", NamedTextColor.YELLOW)));
            player.sendMessage(option);
        }
        ClickCallback<net.kyori.adventure.audience.Audience> cancel = audience -> {
            if (audience instanceof Player clicked && clicked.getUniqueId().equals(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> cancel(player.getUniqueId(), request.token));
            }
        };
        player.sendMessage(Component.text("[Cancel]", NamedTextColor.RED)
                .clickEvent(ClickEvent.callback(cancel, options -> options.uses(1)
                        .lifetime(Duration.ofSeconds(timeoutSeconds)))));
    }

    private void select(UUID playerId, UUID token, int optionIndex) {
        PlayerState state = states.get(playerId);
        Request active = state == null ? null : state.active;
        if (active == null || !active.token.equals(token)) return;
        List<QuestionOption> options = active.question.configuredOptions();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.sendMessage(Component.text("Choose a number from 1 to "
                    + options.size() + ".", NamedTextColor.RED));
            return;
        }
        resolve(playerId, state, options.get(optionIndex).actions());
    }

    private void cancel(UUID playerId, UUID token) {
        PlayerState state = states.get(playerId);
        Request active = state == null ? null : state.active;
        if (active != null && active.token.equals(token)) {
            resolve(playerId, state, active.question.cancelActions());
        }
    }

    private void cancelActive(UUID playerId) {
        PlayerState state = states.get(playerId);
        if (state != null && state.active != null) {
            resolve(playerId, state, state.active.question.cancelActions());
        }
    }

    private void resolve(UUID playerId, PlayerState state, List<BehaviourAction> branch) {
        Request request = state.active;
        if (request == null || state.resolving) return;
        if (state.timeout != null) state.timeout.cancel();
        state.timeout = null;
        state.active = null;
        state.resolving = true;
        request.resolver.accept(branch, () -> {
            state.questions.complete(request);
            state.resolving = false;
            activateNext(playerId, state);
        });
    }

    private void checkActiveRanges() {
        for (Map.Entry<UUID, PlayerState> entry : new ArrayList<>(states.entrySet())) {
            PlayerState state = entry.getValue();
            if (state.active != null && !valid(state.active, Bukkit.getPlayer(entry.getKey()))) {
                resolve(entry.getKey(), state, state.active.question.cancelActions());
            } else if (state.active == null && !state.resolving) {
                activateNext(entry.getKey(), state);
            }
        }
    }

    private boolean valid(Request request, Player player) {
        if (player == null || !player.isOnline()) return false;
        if (instances.findAll().stream().noneMatch(candidate -> candidate.getId().equals(request.instance.getId()))) {
            return false;
        }
        Location npc = request.instance.getLocation();
        return npc.getWorld() != null && player.getWorld() == npc.getWorld()
                && player.getLocation().distanceSquared(npc) <= RANGE_SQUARED;
    }

    private void removeIfEmpty(UUID playerId, PlayerState state) {
        if (state.active == null && !state.resolving && state.questions.isEmpty()) states.remove(playerId, state);
    }

    private static final class PlayerState {
        private final DeduplicatedFifoQueue<QuestionKey, Request> questions =
                new DeduplicatedFifoQueue<>(Request::key);
        private Request active;
        private boolean resolving;
        private BukkitTask timeout;
    }

    private record QuestionKey(UUID instanceId, UUID questionId) { }
    private record Request(UUID playerId, NpcInstance instance, String npcName, NamedTextColor npcColor,
            NpcQuestion question, QuestionKey key, UUID token,
            BiConsumer<List<BehaviourAction>, Runnable> resolver) { }
}
