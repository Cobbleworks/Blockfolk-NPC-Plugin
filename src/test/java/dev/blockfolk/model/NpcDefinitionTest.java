package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDefinitionTest {
    @Test
    void usesSafePropertyDefaults() {
        NpcDefinition definition = NpcDefinition.create("Guard");

        assertTrue(definition.isShowName());
        assertTrue(definition.isLookAtPlayer());
        assertFalse(definition.isItemPickup());
    }

    @Test
    void createsStableStorageKeyFromDisplayName() {
        assertEquals("royal-guard-01", NpcDefinition.toKey("Royal Guard 01!"));
        assertEquals("npc", NpcDefinition.toKey("!!!"));
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

    @Test
    void storesCustomEventActionsSeparatelyFromBuiltInEvents() {
        NpcDefinition definition = NpcDefinition.create("Guard");
        BehaviourAction action = new BehaviourAction(BehaviourActionType.WAVE, null);

        definition.setCustomEventActions("jungle/idolWasStolen", List.of(action));
        List<BehaviourAction> copy = definition.getCustomEventActions("jungle/idolWasStolen");
        copy.clear();

        assertEquals(List.of(action), definition.getCustomEventActions("jungle/idolWasStolen"));
        assertEquals(1, definition.customEventActionCount());
        assertEquals(List.of(), definition.getBehaviourActions(BehaviourEvent.SPAWN));
    }
}
