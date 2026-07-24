package dev.blockfolk.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiMemoryStore {
    private static final int MAX_EVENTS = 10;
    private static final int MAX_MESSAGES = 20;
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);
    private final Map<UUID, Deque<Entry>> events = new HashMap<>();
    private final Map<ConversationKey, Deque<String>> conversations = new HashMap<>();
    private static final UUID SHARED_CONVERSATION = new UUID(0L, 0L);

    public void rememberEvent(UUID instance, String summary) {
        if (summary == null || summary.isBlank()) return;
        Deque<Entry> memory = events.computeIfAbsent(instance, ignored -> new ArrayDeque<>());
        memory.addLast(new Entry(Instant.now(), summary.trim()));
        trim(memory, MAX_EVENTS);
    }

    public void rememberMessage(UUID instance, UUID player, boolean shared, String message) {
        if (player == null || message == null || message.isBlank()) return;
        Deque<String> memory = conversations.computeIfAbsent(
                new ConversationKey(instance, shared ? SHARED_CONVERSATION : player), ignored -> new ArrayDeque<>());
        memory.addLast(message.trim());
        trim(memory, MAX_MESSAGES);
    }

    public List<String> recentEvents(UUID instance) {
        Deque<Entry> memory = events.get(instance);
        if (memory == null) return List.of();
        Instant cutoff = Instant.now().minus(EVENT_AGE);
        while (!memory.isEmpty() && memory.getFirst().time().isBefore(cutoff)) memory.removeFirst();
        return memory.stream().map(Entry::summary).toList();
    }

    public List<String> recentConversation(UUID instance, UUID player, boolean shared) {
        if (player == null) return List.of();
        UUID scope = shared ? SHARED_CONVERSATION : player;
        return new ArrayList<>(conversations.getOrDefault(new ConversationKey(instance, scope), new ArrayDeque<>()));
    }

    public void forget(UUID instance) {
        events.remove(instance);
        conversations.keySet().removeIf(key -> key.instance().equals(instance));
    }

    private static <T> void trim(Deque<T> deque, int size) {
        while (deque.size() > size) deque.removeFirst();
    }

    private record Entry(Instant time, String summary) { }
    private record ConversationKey(UUID instance, UUID player) { }
}
