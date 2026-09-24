package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.BehaviourRow;
import dev.blockfolk.model.CustomBehaviourRow;

class BehaviourRowCodecTest {

    @Test
    void selectedRowsSurviveYamlWithOrderAndEmptyRows() throws Exception {
        List<BehaviourRow> rows = List.of(
                new BehaviourRow(BehaviourEvent.RIGHT_CLICK,
                        List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "First"))),
                new BehaviourRow(BehaviourEvent.SPAWN, List.of()), new BehaviourRow(BehaviourEvent.RIGHT_CLICK,
                        List.of(new BehaviourAction(BehaviourActionType.WAVE, null))));
        YamlConfiguration saved = new YamlConfiguration();
        saved.set("behaviour-rows", BehaviourRowCodec.encode(rows));
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(saved.saveToString());

        assertEquals(rows, BehaviourRowCodec.decode(loaded.getList("behaviour-rows"), message -> {
        }));
    }

    @Test
    void oversizedStoredRowSplitsWithoutChangingActionOrder() {
        List<Map<String, Object>> actions = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> Map.<String, Object>of("type", "send_dialog", "value", "Line " + index)).toList();

        List<BehaviourRow> rows = BehaviourRowCodec.decode(List.of(Map.of("event", "spawn", "actions", actions)),
                message -> {
                });

        assertEquals(2, rows.size());
        assertEquals(7, rows.getFirst().actions().size());
        assertEquals("Line 7", rows.getLast().actions().getFirst().value());
    }

    @Test
    void customEventRowsSurviveYamlWithDuplicatesAndEmptyRows() throws Exception {
        List<CustomBehaviourRow> rows = List.of(
                new CustomBehaviourRow("town/alarm",
                        List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "First"))),
                new CustomBehaviourRow("town/quiet", List.of()),
                new CustomBehaviourRow("town/alarm", List.of(new BehaviourAction(BehaviourActionType.WAVE, null))));
        YamlConfiguration saved = new YamlConfiguration();
        saved.set("custom-event-rows", BehaviourRowCodec.encodeCustom(rows));
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(saved.saveToString());

        assertEquals(rows, BehaviourRowCodec.decodeCustom(loaded.getList("custom-event-rows"), message -> {
        }));
    }
}
