package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SceneTest {

    @Test
    void keysAreNormalizedPerGroupAndTheNameKeepsItsLastPart() {
        Scene scene = Scene.create("Western/Last Train Is Late");

        assertEquals("western/last-train-is-late", scene.getKey());
        assertEquals("Last Train Is Late", scene.getDisplayName());
        assertEquals("jungle/idolwasstolen", Scene.normalizeKey("jungle/idolWasStolen"));
        assertThrows(IllegalArgumentException.class, () -> new Scene("western//train"));
        assertThrows(IllegalArgumentException.class, () -> new Scene("../train"));
        assertTrue(Scene.tryKey("/broken").isEmpty());
    }

    @Test
    void rolesGetUniqueIdsEvenForTheSamePreset() {
        Scene scene = new Scene("market");

        SceneRole first = scene.addRole("shopkeeper");
        SceneRole second = scene.addRole("shopkeeper");

        assertEquals("shopkeeper", first.getId());
        assertEquals("shopkeeper-2", second.getId());
        assertTrue(scene.removeRole("shopkeeper"));
        assertEquals(List.of(second), scene.getRoles());
    }

    @Test
    void findsTheRoleAPresetAlreadyPlays() {
        Scene scene = new Scene("market");
        SceneRole smith = scene.addRole("Cranky Kong");

        assertEquals(smith, scene.roleOf("cranky-kong", null).orElseThrow());
        assertTrue(scene.roleOf("cranky-kong", smith.getId()).isEmpty());
        assertTrue(scene.features("Cranky Kong"));
        assertFalse(scene.features("luigi"));
    }

    @Test
    void descriptionsKeepTheirLinesAndAreCappedInLength() {
        Scene scene = new Scene("market");

        scene.setDescription("  First line.\r\nSecond line.  ");
        assertEquals("First line.\nSecond line.", scene.getDescription());

        scene.setDescription("x".repeat(Scene.MAX_DESCRIPTION_LENGTH + 50));
        assertEquals(Scene.MAX_DESCRIPTION_LENGTH, scene.getDescription().length());
    }

    @Test
    void settingsAreClampedToSensibleRanges() {
        Scene scene = new Scene("market");

        scene.setChancePercent(0);
        scene.setAreaRadius(100_000);
        scene.setCooldownSeconds(-5);
        scene.setDurationSeconds(1);

        assertEquals(1, scene.getChancePercent());
        assertEquals(Scene.MAX_RADIUS, scene.getAreaRadius());
        assertEquals(0, scene.getCooldownSeconds());
        assertEquals(10, scene.getDurationSeconds());
    }

    @Test
    void reactionsAreLimitedToEventsAScenePlayerCanReactTo() {
        SceneRole role = new SceneRole("wago", "wago");
        role.setReaction(BehaviourEvent.PLAYER_APPROACH, List.of(new BehaviourAction(BehaviourActionType.WAVE, null)));

        assertEquals(1, role.getReaction(BehaviourEvent.PLAYER_APPROACH).size());
        assertThrows(IllegalArgumentException.class, () -> role.setReaction(BehaviourEvent.DEATH,
                List.of(new BehaviourAction(BehaviourActionType.WAVE, null))));
        role.setReaction(BehaviourEvent.PLAYER_APPROACH, List.of());
        assertTrue(role.getReactions().isEmpty());
    }

    @Test
    void beatRowsHoldAtMostSevenActions() {
        SceneBeat beat = new SceneBeat();
        List<BehaviourAction> actions = java.util.Collections.nCopies(9,
                new BehaviourAction(BehaviourActionType.WAVE, null));

        beat.setActions("watcher", actions);

        assertEquals(SceneRole.MAX_ACTIONS, beat.getActions("watcher").size());
        beat.setActions("watcher", List.of());
        assertFalse(beat.hasActions("watcher"));
    }

    @Test
    void aSceneAlwaysHasAtLeastOneBeat() {
        Scene scene = new Scene("train");
        SceneBeat only = scene.getBeats().getFirst();

        assertTrue(scene.removeBeat(only.getId()));
        assertEquals(1, scene.getBeats().size());
        scene.setBeats(List.of());
        assertEquals(1, scene.getBeats().size());
    }

    @Test
    void beatsCanBeMovedDuplicatedAndRemovedByTheirId() {
        Scene scene = new Scene("train");
        SceneBeat first = scene.getBeats().getFirst();
        first.setActions("watcher", List.of(new BehaviourAction(BehaviourActionType.WAVE, null)));
        SceneBeat second = scene.addBeat();

        assertTrue(scene.moveBeat(second.getId(), -1));
        assertEquals(List.of(second, first), scene.getBeats());
        assertFalse(scene.moveBeat(second.getId(), -1));

        SceneBeat copy = scene.duplicateBeat(first.getId()).orElseThrow();
        assertEquals(2, scene.beatIndex(copy.getId()));
        assertFalse(copy.getId().equals(first.getId()));
        assertEquals(first.getActions("watcher"), copy.getActions("watcher"));

        assertTrue(scene.removeBeat(second.getId()));
        assertEquals(List.of(first, copy), scene.getBeats());
        assertEquals(-1, scene.beatIndex(second.getId()));
    }

    @Test
    void removingARoleRemovesItsBeatActionsAndTheRuleWaitingForIt() {
        Scene scene = new Scene("train");
        SceneRole luigi = scene.addRole("luigi");
        SceneBeat beat = scene.getBeats().getFirst();
        beat.setActions(luigi.getId(), List.of(new BehaviourAction(BehaviourActionType.WAVE, null)));
        beat.setAdvance(SceneBeatAdvance.ROLE_DONE);
        beat.setAdvanceRoleId(luigi.getId());

        assertTrue(scene.removeRole(luigi.getId()));

        assertFalse(beat.hasActions("luigi"));
        assertEquals(SceneBeatAdvance.EVERYONE_DONE, beat.getAdvance());
        assertEquals(null, beat.getAdvanceRoleId());
    }

    @Test
    void actionCleanupLooksInsideBeatsAndQuestionBranches() {
        Scene scene = new Scene("train");
        SceneRole watcher = scene.addRole("watcher");
        BehaviourAction route = new BehaviourAction(BehaviourActionType.SET_ROUTE, "station");
        NpcQuestion question = new NpcQuestion(UUID.randomUUID(), "Ready?", List.of(
                new QuestionOption("Yes", List.of(route, new BehaviourAction(BehaviourActionType.NEXT_BEAT, null)))),
                List.of());
        scene.getBeats().getFirst().setActions(watcher.getId(), List.of(BehaviourAction.ask(question)));
        SceneRole luigi = scene.addRole("luigi");
        scene.addBeat().setActions(luigi.getId(), List.of(route));
        luigi.setWrapUp(List.of(route));

        assertTrue(scene.removeActions(action -> action.type() == BehaviourActionType.SET_ROUTE));
        List<BehaviourActionType> remaining = new java.util.ArrayList<>();
        scene.forEachAction(action -> remaining.add(action.type()));
        assertEquals(List.of(BehaviourActionType.NEXT_BEAT), remaining);
        assertFalse(scene.getBeats().get(1).hasActions("luigi"));
        assertFalse(scene.removeActions(action -> action.type() == BehaviourActionType.SET_ROUTE));
    }

    @Test
    void startedScenesListTheChainWithoutTheSceneItself() {
        Scene scene = new Scene("western/train");
        SceneRole watcher = scene.addRole("watcher");
        scene.getBeats().getFirst().setActions(watcher.getId(),
                List.of(new BehaviourAction(BehaviourActionType.START_SCENE, "Western/Saloon"),
                        new BehaviourAction(BehaviourActionType.START_SCENE, "western/train")));
        watcher.setWrapUp(List.of(new BehaviourAction(BehaviourActionType.START_SCENE, "western/saloon"),
                new BehaviourAction(BehaviourActionType.START_SCENE, "western/night")));

        assertEquals(List.of("western/saloon", "western/night"), scene.startedScenes());
    }

    @Test
    void triggersAreStoredByTheirStableNames() {
        assertEquals("player-enters", SceneTrigger.PLAYER_ENTERS.storedName());
        assertEquals(SceneTrigger.PLAYER_ENTERS, SceneTrigger.fromStored("player-enters").orElseThrow());
        assertEquals(SceneTrigger.NOON, SceneTrigger.forPeriod(DayPeriod.NOON));
        assertTrue(SceneTrigger.fromStored("meteor-shower").isEmpty());
        assertEquals(SceneEnding.BEATS_FINISHED, SceneEnding.fromStored("nonsense"));
        assertEquals(SceneEnding.BEATS_FINISHED, SceneEnding.fromStored("lanes-finished"));
        assertEquals(SceneBeatAdvance.NEXT_BEAT_ONLY, SceneBeatAdvance.fromStored("next-beat-only"));
        assertEquals(SceneEnding.WEATHER_CHANGES, SceneEnding.fromStored("weather-changes"));
    }

    @Test
    void oldEmitCustomEventActionsLoadAsStartScene() {
        assertEquals(BehaviourActionType.START_SCENE, BehaviourActionType.fromStored("emit_event"));
    }
}
