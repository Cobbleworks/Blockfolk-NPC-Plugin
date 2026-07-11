package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void changingSkinUrlClearsPreviouslyResolvedTexture() {
        NpcDefinition definition = NpcDefinition.create("Guard");
        definition.setResolvedSkin("https://textures.minecraft.net/texture/hash", "value", "signature");

        definition.setSkinUrl("https://example.test/new-skin.png");

        assertEquals("https://example.test/new-skin.png", definition.getSkinUrl());
        assertNull(definition.getSkinTextureValue());
        assertNull(definition.getSkinTextureSignature());
    }
}
