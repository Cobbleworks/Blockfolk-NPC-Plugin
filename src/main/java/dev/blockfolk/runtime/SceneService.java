package dev.blockfolk.runtime;

import java.util.ArrayList;
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
import dev.blockfolk.model.SceneBeat;
import dev.blockfolk.model.SceneBeatAdvance;
import dev.blockfolk.model.SceneEnding;
import dev.blockfolk.model.SceneRole;
import dev.blockfolk.model.SceneTrigger;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.SceneRepository;
import dev.blockfolk.util.UiText;

/**
 * Runs scenes: casts their roles, plays the scene's beats one after another
 * (every role's actions of a beat at the same time), watches triggers and
 * endings, and hands the NPCs back to their normal routine afterwards. The cast
 * is held from the first beat to the last, so a started scene plays through.
 *
 * <p>
 * An NPC plays in one scene at a time. A scene that cannot cast a required role
 * is skipped instead of half-playing, and administrators get a short chat
 * message saying why.
 */
public final class SceneService {

    private static final long TICK_INTERVAL = 10L;
    /** Scenes that end after their last beat stop after this long anyway. */
    private static final long BEATS_FINISHED_LIMIT_TICKS = 30L * 60L * 20L;
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

    /**
     * An opaque token for the scene run the instance plays in, or null. Each run
     * has its own, so a routine can tell whether a scene took the NPC over since it
     * started.
     */
    public Object sceneToken(NpcInstance instance) {
        return castOf.get(instance.getId());
    }

    /** A one-line state for menus, such as "Running for 2 min (3 NPCs)". */
    public String status(String sceneKey) {
        ActiveScene scene = active.get(sceneKey);
        if (scene == null) {
            long remaining = cooldownUntilTick.getOrDefault(sceneKey, 0L) - tick;
            return remaining > 0 ? "Cooling down (" + duration(remaining) + " left)" : "Not running";
        }
        String phase = scene.phase == Phase.WRAPPING_UP
                ? "Wrapping up"
                : scene.scriptDone ? "Running, every beat done," : "Running beat " + beatLabel(scene) + ",";
        return phase + " for " + duration(tick - scene.startTick) + " (" + scene.members.size()
                + (scene.members.size() == 1 ? " NPC)" : " NPCs)");
    }

    /**
     * The id of the beat the scene plays now; empty when it is not running, every
     * beat is done, or it is wrapping up.
     */
    public Optional<String> currentBeat(String sceneKey) {
        ActiveScene scene = active.get(sceneKey);
        if (scene == null || scene.phase != Phase.RUNNING || scene.scriptDone || scene.beat == null)
            return Optional.empty();
        return Optional.of(scene.beat.getId());
    }

    private static String beatLabel(ActiveScene scene) {
        return (scene.beatIndex + 1) + "/" + scene.scene.getBeats().size();
    }

    /** Why the scene was skipped the last time, if it was skipped since startup. */
    public Optional<String> lastSkip(String sceneKey) {
        Skip skip = lastSkips.get(sceneKey);
        if (skip == null)
            return Optional.empty();
        return Optional.of(skip.reason() + " (" + duration(tick - skip.tick()) + " ago)");
    }

    /**
     * Whether the instance plays in a scene that keeps AI quiet: its lines start no
     * AI replies, it answers no other NPC, and its preset's AI Trigger actions
     * wait. Holds during the wrap-up too.
     */
    public boolean suppressesAi(NpcInstance instance) {
        ActiveScene scene = castOf.get(instance.getId());
        return scene != null && !scene.scene.triggersAiBehaviour();
    }

    /** The scene the instance plays in, if any. */
    public Optional<String> sceneOf(NpcInstance instance) {
        ActiveScene scene = castOf.get(instance.getId());
        return scene == null ? Optional.empty() : Optional.of(scene.scene.getDisplayName());
    }

    /**
     * What an AI-controlled NPC should know about the scene it plays in: its name,
     * description, whether it is ending, who else takes part, and how far the scene
     * got. Empty when the instance plays no scene.
     */
    public Optional<String> aiContext(NpcInstance instance) {
        ActiveScene scene = castOf.get(instance.getId());
        if (scene == null)
            return Optional.empty();
        StringBuilder out = new StringBuilder();
        out.append("Name: ").append(scene.scene.getDisplayName()).append('\n');
        out.append("Phase: ").append(scene.phase == Phase.RUNNING ? "playing" : "ending (wrapping up)").append('\n');
        if (!scene.scene.getDescription().isBlank())
            out.append("Description:\n").append(scene.scene.getDescription()).append('\n');
        Map<String, Integer> others = new LinkedHashMap<>();
        for (Member member : scene.members.values()) {
            if (member.instance.getId().equals(instance.getId()))
                continue;
            String name = definitions.find(member.instance.getDefinitionKey()).map(NpcDefinition::getDisplayName)
                    .orElse(member.instance.getDefinitionKey());
            others.merge(name, 1, Integer::sum);
        }
        out.append("Others in the scene: ");
        if (others.isEmpty())
            out.append("nobody");
        else
            out.append(String.join(", ", others.entrySet().stream().map(
                    entry -> entry.getValue() == 1 ? entry.getKey() : entry.getKey() + " (" + entry.getValue() + ")")
                    .toList()));
        out.append('\n');
        if (scene.phase == Phase.RUNNING && scene.scene.getBeats().size() > 1)
            out.append("Progress: ")
                    .append(scene.scriptDone
                            ? "every beat is done"
                            : "beat " + (scene.beatIndex + 1) + " of " + scene.scene.getBeats().size())
                    .append('\n');
        return Optional.of(out.toString());
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
        return tryStart(scene, actor, requester, false, null);
    }

    /**
     * Start Scene action. Runs a tick later so the calling actions can finish
     * first.
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
            tryStart(scene, actor, null, true, null);
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

    /**
     * Casts and begins the scene. {@code world} limits the cast to NPCs in that
     * world (the world whose time, weather, or area started it); null casts NPCs
     * from every world.
     */
    private boolean tryStart(Scene scene, Entity actor, CommandSender requester, boolean automatic, World world) {
        if (active.containsKey(scene.getKey())) {
            if (requester != null)
                requester.sendMessage(UiText.warning("Scene '" + scene.getDisplayName() + "' is already running."));
            return false;
        }
        if (scene.getRoles().isEmpty()) {
            skip(scene, "it has no roles yet", requester, automatic);
            return false;
        }
        Casting casting = cast(scene, world);
        if (casting.reason() != null) {
            skip(scene, casting.reason(), requester, automatic);
            return false;
        }
        begin(scene, casting.members(), actor, world);
        if (requester != null)
            requester.sendMessage(UiText.success("Started scene '" + scene.getDisplayName() + "' with "
                    + casting.members().size() + (casting.members().size() == 1 ? " NPC." : " NPCs.")));
        return true;
    }

    /** Automatic triggers respect the scene's chance and cooldown silently. */
    private void trigger(Scene scene, Entity actor, World world) {
        if (!scene.isEnabled() || active.containsKey(scene.getKey()))
            return;
        if (tick < cooldownUntilTick.getOrDefault(scene.getKey(), 0L))
            return;
        if (scene.getChancePercent() < 100 && ThreadLocalRandom.current().nextInt(100) >= scene.getChancePercent())
            return;
        tryStart(scene, actor, null, true, world);
    }

    /**
     * Every free loaded instance of a role's preset plays it. A role without a
     * single free instance skips the whole scene, so it never plays with a part
     * missing.
     */
    private Casting cast(Scene scene, World world) {
        Set<UUID> taken = new HashSet<>();
        List<Member> members = new ArrayList<>();
        for (SceneRole role : scene.getRoles()) {
            if (role.getPresetKey() == null)
                return Casting.failed("one of its roles has no NPC preset");
            NpcDefinition definition = definitions.find(role.getPresetKey()).orElse(null);
            if (definition == null)
                return Casting
                        .failed("the NPC preset '" + role.getPresetKey() + "' of one of its roles no longer exists");
            String name = definition.getDisplayName();
            List<NpcInstance> loaded = instances.findActive().stream()
                    .filter(instance -> instance.getDefinitionKey().equals(definition.getKey()))
                    .filter(instance -> world == null || world.equals(instances.currentLocation(instance).getWorld()))
                    .toList();
            if (loaded.isEmpty()) {
                boolean awaitingRespawn = instances.findAll().stream()
                        .anyMatch(instance -> instance.getDefinitionKey().equals(definition.getKey())
                                && instance.isAwaitingRespawn()
                                && (world == null || instance.getStoredLocation().worldName().equals(world.getName())));
                return Casting.failed("no " + name + " is loaded" + (world != null ? " in " + world.getName() : "")
                        + (awaitingRespawn ? " (waiting for respawn)" : " (not spawned, or its chunk is not loaded)"));
            }
            List<NpcInstance> unclaimed = loaded.stream().filter(instance -> !taken.contains(instance.getId()))
                    .toList();
            if (unclaimed.isEmpty())
                return Casting
                        .failed(name + " plays two roles in this scene; an NPC plays one role per scene, so remove"
                                + " the extra role and give its actions to the other one");
            List<NpcInstance> free = unclaimed.stream().filter(instance -> blocker(instance) == null).toList();
            if (free.isEmpty())
                return Casting.failed(name + " " + blocker(unclaimed.getFirst()));
            for (NpcInstance instance : free) {
                taken.add(instance.getId());
                members.add(new Member(instance, role));
            }
        }
        return new Casting(members, null);
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

    private void begin(Scene scene, List<Member> members, Entity actor, World triggerWorld) {
        // Time-of-day and weather endings follow the world it started in, or the
        // world of its first NPC.
        World world = triggerWorld != null
                ? triggerWorld
                : instances.currentLocation(members.getFirst().instance).getWorld();
        ActiveScene running = new ActiveScene(scene, tick, world, actor);
        active.put(scene.getKey(), running);
        lastSkips.remove(scene.getKey());
        for (Member member : members) {
            running.members.put(member.instance.getId(), member);
            castOf.put(member.instance.getId(), running);
            behaviour.interruptMovement(member.instance);
        }
        startBeat(running, 0);
    }

    private boolean isLive(ActiveScene scene, Member member, Run run) {
        return !run.cancelled && active.get(scene.scene.getKey()) == scene
                && scene.members.get(member.instance.getId()) == member;
    }

    // ----------------------------------------------------------------- beats

    /**
     * Starts the beat at {@code index}: actions still running from the previous
     * beat are cut off, and every role with actions in this beat starts them.
     */
    private void startBeat(ActiveScene scene, int index) {
        SceneBeat beat = scene.scene.getBeats().get(index);
        scene.beatIndex = index;
        scene.beat = beat;
        scene.beatSerial++;
        int serial = scene.beatSerial;
        String detail = index == 0
                ? "The scene '" + scene.scene.getDisplayName() + "' started."
                : "The scene '" + scene.scene.getDisplayName() + "' moved on to beat " + (index + 1) + ".";
        // A row that finishes at once must not move the scene on while the
        // other rows of this beat are still being started.
        scene.startingBeat = true;
        for (Member member : List.copyOf(scene.members.values())) {
            cutRun(member);
            List<BehaviourAction> actions = beat.getActions(member.role.getId());
            member.beatDone = actions.isEmpty();
            if (actions.isEmpty())
                continue;
            Run run = new Run();
            member.run = run;
            behaviour.runSceneSequence(actions, member.instance, scene.actor(), detail,
                    () -> isLive(scene, member, run), () -> beatFinished(scene, member, run));
            if (scene.beatSerial != serial || active.get(scene.scene.getKey()) != scene) {
                scene.startingBeat = false;
                return;
            }
        }
        scene.startingBeat = false;
        if (beat.getAdvance() == SceneBeatAdvance.AFTER_TIME)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (scene.beatSerial == serial)
                    advance(scene);
            }, beat.getSeconds() * 20L);
        checkBeatDone(scene);
    }

    /** Stops the member's running actions, if any. */
    private void cutRun(Member member) {
        if (member.run == null)
            return;
        member.run.cancelled = true;
        member.run = null;
        behaviour.interruptMovement(member.instance);
    }

    private void beatFinished(ActiveScene scene, Member member, Run run) {
        if (run.cancelled || member.run != run)
            return;
        member.run = null;
        member.beatDone = true;
        checkBeatDone(scene);
    }

    /** Moves on once the current beat's advance rule is met. */
    private void checkBeatDone(ActiveScene scene) {
        if (!isPlaying(scene) || scene.startingBeat)
            return;
        SceneBeat beat = scene.beat;
        boolean done = switch (beat.getAdvance()) {
            case EVERYONE_DONE -> scene.members.values().stream().allMatch(member -> member.beatDone);
            // Without the chosen role (removed, or its NPCs left), wait for everyone.
            case ROLE_DONE -> beat.getAdvanceRoleId() == null || scene.scene.findRole(beat.getAdvanceRoleId()).isEmpty()
                    ? scene.members.values().stream().allMatch(member -> member.beatDone)
                    : scene.members.values().stream()
                            .filter(member -> member.role.getId().equals(beat.getAdvanceRoleId()))
                            .allMatch(member -> member.beatDone);
            case AFTER_TIME, NEXT_BEAT_ONLY -> false;
        };
        if (done)
            advance(scene);
    }

    /**
     * Starts the next beat. After the last beat the scene ends if it ends after its
     * last beat, and otherwise holds its cast until its ending.
     */
    private void advance(ActiveScene scene) {
        if (!isPlaying(scene))
            return;
        int next = scene.beatIndex + 1;
        if (next < scene.scene.getBeats().size()) {
            startBeat(scene, next);
            return;
        }
        scene.scriptDone = true;
        scene.beatSerial++;
        for (Member member : scene.members.values()) {
            cutRun(member);
            member.beatDone = true;
        }
        if (scene.scene.getEnding() == SceneEnding.BEATS_FINISHED)
            beginWrapUp(scene);
    }

    /** Whether the scene still works through its beats. */
    private boolean isPlaying(ActiveScene scene) {
        return scene.phase == Phase.RUNNING && !scene.scriptDone && scene.beat != null
                && active.get(scene.scene.getKey()) == scene;
    }

    /**
     * Next Beat action: the instance's scene moves on to its next beat, cutting off
     * what still runs in the current one. Runs a tick later so the calling actions
     * finish first; several Next Beat actions in the same beat move on only once.
     */
    public void nextBeat(NpcInstance instance) {
        ActiveScene scene = castOf.get(instance.getId());
        if (scene == null || !isPlaying(scene))
            return;
        int serial = scene.beatSerial;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (scene.beatSerial == serial)
                advance(scene);
        });
    }

    /**
     * Whether the member has nothing left to do in the script: it finished the
     * current beat and has no actions in a later one.
     */
    private static boolean partDone(ActiveScene scene, Member member) {
        if (scene.scriptDone)
            return true;
        if (!member.beatDone)
            return false;
        List<SceneBeat> beats = scene.scene.getBeats();
        for (int index = scene.beatIndex + 1; index < beats.size(); index++) {
            if (beats.get(index).hasActions(member.role.getId()))
                return false;
        }
        return true;
    }

    private void beginWrapUp(ActiveScene scene) {
        if (scene.phase != Phase.RUNNING)
            return;
        scene.phase = Phase.WRAPPING_UP;
        scene.wrapUpTick = tick;
        scene.beatSerial++;
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
        // Idle reactions wait until the role played its part in every beat.
        if (member == null || event == BehaviourEvent.IDLE && !partDone(scene, member))
            return null;
        List<BehaviourAction> actions = member.role.getReaction(event);
        if (actions.isEmpty())
            return null;
        return new Reaction(actions, () -> scene.phase == Phase.RUNNING && active.get(scene.scene.getKey()) == scene
                && scene.members.get(instance.getId()) == member);
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
        Map<String, Map<World, Location>> presetWorlds = crossed.isEmpty() && weatherChanges.isEmpty()
                ? Map.of()
                : presetWorlds();
        for (Scene scene : scenes.findAll()) {
            if (!scene.isEnabled() || scene.getTriggers().isEmpty()) {
                playersInside.remove(scene.getKey());
                continue;
            }
            // Time and weather come from the worlds the scene's NPCs are in.
            for (Map.Entry<World, Location> entry : sceneWorlds(scene, presetWorlds).entrySet()) {
                World world = entry.getKey();
                for (DayPeriod period : crossed.getOrDefault(world.getUID(), List.of())) {
                    if (scene.hasTrigger(SceneTrigger.forPeriod(period)))
                        trigger(scene, null, world);
                }
                WeatherChange change = weatherChanges.get(world.getUID());
                if (change != null && weatherTriggers(scene, change, entry.getValue()))
                    trigger(scene, null, world);
            }
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
            member.beatDone = true;
            release(scene, member);
        }
        if (active.get(scene.scene.getKey()) != scene)
            return;
        checkBeatDone(scene);
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
            case BEATS_FINISHED -> elapsed >= BEATS_FINISHED_LIMIT_TICKS;
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

    /** {@code where} is an NPC of the scene; its biome decides rain or snow. */
    private boolean weatherTriggers(Scene scene, WeatherChange change, Location where) {
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
        boolean cold = where.getWorld() != null && where.getBlock().getTemperature() < SNOW_TEMPERATURE;
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
            trigger(scene, entered, center.getWorld());
    }

    /** The centre of the area the Player Enters trigger watches. */
    private Optional<Location> areaCenter(Scene scene) {
        if (!scene.hasArea())
            return Optional.empty();
        return locationResolver.apply(scene.getAreaLocationKey()).map(ActionLocation::toLocation)
                .filter(location -> location.getWorld() != null);
    }

    /**
     * For every preset, the worlds its loaded instances are in, each with the
     * location of one of them.
     */
    private Map<String, Map<World, Location>> presetWorlds() {
        Map<String, Map<World, Location>> result = new HashMap<>();
        for (NpcInstance instance : instances.findActive()) {
            Location location = instances.currentLocation(instance);
            if (location.getWorld() != null)
                result.computeIfAbsent(instance.getDefinitionKey(), ignored -> new LinkedHashMap<>())
                        .putIfAbsent(location.getWorld(), location);
        }
        return result;
    }

    /** The worlds the scene's NPCs are loaded in, each with one NPC's location. */
    private static Map<World, Location> sceneWorlds(Scene scene, Map<String, Map<World, Location>> presetWorlds) {
        Map<World, Location> result = new LinkedHashMap<>();
        for (SceneRole role : scene.getRoles()) {
            if (role.getPresetKey() != null)
                presetWorlds.getOrDefault(role.getPresetKey(), Map.of()).forEach(result::putIfAbsent);
        }
        return result;
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
        private Phase phase = Phase.RUNNING;
        private long wrapUpTick;
        /** The beat playing now, and its position when it started. */
        private SceneBeat beat;
        private int beatIndex;
        /** Changes whenever the scene moves on, so late callbacks are ignored. */
        private int beatSerial;
        private boolean startingBeat;
        /** Every beat is done; the cast stays until the scene's ending. */
        private boolean scriptDone;

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
        /** Finished its actions in the current beat, or has none in it. */
        private boolean beatDone;

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
