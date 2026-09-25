package dev.blockfolk.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AiMemoryStore {
    private static final int MAX_EVENTS = 10;
    private static final int DREAM_HISTORY_LINES = 20;
    public static final int DEFAULT_MAX_MESSAGES = 20;
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);
    private final int maxMessages;
    private final Map<UUID, Deque<Entry>> events = new HashMap<>();
    private static final int DREAM_BATCH_SIZE = 10;
    private static final int DREAM_OVERLAP = 2;
    private final Map<ConversationKey, Deque<MessageEntry>> conversations = new HashMap<>();
    private final Map<ConversationKey, Long> dreamCompletedThrough = new HashMap<>();
    private final Map<ConversationKey, Long> nextSequences = new HashMap<>();
    private final Set<ConversationKey> dreamInFlight = new HashSet<>();
    private static final UUID SHARED_CONVERSATION = new UUID(0L, 0L);

    public AiMemoryStore() {
        this(DEFAULT_MAX_MESSAGES);
    }

    public AiMemoryStore(int maxMessages) {
        this.maxMessages = Math.max(0, maxMessages);
    }

    public void rememberEvent(UUID instance, String summary) {
        if (summary == null || summary.isBlank())
            return;
        Deque<Entry> memory = events.computeIfAbsent(instance, ignored -> new ArrayDeque<>());
        memory.addLast(new Entry(Instant.now(), summary.trim()));
        trim(memory, MAX_EVENTS);
    }

    public synchronized void rememberMessage(UUID instance, UUID player, boolean shared, String message) {
        rememberMessage(instance, player, shared, message, false);
    }

    public synchronized void rememberMessage(UUID instance, UUID player, boolean shared, String message,
            boolean retainForDream) {
        if ((maxMessages == 0 && !retainForDream) || player == null || message == null || message.isBlank())
            return;
        ConversationKey key = key(instance, player, shared);
        Deque<MessageEntry> memory = conversations.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long sequence = nextSequences.merge(key, 1L, Long::sum);
        memory.addLast(new MessageEntry(sequence, player, message.trim()));
        trim(memory, Math.max(DREAM_HISTORY_LINES, maxMessages));
    }

    /**
     * Claims a ten-line conversation batch, including the previous exchange as
     * context.
     */
    public synchronized DreamBatch claimDreamBatch(UUID instance, UUID player, boolean shared) {
        return claimDreamBatch(instance, player, shared, false);
    }

    /**
     * An idle conversation can be reviewed even when fewer than ten new lines
     * exist.
     */
    public synchronized DreamBatch claimDreamBatch(UUID instance, UUID player, boolean shared, boolean afterIdle) {
        if (player == null)
            return null;
        ConversationKey key = key(instance, player, shared);
        Deque<MessageEntry> messages = conversations.get(key);
        if (messages == null || messages.isEmpty() || dreamInFlight.contains(key))
            return null;
        long completedThrough = dreamCompletedThrough.getOrDefault(key, 0L);
        long latestSequence = messages.getLast().sequence();
        if (latestSequence <= completedThrough || (!afterIdle && latestSequence - completedThrough < DREAM_BATCH_SIZE))
            return null;
        long firstSequence = Math.max(1L, completedThrough - DREAM_OVERLAP + 1L);
        List<String> batch = messages.stream().filter(entry -> entry.sequence() >= firstSequence)
                .map(MessageEntry::text).toList();
        if (batch.isEmpty())
            return null;
        Set<UUID> participants = messages.stream().filter(entry -> entry.sequence() > completedThrough)
                .map(MessageEntry::player).collect(java.util.stream.Collectors.toSet());
        dreamInFlight.add(key);
        return new DreamBatch(instance, player, shared, latestSequence, batch, participants);
    }

    public synchronized boolean dreamInFlight(UUID instance, UUID player, boolean shared) {
        return dreamInFlight.contains(key(instance, player, shared));
    }

    public synchronized void completeDreamBatch(DreamBatch batch) {
        ConversationKey key = key(batch.instance(), batch.player(), batch.shared());
        if (dreamInFlight.remove(key))
            dreamCompletedThrough.merge(key, batch.throughSequence(), Math::max);
    }

    public synchronized void releaseDreamBatch(DreamBatch batch) {
        dreamInFlight.remove(key(batch.instance(), batch.player(), batch.shared()));
    }

    public List<String> recentEvents(UUID instance) {
        Deque<Entry> memory = events.get(instance);
        if (memory == null)
            return List.of();
        Instant cutoff = Instant.now().minus(EVENT_AGE);
        while (!memory.isEmpty() && memory.getFirst().time().isBefore(cutoff))
            memory.removeFirst();
        return memory.stream().map(Entry::summary).toList();
    }

    public synchronized List<String> recentConversation(UUID instance, UUID player, boolean shared) {
        if (player == null || maxMessages == 0)
            return List.of();
        UUID scope = shared ? SHARED_CONVERSATION : player;
        List<String> recent = conversations.getOrDefault(new ConversationKey(instance, scope), new ArrayDeque<>())
                .stream().map(MessageEntry::text).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return recent.size() <= maxMessages
                ? recent
                : new ArrayList<>(recent.subList(recent.size() - maxMessages, recent.size()));
    }

    public synchronized void forget(UUID instance) {
        events.remove(instance);
        conversations.keySet().removeIf(key -> key.instance().equals(instance));
        dreamCompletedThrough.keySet().removeIf(key -> key.instance().equals(instance));
        nextSequences.keySet().removeIf(key -> key.instance().equals(instance));
        dreamInFlight.removeIf(key -> key.instance().equals(instance));
    }

    private static <T> void trim(Deque<T> deque, int size) {
        while (deque.size() > size)
            deque.removeFirst();
    }

    private record Entry(Instant time, String summary) {
    }

    private static ConversationKey key(UUID instance, UUID player, boolean shared) {
        return new ConversationKey(instance, shared ? SHARED_CONVERSATION : player);
    }

    private record MessageEntry(long sequence, UUID player, String text) {
    }

    public record DreamBatch(UUID instance, UUID player, boolean shared, long throughSequence, List<String> lines,
            Set<UUID> participants) {
        public DreamBatch {
            lines = List.copyOf(lines);
            participants = Set.copyOf(participants);
        }
    }

    private record ConversationKey(UUID instance, UUID player) {
    }
}
