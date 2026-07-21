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
    private static final int MAX_ACTIVITIES = 20;
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);
    private final Map<UUID, Deque<Entry>> events = new HashMap<>();
    private final Map<ConversationKey, Deque<String>> conversations = new HashMap<>();
    private final Map<UUID, Deque<String>> sharedConversations = new HashMap<>();
    private final Map<UUID, Deque<String>> activities = new HashMap<>();

    public void rememberEvent(UUID instance, String summary) {
        if (summary == null || summary.isBlank()) return;
        Deque<Entry> memory = events.computeIfAbsent(instance, ignored -> new ArrayDeque<>());
        memory.addLast(new Entry(Instant.now(), summary.trim()));
        trim(memory, MAX_EVENTS);
    }

    public void rememberMessage(UUID instance, UUID player, String message) {
        rememberMessage(instance, player, message, false);
    }

    public void rememberMessage(UUID instance, UUID player, String message, boolean shared) {
        if (player == null || message == null || message.isBlank()) return;
        Deque<String> memory = conversations.computeIfAbsent(
                new ConversationKey(instance, player), ignored -> new ArrayDeque<>());
        memory.addLast(message.trim());
        trim(memory, MAX_MESSAGES);
        if (shared) {
            Deque<String> sharedMemory = sharedConversations.computeIfAbsent(
                    instance, ignored -> new ArrayDeque<>());
            sharedMemory.addLast(message.trim());
            trim(sharedMemory, MAX_MESSAGES);
        }
    }

    public List<String> recentEvents(UUID instance) {
        Deque<Entry> memory = events.get(instance);
        if (memory == null) return List.of();
        Instant cutoff = Instant.now().minus(EVENT_AGE);
        while (!memory.isEmpty() && memory.getFirst().time().isBefore(cutoff)) memory.removeFirst();
        return memory.stream().map(Entry::summary).toList();
    }

    public List<String> recentConversation(UUID instance, UUID player) {
        if (player == null) return List.of();
        return new ArrayList<>(conversations.getOrDefault(new ConversationKey(instance, player), new ArrayDeque<>()));
    }

    public List<String> recentSharedConversation(UUID instance) {
        return new ArrayList<>(sharedConversations.getOrDefault(instance, new ArrayDeque<>()));
    }

    public void rememberActivity(UUID instance, String summary) {
        if (summary == null || summary.isBlank()) return;
        Deque<String> memory = activities.computeIfAbsent(instance, ignored -> new ArrayDeque<>());
        memory.addLast(summary.trim());
        trim(memory, MAX_ACTIVITIES);
    }

    public List<String> recentActivities(UUID instance) {
        return new ArrayList<>(activities.getOrDefault(instance, new ArrayDeque<>()));
    }

    public void forget(UUID instance) {
        events.remove(instance);
        activities.remove(instance);
        sharedConversations.remove(instance);
        conversations.keySet().removeIf(key -> key.instance().equals(instance));
    }

    private static <T> void trim(Deque<T> deque, int size) {
        while (deque.size() > size) deque.removeFirst();
    }

    private record Entry(Instant time, String summary) { }
    private record ConversationKey(UUID instance, UUID player) { }
}
