package dev.blockfolk.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.RegionalEvent;
import dev.blockfolk.model.RegionalEvent.Phase;
import dev.blockfolk.model.RegionalEvent.Region;
import dev.blockfolk.model.RegionalEvent.Trigger;
import dev.blockfolk.repository.RegionalEventRepository;

/**
 * Runs regional events on the server thread and reserves participating NPCs
 * across phases.
 */
public final class RegionalEventService {
    private final Plugin plugin;
    private final RegionalEventRepository repository;
    private final NpcInstanceRegistry instances;
    private final NpcBehaviourService behaviours;
    private final Map<UUID, Run> running = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> playersInRegion = new HashMap<>();
    private final Map<UUID, Long> worldTimes = new HashMap<>();
    private BukkitTask task;

    public RegionalEventService(Plugin plugin, RegionalEventRepository repository, NpcInstanceRegistry instances,
            NpcBehaviourService behaviours) {
        this.plugin = plugin;
        this.repository = repository;
        this.instances = instances;
        this.behaviours = behaviours;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
    }

    public void shutdown() {
        if (task != null)
            task.cancel();
        List.copyOf(running.keySet()).forEach(id -> finish(id, false));
    }

    public boolean isRunning(UUID id) {
        return running.containsKey(id);
    }

    public void stop(UUID id) {
        finish(id, true);
    }

    public void npcRemoved(NpcInstance instance) {
        List.copyOf(running.values()).stream().filter(run -> run.actors.containsKey(instance.getId()))
                .forEach(run -> stop(run.event.id()));
    }

    public void emit(String reference, Entity actor) {
        RegionalEvent.referenceId(reference).map(repository::event).ifPresent(event -> play(event, actor));
    }

    /** Returns an explanation if the event cannot start. */
    public String play(RegionalEvent event, Entity actor) {
        if (isRunning(event.id()))
            return "This event is already running.";
        Region region = repository.region(event.regionId());
        if (region == null || Bukkit.getWorld(region.world()) == null)
            return "The event's region is unavailable.";
        List<Phase> phases = event.phases().stream()
                .filter(phase -> phase.actors().values().stream().anyMatch(actions -> !actions.isEmpty())).toList();
        if (phases.isEmpty())
            return "Add at least one NPC action before starting the event.";
        Map<UUID, NpcInstance> actors = new LinkedHashMap<>();
        for (Phase phase : phases) {
            for (var row : phase.actors().entrySet()) {
                UUID id = row.getKey();
                if (row.getValue().isEmpty() || actors.containsKey(id))
                    continue;
                NpcInstance instance = instances.findById(id).orElse(null);
                if (instance == null || instances.findEntity(instance).isEmpty()
                        || !region.world().equals(instance.getStoredLocation().worldName())
                        || !behaviours.canJoinRegionalEvent(instance)) {
                    return "A participating NPC is unavailable or busy.";
                }
                actors.put(id, instance);
            }
        }
        Run run = new Run(event, phases, actors, actor);
        running.put(event.id(), run);
        actors.values().forEach(behaviours::beginRegionalEvent);
        startPhase(run);
        return null;
    }

    private void startPhase(Run run) {
        Phase phase = run.phases.get(run.phase);
        List<UUID> rows = phase.actors().entrySet().stream().filter(row -> !row.getValue().isEmpty())
                .map(Map.Entry::getKey).toList();
        run.pending = rows.size();
        for (UUID id : rows) {
            if (running.get(run.event.id()) != run)
                return;
            behaviours.runRegionalActions(phase.actors().get(id), run.actors.get(id), run.actor, run.event.name(),
                    () -> run.pending--);
        }
    }

    private void finish(UUID id, boolean resumeRoutines) {
        Run run = running.remove(id);
        if (run != null)
            run.actors.values().forEach(instance -> behaviours.endRegionalEvent(instance, resumeRoutines));
    }

    private void tick() {
        for (Run run : List.copyOf(running.values())) {
            if (running.get(run.event.id()) != run)
                continue;
            if (run.actors.values().stream()
                    .anyMatch(npc -> instances.findEntity(npc).isEmpty() || !behaviours.isInRegionalEvent(npc))) {
                stop(run.event.id());
            } else if (run.pending == 0) {
                if (++run.phase >= run.phases.size())
                    stop(run.event.id());
                else
                    startPhase(run);
            }
        }
        tickTriggers();
    }

    private void tickTriggers() {
        Map<String, Long> currentTimes = new HashMap<>();
        Map<String, Long> previousTimes = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            long current = world.getTime();
            currentTimes.put(world.getName(), current);
            previousTimes.put(world.getName(), worldTimes.put(world.getUID(), current));
        }
        List<RegionalEvent> automatic = repository.events().stream()
                .filter(event -> event.enabled() && event.trigger() != Trigger.MANUAL).toList();
        Map<UUID, List<Player>> arrivals = new HashMap<>();
        for (RegionalEvent event : automatic) {
            Region region = repository.region(event.regionId());
            if (region == null)
                continue;
            if (event.trigger() == Trigger.PLAYER_ENTER) {
                List<Player> entered = arrivals.computeIfAbsent(region.id(), ignored -> entrants(region));
                if (!entered.isEmpty())
                    play(event, entered.getFirst());
            } else {
                Long previous = previousTimes.get(region.world());
                Long current = currentTimes.get(region.world());
                if (previous != null && current != null && crossed(previous, current, event.period()))
                    play(event, null);
            }
        }
        playersInRegion.keySet().retainAll(arrivals.keySet());
    }

    private List<Player> entrants(Region region) {
        World world = Bukkit.getWorld(region.world());
        if (world == null) {
            playersInRegion.remove(region.id());
            return List.of();
        }
        List<Player> inside = world.getPlayers().stream().filter(player -> region.contains(player.getLocation()))
                .toList();
        Set<UUID> ids = new HashSet<>();
        inside.forEach(player -> ids.add(player.getUniqueId()));
        Set<UUID> earlier = playersInRegion.put(region.id(), ids);
        if (earlier == null)
            return List.of();
        return inside.stream().filter(player -> !earlier.contains(player.getUniqueId())).toList();
    }

    static boolean crossed(long previous, long current, DayPeriod period) {
        long elapsed = Math.floorMod(current - previous, DayPeriod.TICKS_PER_DAY);
        long distance = Math.floorMod(period.startTick() - previous, DayPeriod.TICKS_PER_DAY);
        return elapsed <= DayPeriod.TICKS_PER_DAY / 2 && distance > 0 && distance <= elapsed;
    }

    private static final class Run {
        private final RegionalEvent event;
        private final List<Phase> phases;
        private final Map<UUID, NpcInstance> actors;
        private final Entity actor;
        private int phase;
        private int pending;

        private Run(RegionalEvent event, List<Phase> phases, Map<UUID, NpcInstance> actors, Entity actor) {
            this.event = event;
            this.phases = phases;
            this.actors = actors;
            this.actor = actor;
        }
    }
}
