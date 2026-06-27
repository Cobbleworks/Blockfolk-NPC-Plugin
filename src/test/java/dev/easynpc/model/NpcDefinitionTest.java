package dev.easynpc.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class NpcDefinitionTest {
    @Test
    void createsStableStorageKeyFromDisplayName() {
        assertEquals("royal-guard-01", NpcDefinition.toKey("Royal Guard 01!"));
        assertEquals("npc", NpcDefinition.toKey("!!!"));
    }

    @Test
    void clampsDialogTimingToAtLeastOneSecond() {
        NpcDefinition definition = NpcDefinition.create("Guard");

        definition.setSecondsPerDialogLine(-4);

        assertEquals(1, definition.getSecondsPerDialogLine());
    }

    @Test
    void returnsCopyOfDialogLines() {
        NpcDefinition definition = NpcDefinition.create("Guard");
        definition.setDialogLines(List.of("Hello"));

        List<String> copy = definition.getDialogLines();

        assertEquals(List.of("Hello"), copy);
        assertNotSame(copy, definition.getDialogLines());
    }
}
