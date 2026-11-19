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
    void rowsHoldAtMostSevenActions() {
        SceneRole role = new SceneRole("watcher", "watcher");
        List<BehaviourAction> actions = java.util.Collections.nCopies(9,
                new BehaviourAction(BehaviourActionType.WAVE, null));

        role.setLane(actions);

        assertEquals(SceneRole.MAX_ACTIONS, role.getLane().size());
    }

    @Test
    void cueNamesAndRouteCleanupLookInsideQuestionBranches() {
        Scene scene = new Scene("train");
        SceneRole watcher = scene.addRole("watcher");
        BehaviourAction route = new BehaviourAction(BehaviourActionType.SET_ROUTE, "station");
        NpcQuestion question = new NpcQuestion(UUID.randomUUID(), "Ready?", List
                .of(new QuestionOption("Yes", List.of(route, new BehaviourAction(BehaviourActionType.CUE, "told")))),
                List.of());
        watcher.setLane(List.of(BehaviourAction.ask(question)));
        SceneRole luigi = scene.addRole("luigi");
        luigi.setLane(List.of(new BehaviourAction(BehaviourActionType.WAIT_FOR_CUE, "told"), route));

        assertEquals(List.of("told"), scene.cueNames());
        assertTrue(scene.removeActions(action -> action.type() == BehaviourActionType.SET_ROUTE));
        List<BehaviourActionType> remaining = new java.util.ArrayList<>();
        scene.forEachAction(action -> remaining.add(action.type()));
        assertEquals(List.of(BehaviourActionType.CUE, BehaviourActionType.WAIT_FOR_CUE), remaining);
        assertFalse(scene.removeActions(action -> action.type() == BehaviourActionType.SET_ROUTE));
    }

    @Test
    void startedScenesListTheChainWithoutTheSceneItself() {
        Scene scene = new Scene("western/train");
        SceneRole watcher = scene.addRole("watcher");
        watcher.setLane(List.of(new BehaviourAction(BehaviourActionType.START_SCENE, "Western/Saloon"),
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
        assertEquals(SceneEnding.LANES_FINISHED, SceneEnding.fromStored("nonsense"));
        assertEquals(SceneEnding.WEATHER_CHANGES, SceneEnding.fromStored("weather-changes"));
    }

    @Test
    void oldEmitCustomEventActionsLoadAsStartScene() {
        assertEquals(BehaviourActionType.START_SCENE, BehaviourActionType.fromStored("emit_event"));
    }
}
