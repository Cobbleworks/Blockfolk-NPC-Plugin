package dev.blockfolk.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.ActionLocation;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.Scene;
import dev.blockfolk.model.SceneEnding;
import dev.blockfolk.model.SceneRole;
import dev.blockfolk.model.SceneTrigger;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.SceneRepository;
import dev.blockfolk.util.UiText;

/**
 * Runs scenes: casts their roles, plays every role's lane at the same time,
 * passes cues between lanes, watches triggers and endings, and hands the NPCs
 * back to their normal routine afterwards.
 *
 * <p>
 * An NPC plays in one scene at a time. A scene that cannot cast a required role
 * is skipped instead of half-playing, and administrators get a short chat
 * message saying why.
 */
public final class SceneService {

    private static final long TICK_INTERVAL = 10L;
    /** Scenes that end "when everyone is done" stop after this long anyway. */
    private static final long LANES_FINISHED_LIMIT_TICKS = 30L * 60L * 20L;
    /** Wrap-up actions that never finish are cut off after this long. */
    private static final long WRAP_UP_LIMIT_TICKS = 5L * 60L * 20L;
    /** The same skip reason is announced at most once per this many ticks. */
    private static final long SKIP_NOTICE_THROTTLE_TICKS = 60L * 20L;
    private static final double SNOW_TEMPERATURE = 0.15;

    private final Plugin plugin;
    private final SceneRepository scenes;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final NpcBehaviourService behaviour;
    private final boolean notifySkips;
    private NpcCombatService combatService;
    private Function<String, Optional<ActionLocation>> locationResolver = ignored -> Optional.empty();
    private final Map<String, ActiveScene> active = new LinkedHashMap<>();
    private final Map<UUID, ActiveScene> castOf = new HashMap<>();
    private final Map<String, Long> cooldownUntilTick = new HashMap<>();
    private final Map<UUID, Long> lastWorldTimes = new HashMap<>();
    private final Map<UUID, Weather> lastWeather = new HashMap<>();
    private final Map<String, Set<UUID>> playersInside = new HashMap<>();
    private final Map<String, Long> skipNoticeUntilTick = new HashMap<>();
    private final Map<String, Skip> lastSkips = new HashMap<>();
    private BukkitTask task;
    private long tick;

    public SceneService(Plugin plugin, SceneRepository scenes, NpcDefinitionRepository definitions,
            NpcInstanceRegistry instances, NpcBehaviourService behaviour, boolean notifySkips) {
        this.plugin = plugin;
        this.scenes = scenes;
        this.definitions = definitions;
        this.instances = instances;
        this.behaviour = behaviour;
        this.notifySkips = notifySkips;
    }

    public void setCombatService(NpcCombatService combatService) {
        this.combatService = combatService;
    }

    public void setLocationResolver(Function<String, Optional<ActionLocation>> locationResolver) {
        this.locationResolver = locationResolver == null ? ignored -> Optional.empty() : locationResolver;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null)
            task.cancel();
        task = null;
        for (ActiveScene scene : List.copyOf(active.values()))
            forceEnd(scene);
        active.clear();
        castOf.clear();
        lastWorldTimes.clear();
        lastWeather.clear();
        playersInside.clear();
    }

    // ---------------------------------------------------------------- queries

    public boolean isRunning(String sceneKey) {
        return Scene.tryKey(sceneKey).map(active::containsKey).orElse(false);
    }

    /** Whether the instance currently plays a role, including its wrap-up. */
    public boolean isCast(NpcInstance instance) {
        return castOf.containsKey(instance.getId());
    }

    /** A one-line state for menus, such as "Running for 2 min (3 NPCs)". */
    public String status(String sceneKey) {
        ActiveScene scene = active.get(sceneKey);
        if (scene == null) {
            long remaining = cooldownUntilTick.getOrDefault(sceneKey, 0L) - tick;
            return remaining > 0 ? "Cooling down (" + duration(remaining) + " left)" : "Not running";
        }
        String phase = scene.phase == Phase.RUNNING ? "Running" : "Wrapping up";
        return phase + " for " + duration(tick - scene.startTick) + " (" + scene.members.size()
                + (scene.members.size() == 1 ? " NPC)" : " NPCs)");
    }

    /** Why the scene was skipped the last time, if it was skipped since startup. */
    public Optional<String> lastSkip(String sceneKey) {
        Skip skip = lastSkips.get(sceneKey);
        if (skip == null)
            return Optional.empty();
        return Optional.of(skip.reason() + " (" + duration(tick - skip.tick()) + " ago)");
    }

    /** The scene the instance plays in, if any. */
    public Optional<String> sceneOf(NpcInstance instance) {
        ActiveScene scene = castOf.get(instance.getId());
        return scene == null ? Optional.empty() : Optional.of(scene.scene.getDisplayName());
    }

    // ------------------------------------------------------- starting/stopping

    /**
     * Starts a scene by hand. Cooldown and chance only apply to triggers; skip
     * reasons are sent to {@code requester}.
     */
    public boolean startManually(String sceneKey, CommandSender requester, Entity actor) {
        Scene scene = scenes.find(sceneKey).orElse(null);
        if (scene == null) {
            if (requester != null)
                requester.sendMessage(UiText.error("Unknown scene: " + sceneKey));
            return false;
        }
        return tryStart(scene, actor, requester, false);
    }

    /**
     * Start Scene action. Runs a tick later so the calling lane can finish first.
     */
    public void startFromAction(String sceneKey, Entity actor) {
        if (sceneKey == null)
            return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Scene scene = scenes.find(sceneKey).orElse(null);
            if (scene == null) {
                plugin.getLogger().warning(() -> "A Start Scene action names the unknown scene '" + sceneKey + "'.");
                return;
            }
            if (active.containsKey(scene.getKey()))
                return;
            tryStart(scene, actor, null, true);
        });
    }

    /**
     * Ends a running scene: the first stop runs every role's wrap-up, a second stop
     * during the wrap-up releases the NPCs at once.
     */
    public boolean stop(String sceneKey, CommandSender requester) {
        ActiveScene scene = Scene.tryKey(sceneKey).map(active::get).orElse(null);
        if (scene == null) {
            if (requester != null)
                requester.sendMessage(UiText.warning("That scene is not running."));
            return false;
        }
        if (scene.phase == Phase.RUNNING) {
            beginWrapUp(scene);
            if (requester != null)
                requester.sendMessage(UiText.success("Scene '" + scene.scene.getDisplayName()
                        + "' is ending; its roles run their wrap-up actions. Stop again to end it now."));
        } else {
            forceEnd(scene);
            if (requester != null)
                requester.sendMessage(UiText.success("Scene '" + scene.scene.getDisplayName() + "' ended."));
        }
        return true;
    }

    /** Stop Scene action: like a first stop. */
    public void stopFromAction(String sceneKey) {
        ActiveScene scene = Scene.tryKey(sceneKey).map(active::get).orElse(null);
        if (scene != null && scene.phase == Phase.RUNNING)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (scene.phase == Phase.RUNNING && active.get(scene.scene.getKey()) == scene)
                    beginWrapUp(scene);
            });
    }

    /** Ends a scene immediately, for example because it was deleted. */
    public void forceStop(String sceneKey) {
        ActiveScene scene = active.get(sceneKey);
        if (scene != null)
            forceEnd(scene);
    }

    private boolean tryStart(Scene scene, Entity actor, CommandSender requester, boolean automatic) {
        if (active.containsKey(scene.getKey())) {
            if (requester != null)
                requester.sendMessage(UiText.warning("Scene '" + scene.getDisplayName() + "' is already running."));
            return false;
        }
        if (scene.getRoles().isEmpty()) {
            skip(scene, "it has no roles yet", requester, automatic);
            return false;
        }
        Casting casting = cast(scene, actor);
        if (casting.reason() != null) {
            skip(scene, casting.reason(), requester, automatic);
            return false;
        }
        begin(scene, casting.members(), actor);
        if (requester != null)
            requester.sendMessage(UiText.success("Started scene '" + scene.getDisplayName() + "' with "
                    + casting.members().size() + (casting.members().size() == 1 ? " NPC." : " NPCs.")));
        return true;
    }

    /** Automatic triggers respect the scene's chance and cooldown silently. */
    private void trigger(Scene scene, Entity actor) {
        if (!scene.isEnabled() || active.containsKey(scene.getKey()))
            return;
        if (tick < cooldownUntilTick.getOrDefault(scene.getKey(), 0L))
            return;
        if (scene.getChancePercent() < 100 && ThreadLocalRandom.current().nextInt(100) >= scene.getChancePercent())
            return;
        tryStart(scene, actor, null, true);
    }

    private Casting cast(Scene scene, Entity actor) {
        Location center = areaCenter(scene).orElse(null);
        Location reference = center != null ? center : actor == null ? null : actor.getLocation();
        Set<UUID> taken = new HashSet<>();
        List<Member> members = new ArrayList<>();
        for (SceneRole role : scene.getRoles()) {
            if (role.getPresetKey() == null)
                return Casting.failed("the role '" + role.getDisplayName() + "' has no NPC preset");
            NpcDefinition definition = definitions.find(role.getPresetKey()).orElse(null);
            if (definition == null)
                return Casting.failed("the NPC preset '" + role.getPresetKey() + "' of role '" + role.getDisplayName()
                        + "' no longer exists");
            List<NpcInstance> loaded = instances.findActive().stream()
                    .filter(instance -> instance.getDefinitionKey().equals(definition.getKey())).toList();
            // A scene with an area only casts NPCs from the area's world.
            if (center != null)
                loaded = loaded.stream()
                        .filter(instance -> center.getWorld().equals(instances.currentLocation(instance).getWorld()))
                        .toList();
            List<NpcInstance> free = loaded.stream().filter(instance -> !taken.contains(instance.getId()))
                    .filter(instance -> blocker(instance) == null).toList();
            if (role.getCast() == SceneRole.Cast.ALL) {
                for (NpcInstance instance : free) {
                    taken.add(instance.getId());
                    members.add(new Member(instance, role));
                }
                continue;
            }
            if (free.isEmpty()) {
                String name = definition.getDisplayName();
                if (loaded.isEmpty())
                    return Casting.failed(
                            "no " + name + " is loaded" + (center != null ? " in " + center.getWorld().getName() : "")
                                    + " (not spawned, or its chunk is not loaded)");
                NpcInstance busy = loaded.stream().filter(instance -> !taken.contains(instance.getId())).findFirst()
                        .orElse(null);
                return Casting.failed(busy == null
                        ? name + " plays two roles in this scene; an NPC plays one role per scene, so remove the"
                                + " extra role or move its steps into a follow-up scene"
                        : name + " " + blocker(busy));
            }
            NpcInstance chosen = reference == null
                    ? free.getFirst()
                    : free.stream().min(Comparator.comparingDouble(instance -> distanceSquared(instance, reference)))
                            .orElseThrow();
            taken.add(chosen.getId());
            members.add(new Member(chosen, role));
        }
        if (members.isEmpty())
            return Casting.failed("none of its NPCs is loaded or free");
        return new Casting(members, null);
    }

    private double distanceSquared(NpcInstance instance, Location reference) {
        Location location = instances.currentLocation(instance);
        return location.getWorld() == null || !location.getWorld().equals(reference.getWorld())
                ? Double.MAX_VALUE
                : location.distanceSquared(reference);
    }

    /** Why the instance cannot join a scene now, or null when it is free. */
    private String blocker(NpcInstance instance) {
        ActiveScene scene = castOf.get(instance.getId());
        if (scene != null)
            return "is busy in scene '" + scene.scene.getDisplayName() + "'";
        if (combatService != null && combatService.isEngaged(instance))
            return "is in combat";
        return null;
    }

    private void begin(Scene scene, List<Member> members, Entity actor) {
        Location center = areaCenter(scene).orElse(null);
        World world = center != null
                ? center.getWorld()
                : instances.currentLocation(members.getFirst().instance).getWorld();
        ActiveScene running = new ActiveScene(scene, tick, world, actor);
        active.put(scene.getKey(), running);
        lastSkips.remove(scene.getKey());
        for (Member member : members) {
            running.members.put(member.instance.getId(), member);
            castOf.put(member.instance.getId(), running);
            behaviour.interruptMovement(member.instance);
        }
        String detail = "The scene '" + scene.getDisplayName() + "' started.";
        for (Member member : List.copyOf(members)) {
            List<BehaviourAction> lane = member.role.getLane();
            if (lane.isEmpty()) {
                member.laneDone = true;
                continue;
            }
            Run run = new Run();
            member.run = run;
            behaviour.runSceneSequence(lane, member.instance, actor, detail, () -> isLive(running, member, run),
                    () -> laneFinished(running, member, run));
        }
        checkLanesFinished(running);
    }

    private boolean isLive(ActiveScene scene, Member member, Run run) {
        return !run.cancelled && active.get(scene.scene.getKey()) == scene
                && scene.members.get(member.instance.getId()) == member;
    }

    private void laneFinished(ActiveScene scene, Member member, Run run) {
        if (run.cancelled || member.run != run)
            return;
        member.laneDone = true;
        member.run = null;
        checkLanesFinished(scene);
    }

    private void checkLanesFinished(ActiveScene scene) {
        if (scene.phase != Phase.RUNNING || scene.scene.getEnding() != SceneEnding.LANES_FINISHED)
            return;
        if (scene.members.values().stream().allMatch(member -> member.laneDone))
            beginWrapUp(scene);
    }

    private void beginWrapUp(ActiveScene scene) {
        if (scene.phase != Phase.RUNNING)
            return;
        scene.phase = Phase.WRAPPING_UP;
        scene.wrapUpTick = tick;
        scene.waiters.clear();
        String detail = "The scene '" + scene.scene.getDisplayName() + "' ended.";
        for (Member member : List.copyOf(scene.members.values())) {
            if (member.run != null) {
                member.run.cancelled = true;
                member.run = null;
                behaviour.interruptMovement(member.instance);
            }
            List<BehaviourAction> wrapUp = member.role.getWrapUp();
            if (wrapUp.isEmpty()) {
                release(scene, member);
                continue;
            }
            Run run = new Run();
            member.run = run;
            behaviour.runSceneSequence(wrapUp, member.instance, scene.actor(), detail, () -> isLive(scene, member, run),
                    () -> {
                        if (!run.cancelled && member.run == run)
                            release(scene, member);
                    });
        }
        if (scene.members.isEmpty())
            finish(scene);
    }

    private void forceEnd(ActiveScene scene) {
        scene.phase = Phase.WRAPPING_UP;
        for (Member member : List.copyOf(scene.members.values())) {
            if (member.run != null) {
                member.run.cancelled = true;
                behaviour.interruptMovement(member.instance);
            }
            release(scene, member);
        }
        finish(scene);
    }

    private void release(ActiveScene scene, Member member) {
        member.run = null;
        scene.members.remove(member.instance.getId());
        castOf.remove(member.instance.getId(), scene);
        if (scene.members.isEmpty() && active.get(scene.scene.getKey()) == scene)
            finish(scene);
    }

    private void finish(ActiveScene scene) {
        if (active.get(scene.scene.getKey()) != scene)
            return;
        active.remove(scene.scene.getKey());
        scene.members.keySet().forEach(id -> castOf.remove(id, scene));
        scene.members.clear();
        scene.waiters.clear();
        if (scene.scene.getCooldownSeconds() > 0)
            cooldownUntilTick.put(scene.scene.getKey(), tick + scene.scene.getCooldownSeconds() * 20L);
    }

    // ------------------------------------------------- hooks for behaviours

    /**
     * The scene's replacement for a preset event while the instance plays a role,
     * or null when the preset's own behaviour applies.
     */
    public Reaction reaction(NpcInstance instance, BehaviourEvent event) {
        ActiveScene scene = castOf.get(instance.getId());
        if (scene == null || scene.phase != Phase.RUNNING)
            return null;
        Member member = scene.members.get(instance.getId());
        if (member == null || event == BehaviourEvent.IDLE && !member.laneDone)
            return null;
        List<BehaviourAction> actions = member.role.getReaction(event);
        if (actions.isEmpty())
            return null;
        return new Reaction(actions, () -> scene.phase == Phase.RUNNING && active.get(scene.scene.getKey()) == scene
                && scene.members.get(instance.getId()) == member);
    }

    /**
     * Give Cue: lets every lane waiting for this cue in the instance's scene go on.
     */
    public void cue(NpcInstance instance, String cue) {
        ActiveScene scene = castOf.get(instance.getId());
        if (scene == null || cue == null || scene.phase != Phase.RUNNING)
            return;
        String name = normalizeCue(cue);
        if (!scene.cues.add(name))
            return;
        List<Runnable> waiting = scene.waiters.remove(name);
        if (waiting != null)
            Bukkit.getScheduler().runTask(plugin, () -> waiting.forEach(Runnable::run));
    }

    /**
     * Wait For Cue: holds the lane until the cue is given. Returns false when the
     * lane may go on at once: the cue was already given, or the NPC plays no scene.
     */
    public boolean awaitCue(NpcInstance instance, String cue, Runnable continuation) {
        ActiveScene scene = castOf.get(instance.getId());
        if (scene == null || cue == null || scene.phase != Phase.RUNNING)
            return false;
        String name = normalizeCue(cue);
        if (scene.cues.contains(name))
            return false;
        scene.waiters.computeIfAbsent(name, ignored -> new ArrayList<>()).add(continuation);
        return true;
    }

    static String normalizeCue(String cue) {
        return cue.trim().toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------ ticking

    private void tick() {
        tick += TICK_INTERVAL;
        for (ActiveScene scene : List.copyOf(active.values()))
            tickActive(scene);
        Map<UUID, List<DayPeriod>> crossed = crossedPeriods();
        Map<UUID, WeatherChange> weatherChanges = weatherChanges();
        for (ActiveScene scene : List.copyOf(active.values()))
            checkEnding(scene, crossed, weatherChanges);
        for (Scene scene : scenes.findAll()) {
            if (!scene.isEnabled() || scene.getTriggers().isEmpty()) {
                playersInside.remove(scene.getKey());
                continue;
            }
            World world = sceneWorld(scene).orElse(null);
            if (world == null)
                continue;
            for (DayPeriod period : crossed.getOrDefault(world.getUID(), List.of())) {
                if (scene.hasTrigger(SceneTrigger.forPeriod(period)))
                    trigger(scene, null);
            }
            WeatherChange change = weatherChanges.get(world.getUID());
            if (change != null && weatherTriggers(scene, change, world))
                trigger(scene, null);
            if (scene.hasTrigger(SceneTrigger.PLAYER_ENTERS))
                checkPlayersEntering(scene);
            else
                playersInside.remove(scene.getKey());
        }
        cooldownUntilTick.values().removeIf(until -> until <= tick);
        skipNoticeUntilTick.values().removeIf(until -> until <= tick);
    }

    private void tickActive(ActiveScene scene) {
        Set<UUID> loaded = new HashSet<>();
        instances.findActive().forEach(instance -> loaded.add(instance.getId()));
        for (Member member : List.copyOf(scene.members.values())) {
            if (loaded.contains(member.instance.getId()))
                continue;
            // Despawned, dead, or unloaded: the scene goes on without it.
            if (member.run != null)
                member.run.cancelled = true;
            member.laneDone = true;
            release(scene, member);
        }
        if (active.get(scene.scene.getKey()) != scene)
            return;
        checkLanesFinished(scene);
        if (scene.phase == Phase.WRAPPING_UP && tick - scene.wrapUpTick >= WRAP_UP_LIMIT_TICKS) {
            plugin.getLogger().warning(() -> "The wrap-up of scene '" + scene.scene.getKey()
                    + "' took longer than five minutes and was cut off.");
            forceEnd(scene);
        }
    }

    private void checkEnding(ActiveScene scene, Map<UUID, List<DayPeriod>> crossed,
            Map<UUID, WeatherChange> weatherChanges) {
        if (scene.phase != Phase.RUNNING || active.get(scene.scene.getKey()) != scene)
            return;
        long elapsed = tick - scene.startTick;
        boolean end = switch (scene.scene.getEnding()) {
            case LANES_FINISHED -> elapsed >= LANES_FINISHED_LIMIT_TICKS;
            case DURATION -> elapsed >= scene.scene.getDurationSeconds() * 20L;
            case TIME_OF_DAY -> scene.world != null
                    && crossed.getOrDefault(scene.world.getUID(), List.of()).contains(scene.scene.getEndPeriod());
            case WEATHER_CHANGES -> scene.world != null && weatherChanges.containsKey(scene.world.getUID());
        };
        if (end)
            beginWrapUp(scene);
    }

    private Map<UUID, List<DayPeriod>> crossedPeriods() {
        Map<UUID, List<DayPeriod>> result = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            UUID id = world.getUID();
            seen.add(id);
            long current = Math.floorMod(world.getTime(), DayPeriod.TICKS_PER_DAY);
            Long previous = lastWorldTimes.put(id, current);
            if (previous == null || previous == current)
                continue;
            long elapsed = Math.floorMod(current - previous, DayPeriod.TICKS_PER_DAY);
            // A large jump is most likely an administrative time change.
            if (elapsed == 0L || elapsed > 12000L)
                continue;
            for (DayPeriod period : DayPeriod.values()) {
                long distance = Math.floorMod(period.startTick() - previous, DayPeriod.TICKS_PER_DAY);
                if (distance > 0L && distance <= elapsed)
                    result.computeIfAbsent(id, ignored -> new ArrayList<>()).add(period);
            }
        }
        lastWorldTimes.keySet().retainAll(seen);
        return result;
    }

    private Map<UUID, WeatherChange> weatherChanges() {
        Map<UUID, WeatherChange> result = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            UUID id = world.getUID();
            seen.add(id);
            Weather current = new Weather(world.hasStorm(), world.isThundering());
            Weather previous = lastWeather.put(id, current);
            if (previous != null && !previous.equals(current))
                result.put(id, new WeatherChange(previous, current));
        }
        lastWeather.keySet().retainAll(seen);
        return result;
    }

    private boolean weatherTriggers(Scene scene, WeatherChange change, World world) {
        boolean stormStarted = !change.before().storm() && change.after().storm();
        boolean stormStopped = change.before().storm() && !change.after().storm();
        boolean thunderStarted = !change.before().thunder() && change.after().thunder();
        if (thunderStarted && scene.hasTrigger(SceneTrigger.THUNDER))
            return true;
        if (stormStopped && scene.hasTrigger(SceneTrigger.CLEAR))
            return true;
        if (!stormStarted)
            return false;
        if (scene.hasTrigger(SceneTrigger.PRECIPITATION))
            return true;
        if (!scene.hasTrigger(SceneTrigger.RAIN) && !scene.hasTrigger(SceneTrigger.SNOW))
            return false;
        Location center = areaCenter(scene).orElse(world.getSpawnLocation());
        boolean cold = center.getWorld() != null && center.getBlock().getTemperature() < SNOW_TEMPERATURE;
        return cold ? scene.hasTrigger(SceneTrigger.SNOW) : scene.hasTrigger(SceneTrigger.RAIN);
    }

    private void checkPlayersEntering(Scene scene) {
        Location center = areaCenter(scene).orElse(null);
        if (center == null || center.getWorld() == null) {
            playersInside.remove(scene.getKey());
            return;
        }
        double radiusSquared = (double) scene.getAreaRadius() * scene.getAreaRadius();
        Set<UUID> inside = new HashSet<>();
        Player entered = null;
        Set<UUID> previous = playersInside.get(scene.getKey());
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || player.getLocation().distanceSquared(center) > radiusSquared)
                continue;
            inside.add(player.getUniqueId());
            if (previous != null && !previous.contains(player.getUniqueId()) && entered == null)
                entered = player;
        }
        playersInside.put(scene.getKey(), inside);
        if (entered != null)
            trigger(scene, entered);
    }

    private Optional<Location> areaCenter(Scene scene) {
        if (!scene.hasArea())
            return Optional.empty();
        return locationResolver.apply(scene.getAreaLocationKey()).map(ActionLocation::toLocation)
                .filter(location -> location.getWorld() != null);
    }

    /** The scene area's world, or the main world for scenes without an area. */
    private Optional<World> sceneWorld(Scene scene) {
        if (scene.hasArea())
            return areaCenter(scene).map(Location::getWorld);
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? Optional.empty() : Optional.of(worlds.getFirst());
    }

    // ----------------------------------------------------------- skipping

    private void skip(Scene scene, String reason, CommandSender requester, boolean automatic) {
        lastSkips.put(scene.getKey(), new Skip(reason, tick));
        String message = "Scene '" + scene.getDisplayName() + "' was skipped: " + reason + ".";
        if (requester != null) {
            requester.sendMessage(UiText.warning(message));
            return;
        }
        if (!automatic || !notifySkips)
            return;
        String throttleKey = scene.getKey() + "|" + reason;
        if (tick < skipNoticeUntilTick.getOrDefault(throttleKey, 0L))
            return;
        skipNoticeUntilTick.put(throttleKey, tick + SKIP_NOTICE_THROTTLE_TICKS);
        plugin.getLogger().info(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("blockfolk.admin"))
                player.sendMessage(UiText.warning(message));
        }
    }

    private static String duration(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        if (seconds < 60)
            return seconds + " s";
        long minutes = seconds / 60;
        return minutes < 60 ? minutes + " min" : minutes / 60 + " h " + minutes % 60 + " min";
    }

    // ------------------------------------------------------------- types

    /** A scene replacement for one preset event, valid while {@code alive}. */
    public record Reaction(List<BehaviourAction> actions, BooleanSupplier alive) {
    }

    private enum Phase {
        RUNNING, WRAPPING_UP
    }

    private static final class ActiveScene {
        private final Scene scene;
        private final long startTick;
        private final World world;
        private final UUID actorId;
        private final Map<UUID, Member> members = new LinkedHashMap<>();
        private final Set<String> cues = new HashSet<>();
        private final Map<String, List<Runnable>> waiters = new HashMap<>();
        private Phase phase = Phase.RUNNING;
        private long wrapUpTick;

        private ActiveScene(Scene scene, long startTick, World world, Entity actor) {
            this.scene = scene;
            this.startTick = startTick;
            this.world = world;
            this.actorId = actor == null ? null : actor.getUniqueId();
        }

        private Entity actor() {
            return actorId == null ? null : Bukkit.getEntity(actorId);
        }
    }

    private static final class Member {
        private final NpcInstance instance;
        private final SceneRole role;
        private Run run;
        private boolean laneDone;

        private Member(NpcInstance instance, SceneRole role) {
            this.instance = instance;
            this.role = role;
        }
    }

    /** One playing action row; cancelled when the scene moves on without it. */
    private static final class Run {
        private boolean cancelled;
    }

    private record Casting(List<Member> members, String reason) {
        static Casting failed(String reason) {
            return new Casting(List.of(), reason);
        }
    }

    private record Weather(boolean storm, boolean thunder) {
    }

    private record WeatherChange(Weather before, Weather after) {
    }

    private record Skip(String reason, long tick) {
    }
}
