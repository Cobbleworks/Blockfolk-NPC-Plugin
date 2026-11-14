package dev.blockfolk.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bookkeeping that keeps NPC-to-NPC conversations finite. Pure logic without
 * Bukkit access, so it is used from the main thread only.
 *
 * <p>
 * Each conversation ("exchange") counts the response rounds it has triggered.
 * It ends when the reply cap or time limit is reached, or after a quiet period.
 * When it ends, every pair of its participants is put on cooldown, and the On
 * Nearby NPC event is locked for each pair for the same duration.
 */
public final class NpcConversationTracker {

    /** An exchange with no new line for this long is over. */
    static final long IDLE_TIMEOUT_MILLIS = 30_000L;

    private final NpcConversationSettings settings;
    private final Map<UUID, Exchange> exchangesByNpc = new HashMap<>();
    private final Map<Pair, Long> pairCooldownUntil = new HashMap<>();
    private final Map<Pair, Long> encounterLockedUntil = new HashMap<>();

    public NpcConversationTracker(NpcConversationSettings settings) {
        this.settings = settings;
    }

    public NpcConversationSettings settings() {
        return settings;
    }

    /**
     * Claims one response round for a spoken line. The speaker continues its own
     * conversation, joins one a listener is already in, or starts a new one.
     *
     * @return the listeners who should respond, or empty when the line must not
     *         trigger replies (cap reached, everyone busy or on cooldown)
     */
    public Optional<Claim> claimReply(UUID speaker, Collection<UUID> listeners, long now) {
        expire(now);
        if (!settings.enabled()) {
            return Optional.empty();
        }
        Exchange exchange = exchangesByNpc.get(speaker);
        if (exchange == null) {
            exchange = listeners.stream().map(exchangesByNpc::get).filter(java.util.Objects::nonNull).findFirst()
                    .orElse(null);
        }
        if (exchange != null && exchange.replies >= settings.maxReplies()) {
            end(exchange, now);
            return Optional.empty();
        }
        Exchange current = exchange;
        List<UUID> eligible = listeners.stream().filter(listener -> !listener.equals(speaker))
                .filter(listener -> current != null && current.participants.contains(listener)
                        || !exchangesByNpc.containsKey(listener) && !onCooldown(speaker, listener, now))
                .distinct().toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        if (current == null) {
            Set<UUID> participants = new LinkedHashSet<>();
            participants.add(speaker);
            participants.addAll(eligible);
            exchange = new Exchange(conversationKey(participants), participants, now);
        }
        exchange.participants.add(speaker);
        exchange.participants.addAll(eligible);
        for (UUID participant : exchange.participants) {
            exchangesByNpc.put(participant, exchange);
        }
        exchange.replies++;
        exchange.lastActivity = now;
        return Optional.of(new Claim(exchange.key, eligible, exchange.replies, settings.maxReplies()));
    }

    /**
     * The memory key of the speaker's current conversation, even when its reply cap
     * is reached, so its final lines are still remembered.
     */
    public Optional<UUID> conversationOf(UUID npc, long now) {
        expire(now);
        return Optional.ofNullable(exchangesByNpc.get(npc)).map(exchange -> exchange.key);
    }

    public Set<UUID> participants(UUID conversationKey) {
        return exchangesByNpc.values().stream().filter(exchange -> exchange.key.equals(conversationKey)).findFirst()
                .map(exchange -> Set.copyOf(exchange.participants)).orElse(Set.of());
    }

    /**
     * Whether {@code observer} may fire On Nearby NPC for {@code other}. A
     * successful claim locks the pair in both directions, so two NPCs meeting at
     * the same moment do not both open a conversation.
     */
    public boolean claimEncounter(UUID observer, UUID other, long now) {
        expire(now);
        Pair pair = Pair.of(observer, other);
        if (onCooldown(observer, other, now) || encounterLockedUntil.getOrDefault(pair, 0L) > now) {
            return false;
        }
        Exchange exchange = exchangesByNpc.get(observer);
        if (exchange != null && exchange.participants.contains(other)) {
            return false;
        }
        encounterLockedUntil.put(pair, now + settings.cooldownSeconds() * 1000L);
        return true;
    }

    /** Ends the NPC's conversation, for example when a player starts talking. */
    public Optional<UUID> interrupt(UUID npc, long now) {
        Exchange exchange = exchangesByNpc.get(npc);
        if (exchange == null) {
            return Optional.empty();
        }
        end(exchange, now);
        return Optional.of(exchange.key);
    }

    /** Removes a despawned NPC; a conversation left with one NPC ends. */
    public Optional<UUID> forget(UUID npc, long now) {
        Exchange exchange = exchangesByNpc.remove(npc);
        pairCooldownUntil.keySet().removeIf(pair -> pair.contains(npc));
        encounterLockedUntil.keySet().removeIf(pair -> pair.contains(npc));
        if (exchange == null) {
            return Optional.empty();
        }
        exchange.participants.remove(npc);
        if (exchange.participants.size() < 2) {
            end(exchange, now);
            return Optional.of(exchange.key);
        }
        return Optional.empty();
    }

    /**
     * Ends conversations past their time limit or quiet for too long and drops
     * stale cooldowns.
     *
     * @return memory keys of conversations that ended
     */
    public List<UUID> expire(long now) {
        List<UUID> ended = new ArrayList<>();
        for (Exchange exchange : Set.copyOf(exchangesByNpc.values())) {
            if (now - exchange.startedAt >= settings.maxDurationSeconds() * 1000L
                    || now - exchange.lastActivity >= IDLE_TIMEOUT_MILLIS) {
                end(exchange, now);
                ended.add(exchange.key);
            }
        }
        pairCooldownUntil.values().removeIf(until -> until <= now);
        encounterLockedUntil.values().removeIf(until -> until <= now);
        return ended;
    }

    public boolean active(UUID npc) {
        return exchangesByNpc.containsKey(npc);
    }

    boolean onCooldown(UUID first, UUID second, long now) {
        return pairCooldownUntil.getOrDefault(Pair.of(first, second), 0L) > now;
    }

    private void end(Exchange exchange, long now) {
        exchangesByNpc.values().removeIf(candidate -> candidate == exchange);
        long until = now + settings.cooldownSeconds() * 1000L;
        List<UUID> members = List.copyOf(exchange.participants);
        for (int first = 0; first < members.size(); first++) {
            for (int second = first + 1; second < members.size(); second++) {
                Pair pair = Pair.of(members.get(first), members.get(second));
                pairCooldownUntil.merge(pair, until, Math::max);
                encounterLockedUntil.merge(pair, until, Math::max);
            }
        }
    }

    /**
     * The same group of NPCs always gets the same key, so a pair that talks again
     * later still sees its recent conversation, and memory stays bounded.
     */
    static UUID conversationKey(Collection<UUID> participants) {
        String joined = participants.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
        return UUID.nameUUIDFromBytes(("blockfolk-npc-conversation:" + joined).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A claimed response round.
     *
     * @param conversationKey
     *            memory and queue key of the conversation
     * @param listeners
     *            NPCs that may respond, in the order given
     * @param reply
     *            1-based number of this round
     * @param maxReplies
     *            configured cap; {@code reply == maxReplies} is the last round
     */
    public record Claim(UUID conversationKey, List<UUID> listeners, int reply, int maxReplies) {
        public Claim {
            listeners = List.copyOf(listeners);
        }

        public boolean lastReply() {
            return reply >= maxReplies;
        }
    }

    private static final class Exchange {
        private final UUID key;
        private final Set<UUID> participants;
        private final long startedAt;
        private long lastActivity;
        private int replies;

        private Exchange(UUID key, Set<UUID> participants, long now) {
            this.key = key;
            this.participants = new LinkedHashSet<>(participants);
            this.startedAt = now;
            this.lastActivity = now;
        }
    }

    private record Pair(UUID low, UUID high) {
        static Pair of(UUID first, UUID second) {
            return first.compareTo(second) <= 0 ? new Pair(first, second) : new Pair(second, first);
        }

        boolean contains(UUID id) {
            return low.equals(id) || high.equals(id);
        }
    }
}
