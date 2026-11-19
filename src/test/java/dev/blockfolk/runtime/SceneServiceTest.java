package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.Scene;
import dev.blockfolk.model.SceneBeat;
import dev.blockfolk.model.SceneBeatAdvance;
import dev.blockfolk.model.SceneEnding;
import dev.blockfolk.model.SceneRole;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.SceneRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class SceneServiceTest {

    private final Map<String, Scene> scenes = new HashMap<>();
    private final Map<String, NpcDefinition> presets = new HashMap<>();
    private final List<NpcInstance> loaded = new ArrayList<>();
    /** Action row completions in start order, so tests can finish rows by hand. */
    private final List<Runnable> runningLanes = new ArrayList<>();
    private final List<BooleanSupplier> laneAlive = new ArrayList<>();
    private final List<NpcInstance> laneOwners = new ArrayList<>();
    private NpcBehaviourService behaviour;
    private SceneService service;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SceneServiceTest"));
        SceneRepository sceneRepository = mock(SceneRepository.class);
        when(sceneRepository.find(anyString())).thenAnswer(call -> Scene.tryKey(call.getArgument(0)).map(scenes::get));
        NpcDefinitionRepository definitions = mock(NpcDefinitionRepository.class);
        when(definitions.find(anyString())).thenAnswer(call -> Optional.ofNullable(presets.get(call.getArgument(0))));
        NpcInstanceRegistry instances = mock(NpcInstanceRegistry.class);
        when(instances.findActive()).thenAnswer(call -> List.copyOf(loaded));
        when(instances.currentLocation(any())).thenAnswer(call -> ((NpcInstance) call.getArgument(0)).getLocation());
        behaviour = mock(NpcBehaviourService.class);
        doAnswer(call -> {
            laneOwners.add(call.getArgument(1));
            laneAlive.add(call.getArgument(4));
            runningLanes.add(call.getArgument(5));
            return null;
        }).when(behaviour).runSceneSequence(any(), any(), any(), anyString(), any(), any());
        service = new SceneService(plugin, sceneRepository, definitions, instances, behaviour, true);
    }

    @Test
    void castsOneInstancePerRoleAndRunsEveryLaneAtOnce() {
        NpcInstance watcher = spawn("watcher");
        NpcInstance luigi = spawn("luigi");
        Scene train = scene("train", "watcher", "luigi");
        CommandSender admin = mock(CommandSender.class);

        assertTrue(service.startManually("train", admin, null));

        assertTrue(service.isRunning("train"));
        assertTrue(service.isCast(watcher));
        assertTrue(service.isCast(luigi));
        assertEquals(2, runningLanes.size());
        verify(behaviour).interruptMovement(watcher);
        assertEquals(Optional.of(train.getDisplayName()), service.sceneOf(luigi));
    }

    @Test
    void sceneEndsOnceEveryLaneFinishedAndReleasesItsNpcs() {
        NpcInstance watcher = spawn("watcher");
        NpcInstance luigi = spawn("luigi");
        scene("train", "watcher", "luigi");
        service.startManually("train", null, null);

        runningLanes.get(0).run();
        assertTrue(service.isRunning("train"));
        runningLanes.get(1).run();

        assertFalse(service.isRunning("train"));
        assertFalse(service.isCast(watcher));
        assertFalse(service.isCast(luigi));
    }

    @Test
    void busyNpcSkipsTheWholeSceneAndTellsTheRequesterWhy() {
        spawn("watcher");
        spawn("luigi");
        scene("train", "watcher", "luigi");
        scene("saloon", "luigi");
        service.startManually("train", null, null);
        CommandSender admin = mock(CommandSender.class);

        assertFalse(service.startManually("saloon", admin, null));

        assertFalse(service.isRunning("saloon"));
        assertTrue(message(admin).contains("busy in scene 'train'"), message(admin));
        assertTrue(service.lastSkip("saloon").orElseThrow().contains("busy in scene 'train'"));
    }

    @Test
    void missingNpcIsReportedAndNothingStarts() {
        spawn("watcher");
        preset("luigi");
        scene("train", "watcher", "luigi");
        CommandSender admin = mock(CommandSender.class);

        assertFalse(service.startManually("train", admin, null));

        assertTrue(message(admin).contains("no Luigi is loaded"), message(admin));
        assertTrue(runningLanes.isEmpty());
        verify(behaviour, never()).interruptMovement(any());
    }

    @Test
    void everyFreeInstanceOfARolesNpcTakesPart() {
        NpcInstance first = spawn("guard");
        NpcInstance second = spawn("guard");
        scene("alarm", "guard");
        scene("patrol", "guard");

        assertTrue(service.startManually("alarm", null, null));
        assertTrue(service.isCast(first));
        assertTrue(service.isCast(second));
        assertEquals(2, runningLanes.size());

        CommandSender admin = mock(CommandSender.class);
        assertFalse(service.startManually("patrol", admin, null));
        assertTrue(message(admin).contains("Guard is busy in scene 'alarm'"), message(admin));
    }

    @Test
    void busyInstancesStayOutWhileTheFreeOnesPlay() {
        spawn("watcher");
        NpcInstance busyGuard = spawn("guard");
        scene("train", "watcher", "guard");
        service.startManually("train", null, null);
        NpcInstance freeGuard = spawn("guard");
        scene("patrol", "guard");

        assertTrue(service.startManually("patrol", null, null));

        assertEquals(Optional.of("train"), service.sceneOf(busyGuard));
        assertEquals(Optional.of("patrol"), service.sceneOf(freeGuard));
    }

    @Test
    void aiContextDescribesTheSceneAndTheOtherNpcs() {
        NpcInstance watcher = spawn("watcher");
        spawn("luigi");
        spawn("guard");
        spawn("guard");
        Scene train = scene("train", "watcher", "luigi", "guard");
        train.setDisplayName("Last Train Is Late");
        train.setDescription("The evening train is overdue.\nThe town waits at the station.");

        assertTrue(service.aiContext(watcher).isEmpty());
        service.startManually("train", null, null);
        String context = service.aiContext(watcher).orElseThrow();

        assertTrue(context.contains("Name: Last Train Is Late"), context);
        assertTrue(context.contains("Phase: playing"), context);
        assertTrue(context.contains("The evening train is overdue.\nThe town waits at the station."), context);
        assertTrue(context.contains("Others in the scene: Luigi, Guard (2)"), context);
    }

    @Test
    void beatsPlayOneAfterAnotherWhileTheCastIsHeld() {
        NpcInstance watcher = spawn("watcher");
        NpcInstance luigi = spawn("luigi");
        Scene train = scene("train", "watcher", "luigi");
        SceneBeat first = train.getBeats().getFirst();
        first.setActions("luigi", List.of());
        train.addBeat().setActions("luigi", List.of(wave()));

        service.startManually("train", null, null);

        assertEquals(List.of(watcher), laneOwners);
        assertTrue(service.isCast(luigi));
        assertEquals(Optional.of(first.getId()), service.currentBeat("train"));
        assertTrue(service.status("train").contains("beat 1/2"), service.status("train"));

        runningLanes.getFirst().run();

        assertEquals(List.of(watcher, luigi), laneOwners);
        assertTrue(service.status("train").contains("beat 2/2"), service.status("train"));
        runningLanes.get(1).run();
        assertFalse(service.isRunning("train"));
    }

    @Test
    void roleDoneMovesOnAndCutsOffTheOthers() {
        spawn("watcher");
        NpcInstance luigi = spawn("luigi");
        Scene train = scene("train", "watcher", "luigi");
        SceneBeat first = train.getBeats().getFirst();
        first.setAdvance(SceneBeatAdvance.ROLE_DONE);
        first.setAdvanceRoleId("watcher");
        train.addBeat().setActions("luigi", List.of(wave()));
        service.startManually("train", null, null);

        runningLanes.getFirst().run();

        assertFalse(laneAlive.get(1).getAsBoolean());
        verify(behaviour, org.mockito.Mockito.times(2)).interruptMovement(luigi);
        assertEquals(luigi, laneOwners.get(2));
        assertTrue(service.isRunning("train"));
    }

    @Test
    void nextBeatOnlyWaitsForTheActionAndMovesOnOnce() {
        NpcInstance watcher = spawn("watcher");
        Scene train = scene("train", "watcher");
        train.getBeats().getFirst().setAdvance(SceneBeatAdvance.NEXT_BEAT_ONLY);
        train.addBeat().setActions("watcher", List.of(wave()));
        train.addBeat().setActions("watcher", List.of(wave()));
        service.startManually("train", null, null);

        runningLanes.getFirst().run();
        assertEquals(1, runningLanes.size());

        List<Runnable> scheduled = new ArrayList<>();
        try (var bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(call -> {
                scheduled.add(call.getArgument(1));
                return null;
            });
            // Two Next Beat actions in the same beat, e.g. two players clicking.
            service.nextBeat(watcher);
            service.nextBeat(watcher);
        }
        scheduled.forEach(Runnable::run);

        assertEquals(2, runningLanes.size());
        assertTrue(service.status("train").contains("beat 2/3"), service.status("train"));
    }

    @Test
    void afterTimeMovesOnOnceTheTimeIsUp() {
        spawn("watcher");
        Scene train = scene("train", "watcher");
        SceneBeat first = train.getBeats().getFirst();
        first.setAdvance(SceneBeatAdvance.AFTER_TIME);
        first.setSeconds(12);
        train.addBeat().setActions("watcher", List.of(wave()));
        ArgumentCaptor<Runnable> timer = ArgumentCaptor.forClass(Runnable.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.startManually("train", null, null);
            verify(scheduler).runTaskLater(any(Plugin.class), timer.capture(), org.mockito.ArgumentMatchers.eq(240L));
        }

        // The watcher's row is still running when the time is up; it is cut off.
        assertEquals(1, runningLanes.size());
        timer.getValue().run();

        assertEquals(2, runningLanes.size());
        assertFalse(laneAlive.getFirst().getAsBoolean());
    }

    @Test
    void anotherEndingKeepsTheCastAfterTheLastBeat() {
        NpcInstance wago = spawn("wago");
        NpcInstance smith = spawn("smith");
        Scene market = scene("market", "wago", "smith");
        market.setEnding(SceneEnding.DURATION);
        market.getBeats().getFirst().setActions("smith", List.of());
        market.addBeat().setActions("smith", List.of(wave()));
        market.getRoles().getFirst().setReaction(BehaviourEvent.IDLE, List.of(wave()));
        market.getRoles().get(1).setReaction(BehaviourEvent.IDLE, List.of(wave()));
        service.startManually("market", null, null);

        // Smith acts later, so his idle reaction waits; Wago's waits for his row.
        assertNull(service.reaction(smith, BehaviourEvent.IDLE));
        assertNull(service.reaction(wago, BehaviourEvent.IDLE));
        runningLanes.getFirst().run();
        assertNotNull(service.reaction(wago, BehaviourEvent.IDLE));
        assertNull(service.reaction(smith, BehaviourEvent.IDLE));
        runningLanes.get(1).run();

        assertTrue(service.isRunning("market"));
        assertTrue(service.isCast(smith));
        assertTrue(service.currentBeat("market").isEmpty());
        assertTrue(service.status("market").contains("every beat done"), service.status("market"));
        assertNotNull(service.reaction(smith, BehaviourEvent.IDLE));
    }

    @Test
    void aQuietSceneSuppressesAiForItsCastUntilItEnds() {
        NpcInstance watcher = spawn("watcher");
        NpcInstance stranger = spawn("stranger");
        Scene train = scene("train", "watcher");
        train.setTriggersAiBehaviour(false);
        scene("loud", "stranger");

        service.startManually("train", null, null);
        service.startManually("loud", null, null);

        assertTrue(service.suppressesAi(watcher));
        assertFalse(service.suppressesAi(stranger));
        service.forceStop("train");
        assertFalse(service.suppressesAi(watcher));
    }

    @Test
    void reactionsReplacePresetEventsOnlyWhileTheSceneRuns() {
        NpcInstance wago = spawn("wago");
        Scene market = scene("market", "wago");
        market.setEnding(SceneEnding.DURATION);
        SceneRole role = market.getRoles().getFirst();
        role.setReaction(BehaviourEvent.PLAYER_APPROACH,
                List.of(new BehaviourAction(BehaviourActionType.OPEN_SHOP, "wago")));
        role.setReaction(BehaviourEvent.IDLE, List.of(new BehaviourAction(BehaviourActionType.WAVE, null)));
        service.startManually("market", null, null);

        SceneService.Reaction approach = service.reaction(wago, BehaviourEvent.PLAYER_APPROACH);
        assertNotNull(approach);
        assertTrue(approach.alive().getAsBoolean());
        assertNull(service.reaction(wago, BehaviourEvent.RIGHT_CLICK));
        // Idle reactions wait until the role played its part in every beat.
        assertNull(service.reaction(wago, BehaviourEvent.IDLE));
        runningLanes.getFirst().run();
        assertNotNull(service.reaction(wago, BehaviourEvent.IDLE));

        service.forceStop("market");

        assertFalse(approach.alive().getAsBoolean());
        assertNull(service.reaction(wago, BehaviourEvent.PLAYER_APPROACH));
    }

    @Test
    void stoppingRunsTheWrapUpBeforeReleasingTheNpc() {
        NpcInstance watcher = spawn("watcher");
        Scene train = scene("train", "watcher");
        train.getRoles().getFirst().setWrapUp(List.of(new BehaviourAction(BehaviourActionType.GO_BACK, null)));
        service.startManually("train", null, null);

        service.stop("train", null);

        assertFalse(laneAlive.getFirst().getAsBoolean());
        assertTrue(service.isCast(watcher));
        assertEquals(2, runningLanes.size());
        runningLanes.get(1).run();
        assertFalse(service.isCast(watcher));
        assertFalse(service.isRunning("train"));
    }

    private void preset(String presetKey) {
        presets.computeIfAbsent(presetKey, key -> {
            NpcDefinition definition = new NpcDefinition(key);
            definition.setDisplayName(Character.toUpperCase(key.charAt(0)) + key.substring(1));
            return definition;
        });
    }

    private NpcInstance spawn(String presetKey) {
        preset(presetKey);
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), presetKey, new Location(null, 0, 64, 0));
        loaded.add(instance);
        return instance;
    }

    private Scene scene(String key, String... presetKeys) {
        Scene scene = new Scene(key);
        for (String preset : presetKeys) {
            SceneRole role = scene.addRole(preset);
            scene.getBeats().getFirst().setActions(role.getId(), List.of(wave()));
        }
        scenes.put(scene.getKey(), scene);
        return scene;
    }

    private static BehaviourAction wave() {
        return new BehaviourAction(BehaviourActionType.WAVE, null);
    }

    private static String message(CommandSender sender) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        return PlainTextComponentSerializer.plainText().serialize(captor.getValue());
    }
}
