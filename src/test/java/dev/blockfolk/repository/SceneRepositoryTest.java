package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.Scene;
import dev.blockfolk.model.SceneBeat;
import dev.blockfolk.model.SceneBeatAdvance;
import dev.blockfolk.model.SceneEnding;
import dev.blockfolk.model.SceneRole;
import dev.blockfolk.model.SceneTrigger;

class SceneRepositoryTest {

    @TempDir
    File dataFolder;

    @Test
    void scenesRoundTripThroughYaml() throws Exception {
        Scene scene = Scene.create("jungle/Market by the Lake");
        scene.setDescription("The traders meet at the lake.");
        scene.setEnabled(false);
        scene.setTriggersAiBehaviour(false);
        scene.setAreaLocationKey("jungle/lake");
        scene.setAreaRadius(40);
        scene.setTrigger(SceneTrigger.NOON, true);
        scene.setTrigger(SceneTrigger.PLAYER_ENTERS, true);
        scene.setChancePercent(50);
        scene.setCooldownSeconds(900);
        scene.setEnding(SceneEnding.TIME_OF_DAY);
        scene.setEndPeriod(DayPeriod.EVENING);
        SceneRole wago = scene.addRole("wago");
        SceneRole smith = scene.addRole("smith");
        SceneBeat arrive = scene.getBeats().getFirst();
        arrive.setActions(wago.getId(), List.of(new BehaviourAction(BehaviourActionType.WAIT, "120.0"),
                new BehaviourAction(BehaviourActionType.MOVE_TO, "location:jungle/lake")));
        arrive.setAdvance(SceneBeatAdvance.ROLE_DONE);
        arrive.setAdvanceRoleId(wago.getId());
        SceneBeat greet = scene.addBeat();
        greet.setActions(smith.getId(), List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Welcome!")));
        greet.setAdvance(SceneBeatAdvance.AFTER_TIME);
        greet.setSeconds(45);
        wago.setWrapUp(List.of(new BehaviourAction(BehaviourActionType.GO_BACK, null)));
        wago.setReaction(BehaviourEvent.PLAYER_APPROACH,
                List.of(new BehaviourAction(BehaviourActionType.OPEN_SHOP, "wago")));

        YamlConfiguration written = new YamlConfiguration();
        SceneRepository.write(written.createSection("scene"), scene);
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(written.saveToString());
        Scene read = SceneRepository.read(reloaded.getConfigurationSection("scene"));

        assertEquals("jungle/market-by-the-lake", read.getKey());
        assertEquals("Market by the Lake", read.getDisplayName());
        assertEquals("The traders meet at the lake.", read.getDescription());
        assertFalse(read.isEnabled());
        assertFalse(read.triggersAiBehaviour());
        assertEquals("jungle/lake", read.getAreaLocationKey());
        assertEquals(40, read.getAreaRadius());
        assertEquals(Set.of(SceneTrigger.NOON, SceneTrigger.PLAYER_ENTERS), read.getTriggers());
        assertEquals(50, read.getChancePercent());
        assertEquals(900, read.getCooldownSeconds());
        assertEquals(SceneEnding.TIME_OF_DAY, read.getEnding());
        assertEquals(DayPeriod.EVENING, read.getEndPeriod());
        SceneRole role = read.findRole(wago.getId()).orElseThrow();
        assertEquals("wago", role.getPresetKey());
        assertEquals(wago.getWrapUp(), role.getWrapUp());
        assertEquals(wago.getReactions(), role.getReactions());
        List<SceneBeat> beats = read.getBeats();
        assertEquals(2, beats.size());
        assertEquals(arrive.getId(), beats.get(0).getId());
        assertEquals(arrive.getActions("wago"), beats.get(0).getActions("wago"));
        assertTrue(beats.get(0).getActions("smith").isEmpty());
        assertEquals(SceneBeatAdvance.ROLE_DONE, beats.get(0).getAdvance());
        assertEquals("wago", beats.get(0).getAdvanceRoleId());
        assertEquals(greet.getActions("smith"), beats.get(1).getActions("smith"));
        assertEquals(SceneBeatAdvance.AFTER_TIME, beats.get(1).getAdvance());
        assertEquals(45, beats.get(1).getSeconds());
    }

    @Test
    void oldRoleLanesBecomeTheFirstBeatAndCueActionsAreDropped() throws Exception {
        YamlConfiguration stored = new YamlConfiguration();
        stored.loadFromString("""
                scene:
                  key: western/train
                  end:
                    mode: lanes-finished
                  roles:
                  - id: watcher
                    preset: watcher
                    lane:
                    - type: send_dialog
                      value: Late again!
                    - type: cue
                      value: told
                  - id: luigi
                    preset: luigi
                    lane:
                    - type: wait_for_cue
                      value: told
                    - type: wave
                """);

        Scene read = SceneRepository.read(stored.getConfigurationSection("scene"));

        assertEquals(SceneEnding.BEATS_FINISHED, read.getEnding());
        assertEquals(1, read.getBeats().size());
        SceneBeat beat = read.getBeats().getFirst();
        assertEquals(List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Late again!")),
                beat.getActions("watcher"));
        assertEquals(List.of(new BehaviourAction(BehaviourActionType.WAVE, null)), beat.getActions("luigi"));
    }

    @Test
    void customEventsBecomeScenesWithEveryReactingPresetAsAnAllInstancesRole() throws Exception {
        SceneRepository repository = new SceneRepository(plugin());
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.loadFromString("""
                order: [Western/TrainIsLate, unused]
                events:
                  '0':
                    name: Western/TrainIsLate
                    description: The evening train is overdue.
                  '1':
                    name: unused
                """);
        NpcDefinition watcher = NpcDefinition.create("Station Watcher");
        NpcDefinition luigi = NpcDefinition.create("Luigi");
        List<BehaviourAction> watcherRow = List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Late!"),
                new BehaviourAction(BehaviourActionType.START_SCENE, "Western/Saloon"));
        List<BehaviourAction> luigiRow = List.of(new BehaviourAction(BehaviourActionType.WAVE, null));

        int created = repository.migrate(legacy, List.of(watcher, luigi),
                Map.of(watcher.getKey(), Map.of("Western/TrainIsLate", watcherRow), luigi.getKey(),
                        Map.of("Western/TrainIsLate", luigiRow, "Western/Saloon", luigiRow)));

        assertEquals(3, created);
        Scene train = repository.find("western/trainislate").orElseThrow();
        assertEquals("TrainIsLate", train.getDisplayName());
        assertEquals("The evening train is overdue.", train.getDescription());
        assertTrue(train.getTriggers().isEmpty());
        assertEquals(0, train.getCooldownSeconds());
        assertEquals(List.of("station-watcher", "luigi"), train.getRoles().stream().map(SceneRole::getId).toList());
        assertEquals(watcherRow, train.getBeats().getFirst().getActions("station-watcher"));
        assertEquals("luigi", train.findRole("luigi").orElseThrow().getPresetKey());
        assertTrue(repository.find("unused").orElseThrow().getRoles().isEmpty());
        // The old emit action names the event; the scene key it starts must match.
        assertEquals(repository.find("Western/Saloon").orElseThrow().getKey(),
                Scene.normalizeKey(watcherRow.get(1).value()));
    }

    @Test
    void migrationLeavesExistingScenesInPlace() {
        SceneRepository repository = new SceneRepository(plugin());
        NpcDefinition guard = NpcDefinition.create("Guard");
        List<BehaviourAction> row = List.of(new BehaviourAction(BehaviourActionType.WAVE, null));

        assertEquals(1, repository.migrate(null, List.of(guard), Map.of(guard.getKey(), Map.of("alarm", row))));
        assertEquals(0, repository.migrate(null, List.of(guard), Map.of(guard.getKey(), Map.of("alarm", row))));
        assertEquals(1, repository.find("alarm").orElseThrow().getRoles().size());
    }

    @Test
    void oldEmitActionsDecodeAsStartScene() {
        BehaviourAction action = BehaviourActionCodec.decode(Map.of("type", "emit_event", "value", "alarm"));

        assertEquals(new BehaviourAction(BehaviourActionType.START_SCENE, "alarm"), action);
    }

    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SceneRepositoryTest"));
        return plugin;
    }
}
