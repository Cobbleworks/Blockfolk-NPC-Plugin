package dev.blockfolk.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.ai.AiControlService;
import dev.blockfolk.ai.AiDecisionResult;
import dev.blockfolk.ai.ChatAddressee;
import dev.blockfolk.ai.NpcConversationSettings;
import dev.blockfolk.ai.NpcConversationTracker;
import dev.blockfolk.ai.NpcResponseIds;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.NpcDefinitionRepository;

/**
 * Lets AI NPCs with "Respond to NPCs" enabled answer what nearby NPCs say.
 *
 * <p>
 * Every NPC chat line (AI speech and Send Dialog) is offered here, including
 * an NPC's reply to a player's chat, so nearby NPCs can chime in. Holo Dialog
 * is not: it is often repeating flavour text. Listeners in hearing range
 * respond together in one coordinated AI request after a short delay, so lines
 * spoken at the same time get one answer. The {@link NpcConversationTracker}
 * caps how many response rounds one conversation gets, and nothing happens
 * without a player nearby. NPCs in a conversation look at whoever spoke last.
 */
public final class NpcConversationService {

    private static final double ENCOUNTER_RANGE = 8.0;
    /** How long an NPC keeps looking at the last NPC that spoke to it. */
    private static final long GAZE_MILLIS = 10_000L;

    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final NpcConversationTracker tracker;
    private final Map<UUID, PendingRound> pendingRounds = new HashMap<>();
    private final Map<UUID, Gaze> gazes = new HashMap<>();
    private AiControlService aiControlService;
    private BiConsumer<NpcInstance, AiDecisionResult> decisionHandler = (instance, result) -> {
    };

    public NpcConversationService(Plugin plugin, NpcDefinitionRepository definitions, NpcInstanceRegistry instances,
            NpcConversationSettings settings) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
        this.tracker = new NpcConversationTracker(settings);
    }

    public void setAiControlService(AiControlService aiControlService) {
        this.aiControlService = aiControlService;
    }

    /** Applies the AI's decisions for one responding NPC. */
    void setDecisionHandler(BiConsumer<NpcInstance, AiDecisionResult> decisionHandler) {
        this.decisionHandler = decisionHandler == null ? (instance, result) -> {
        } : decisionHandler;
    }

    public NpcConversationSettings settings() {
        return tracker.settings();
    }

    public double encounterRange() {
        return ENCOUNTER_RANGE;
    }

    /**
     * Offers a visible NPC line to nearby NPCs. Main thread only.
     */
    public void npcSpoke(NpcInstance speaker, NpcDefinition definition, String text) {
        spoke(speaker, definition, text, null);
    }

    /**
     * Offers an NPC's reply to a player's chat to nearby NPCs, so they can chime
     * in. The player's message is shared with them as part of the conversation.
     * Main thread only.
     */
    public void npcAnsweredPlayer(NpcInstance speaker, NpcDefinition definition, String text, Player player,
            String message) {
        String playerName = player == null ? "A player" : player.getName();
        spoke(speaker, definition, text, message == null ? null : Line.player(playerName, stripFormatting(message)));
    }

    private void spoke(NpcInstance speaker, NpcDefinition definition, String text, Line answered) {
        if (aiControlService == null || !aiControlService.configured() || !settings().enabled() || text == null) {
            return;
        }
        String spoken = stripFormatting(text);
        if (spoken.isEmpty() || instances.findById(speaker.getId()).isEmpty()) {
            return;
        }
        Location origin = instances.currentLocation(speaker);
        if (origin.getWorld() == null || !playerNearby(origin)) {
            return;
        }
        long now = System.currentTimeMillis();
        cancelEnded(tracker.expire(now));
        String line = NpcResponseIds.plainName(definition.getDisplayName()) + ": " + spoken;
        List<String> memoryLines = answered == null ? List.of(line)
                : List.of(answered.playerName() + " (player): " + answered.text(), line);
        Line spokenLine = Line.npc(speaker.getId(), spoken);
        List<NpcInstance> hearers = hearers(speaker, origin);
        Optional<UUID> existing = tracker.conversationOf(speaker.getId(), now);
        Set<UUID> existingParticipants = existing.map(tracker::participants).orElse(Set.of());

        PendingRound pending = existing.map(pendingRounds::get).orElse(null);
        if (pending != null) {
            // Lines spoken before the pending round starts are answered together.
            if (answered != null) {
                pending.lines.add(answered);
            }
            pending.lines.add(spokenLine);
            hearers.stream().filter(this::respondsToNpc).map(NpcInstance::getId).filter(existingParticipants::contains)
                    .forEach(pending.listeners::add);
            remember(pending.key, speaker, hearers, existingParticipants, memoryLines);
            look(speaker, hearers, existingParticipants, spoken, answered != null, now);
            return;
        }

        List<UUID> listeners = hearers.stream().filter(this::respondsToNpc).map(NpcInstance::getId).toList();
        Optional<NpcConversationTracker.Claim> claim = tracker.claimReply(speaker.getId(), listeners, now,
                answered != null);
        if (claim.isEmpty()) {
            // The reply budget is used up, but the closing line still belongs to
            // the conversation.
            existing.ifPresent(key -> {
                remember(key, speaker, hearers, existingParticipants, memoryLines);
                look(speaker, hearers, existingParticipants, spoken, answered != null, now);
            });
            return;
        }
        NpcConversationTracker.Claim round = claim.get();
        Set<UUID> participants = tracker.participants(round.conversationKey());
        remember(round.conversationKey(), speaker, hearers, participants, memoryLines);
        look(speaker, hearers, participants, spoken, answered != null, now);
        // A newcomer joining a conversation whose round is still pending merges
        // into that round instead of replacing it.
        PendingRound next = pendingRounds.get(round.conversationKey());
        if (next == null) {
            PendingRound created = new PendingRound(round.conversationKey(), round.maxReplies());
            created.task = Bukkit.getScheduler().runTaskLater(plugin, () -> startRound(created),
                    settings().replyDelayTicks());
            pendingRounds.put(round.conversationKey(), created);
            next = created;
        }
        next.reply = round.reply();
        if (answered != null) {
            next.lines.add(answered);
        }
        next.lines.add(spokenLine);
        next.listeners.addAll(round.listeners());
    }

    /**
     * The NPC this one should look at because it is talking with it, if any.
     * Expires a few seconds after the last line.
     */
    public Optional<NpcInstance> gazeTarget(NpcInstance instance) {
        Gaze gaze = gazes.get(instance.getId());
        if (gaze == null) {
            return Optional.empty();
        }
        if (gaze.until() <= System.currentTimeMillis()) {
            gazes.remove(instance.getId());
            return Optional.empty();
        }
        Location location = instances.currentLocation(instance);
        double range = settings().hearingRange();
        return instances.findById(gaze.target()).filter(target -> {
            Location targetLocation = instances.currentLocation(target);
            return targetLocation.getWorld() == location.getWorld()
                    && targetLocation.distanceSquared(location) <= range * range;
        });
    }

    /**
     * Whether {@code observer} may fire On Nearby NPC for {@code other}. Locks the
     * pair so the other NPC does not fire the event back at the same time.
     */
    public boolean claimEncounter(NpcInstance observer, NpcInstance other) {
        long now = System.currentTimeMillis();
        cancelEnded(tracker.expire(now));
        return tracker.claimEncounter(observer.getId(), other.getId(), now);
    }

    /** NPC conversations and On Nearby NPC only run while a player is around. */
    public boolean playerNearby(Location location) {
        if (location.getWorld() == null) {
            return false;
        }
        double range = settings().playerRange();
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= range * range) {
                return true;
            }
        }
        return false;
    }

    /**
     * A player talking to these NPCs takes over: their NPC conversations end and
     * queued rounds are dropped.
     */
    public void interrupt(Collection<NpcInstance> npcs) {
        long now = System.currentTimeMillis();
        for (NpcInstance npc : npcs) {
            tracker.interrupt(npc.getId(), now).ifPresent(this::cancelRound);
            gazes.remove(npc.getId());
        }
    }

    public void forget(NpcInstance instance) {
        tracker.forget(instance.getId(), System.currentTimeMillis()).ifPresent(this::cancelRound);
        pendingRounds.values().forEach(round -> round.listeners.remove(instance.getId()));
        gazes.remove(instance.getId());
        gazes.values().removeIf(gaze -> gaze.target().equals(instance.getId()));
    }

    public void stop() {
        pendingRounds.values().forEach(round -> {
            if (round.task != null) {
                round.task.cancel();
            }
        });
        pendingRounds.clear();
        gazes.clear();
    }

    private void startRound(PendingRound round) {
        if (!pendingRounds.remove(round.key, round)) {
            return;
        }
        long now = System.currentTimeMillis();
        cancelEnded(tracker.expire(now));
        if (tracker.participants(round.key).isEmpty()) {
            return;
        }
        // The last line is always an NPC's: a player's line is only added right
        // before the NPC reply that answered it.
        Line last = round.lines.getLast();
        NpcInstance lastSpeaker = instances.findById(last.speaker()).orElse(null);
        if (lastSpeaker == null) {
            return;
        }
        Location origin = instances.currentLocation(lastSpeaker);
        if (origin.getWorld() == null || !playerNearby(origin)) {
            return;
        }
        List<UUID> lineSpeakers = round.lines.stream().filter(Line::byNpc).map(Line::speaker).toList();
        List<Location> speakerLocations = lineSpeakers.stream().distinct().map(instances::findById)
                .flatMap(Optional::stream).map(instances::currentLocation).toList();
        double range = settings().hearingRange();
        List<NpcInstance> candidates = round.listeners.stream().map(instances::findById).flatMap(Optional::stream)
                .filter(candidate -> heardOthers(candidate.getId(), lineSpeakers)).filter(this::respondsToNpc)
                .filter(candidate -> {
                    Location location = instances.currentLocation(candidate);
                    return speakerLocations.stream()
                            .anyMatch(speakerLocation -> speakerLocation.getWorld() == location.getWorld()
                                    && speakerLocation.distanceSquared(location) <= range * range);
                }).sorted(Comparator.comparingDouble(candidate -> distanceSquared(candidate, origin))).toList();
        // The intended responder is never the NPC that just spoke: it is the one
        // named in the latest line, or the closest listener.
        List<NpcInstance> responders = candidates.stream()
                .filter(candidate -> !candidate.getId().equals(last.speaker())).toList();
        if (responders.isEmpty()) {
            return;
        }
        int addressee = ChatAddressee.select(last.text(), responders.stream().map(this::displayName).toList());
        NpcInstance primary = responders.get(Math.max(0, addressee));
        List<NpcInstance> ordered = new ArrayList<>();
        ordered.add(primary);
        candidates.stream().filter(candidate -> candidate != primary).forEach(ordered::add);
        aiControlService.invokeNpcConversation(round.key, ordered, instances.findEntity(lastSpeaker).orElse(null),
                describe(round), decisionHandler);
    }

    private String describe(PendingRound round) {
        boolean playerInvolved = round.lines.stream().anyMatch(line -> !line.byNpc());
        StringBuilder detail = new StringBuilder(playerInvolved
                ? "A player is talking with a nearby NPC. New lines:\n"
                : "Nearby NPCs are talking. New lines:\n");
        for (Line line : round.lines) {
            String name = line.byNpc()
                    ? instances.findById(line.speaker()).map(this::displayName).orElse("An NPC")
                    : "Player " + line.playerName();
            detail.append("- ").append(name).append(": \"").append(line.text()).append("\"\n");
        }
        detail.append("This is reply ").append(round.reply).append(" of ").append(round.maxReplies)
                .append(" in this NPC conversation.");
        if (round.reply >= round.maxReplies) {
            detail.append(" It is the last reply: bring the conversation to a close.");
        }
        return detail.toString();
    }

    private void remember(UUID key, NpcInstance speaker, List<NpcInstance> hearers, Set<UUID> participants,
            List<String> lines) {
        List<NpcInstance> audience = new ArrayList<>();
        audience.add(speaker);
        hearers.stream().filter(hearer -> participants.contains(hearer.getId())).forEach(audience::add);
        lines.forEach(line -> aiControlService.rememberNpcConversationLine(key, audience, line));
    }

    /**
     * Turns the conversation's listeners towards the speaker. The speaker looks at
     * the NPC its line names, otherwise the closest listener, unless it was
     * answering a player: then it keeps facing the player.
     */
    private void look(NpcInstance speaker, List<NpcInstance> hearers, Set<UUID> participants, String spoken,
            boolean answeringPlayer, long now) {
        List<NpcInstance> listening = hearers.stream().filter(hearer -> participants.contains(hearer.getId()))
                .toList();
        long until = now + GAZE_MILLIS;
        listening.forEach(hearer -> gazes.put(hearer.getId(), new Gaze(speaker.getId(), until)));
        if (answeringPlayer || listening.isEmpty()) {
            gazes.remove(speaker.getId());
            return;
        }
        int addressee = ChatAddressee.select(spoken, listening.stream().map(this::displayName).toList());
        gazes.put(speaker.getId(), new Gaze(listening.get(Math.max(0, addressee)).getId(), until));
    }

    private List<NpcInstance> hearers(NpcInstance speaker, Location origin) {
        double range = settings().hearingRange();
        return instances.findActive().stream().filter(other -> !other.getId().equals(speaker.getId())).filter(other -> {
            Location location = instances.currentLocation(other);
            return location.getWorld() == origin.getWorld() && location.distanceSquared(origin) <= range * range;
        }).sorted(Comparator.comparingDouble(other -> distanceSquared(other, origin))).toList();
    }

    private boolean respondsToNpc(NpcInstance instance) {
        return definitions.find(instance.getDefinitionKey()).map(NpcDefinition::getAiControlSettings)
                .map(settings -> settings.enabled() && settings.hasContext() && settings.respondToNpc()).orElse(false);
    }

    private String displayName(NpcInstance instance) {
        return definitions.find(instance.getDefinitionKey()).map(NpcDefinition::getDisplayName)
                .map(NpcResponseIds::plainName).orElse(instance.getDefinitionKey());
    }

    private double distanceSquared(NpcInstance instance, Location origin) {
        Location location = instances.currentLocation(instance);
        return location.getWorld() == origin.getWorld() ? location.distanceSquared(origin) : Double.MAX_VALUE;
    }

    private void cancelEnded(List<UUID> endedKeys) {
        endedKeys.forEach(this::cancelRound);
    }

    private void cancelRound(UUID key) {
        PendingRound round = pendingRounds.remove(key);
        if (round != null && round.task != null) {
            round.task.cancel();
        }
        if (aiControlService != null) {
            aiControlService.cancelNpcConversation(key);
        }
    }

    /**
     * An NPC never responds to its own lines (AI speech or Send Dialog): it only
     * takes part in a round that contains a line from another NPC.
     */
    static boolean heardOthers(UUID listener, List<UUID> lineSpeakers) {
        return lineSpeakers.stream().anyMatch(speaker -> !speaker.equals(listener));
    }

    static String stripFormatting(String text) {
        return text.replaceAll("(?i)§[0-9a-fk-orx]", "").trim();
    }

    /** A line in a round: spoken by an NPC, or the player message an NPC answered. */
    private record Line(UUID speaker, String playerName, String text) {
        static Line npc(UUID speaker, String text) {
            return new Line(speaker, null, text);
        }

        static Line player(String playerName, String text) {
            return new Line(null, playerName, text);
        }

        boolean byNpc() {
            return speaker != null;
        }
    }

    private record Gaze(UUID target, long until) {
    }

    private static final class PendingRound {
        private final UUID key;
        private final int maxReplies;
        private int reply;
        private final List<Line> lines = new ArrayList<>();
        private final Set<UUID> listeners = new LinkedHashSet<>();
        private BukkitTask task;

        private PendingRound(UUID key, int maxReplies) {
            this.key = key;
            this.maxReplies = maxReplies;
        }
    }
}
