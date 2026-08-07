package dev.blockfolk.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
        assertTrue(definition.isPushable());
        assertEquals(NpcColor.ORANGE, definition.getColor());
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

    @Test
    void findsRoutesReferencedByMovementEventsCustomEventsAndQuestions() {
        NpcDefinition definition = NpcDefinition.create("Guard");
        definition.setMovementProfile(MovementProfile.routing("Day Patrol", WalkingSpeed.NORMAL));
        definition.setBehaviourActions(BehaviourEvent.SPAWN,
                List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "Village/Night Patrol")));
        definition.setCustomEventActions("alarm",
                List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "Emergency")));
        definition.setBehaviourActions(BehaviourEvent.RIGHT_CLICK,
                List.of(BehaviourAction.ask(new NpcQuestion(UUID.randomUUID(), "Choose",
                        List.of(new QuestionOption("Market",
                                List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "Market Loop")))),
                        List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "Return Home"))))));

        assertEquals(java.util.Set.of("day-patrol", "village/night-patrol", "emergency", "market-loop", "return-home"),
                definition.getReferencedRouteKeys());
    }

    @Test
    void longTermMemoryDiscardsTheOldestFactAtCapacity() {
        NpcDefinition definition = NpcDefinition.create("Guard");
        for (int index = 0; index <= NpcDefinition.MAX_AI_MEMORIES; index++) {
            definition.addAiMemory("fact " + index);
        }

        assertEquals(NpcDefinition.MAX_AI_MEMORIES, definition.getAiMemories().size());
        assertEquals("fact 1", definition.getAiMemories().getFirst());
        assertEquals("fact " + NpcDefinition.MAX_AI_MEMORIES, definition.getAiMemories().getLast());
    }

    @Test
    void removesDeletedRouteReferencesIncludingQuestionBranches() {
        NpcDefinition definition = NpcDefinition.create("Guard");
        definition.setMovementProfile(MovementProfile.routing("patrol", WalkingSpeed.FAST));
        definition.setBehaviourActions(BehaviourEvent.SPAWN,
                List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "patrol"),
                        new BehaviourAction(BehaviourActionType.WAVE, null),
                        BehaviourAction.ask(new NpcQuestion(UUID.randomUUID(), "Choose",
                                List.of(new QuestionOption("Go",
                                        List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "patrol")))),
                                List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "other"))))));

        assertTrue(definition.removeRouteReferences("patrol"));

        assertEquals(java.util.Set.of("other"), definition.getReferencedRouteKeys());
        assertFalse(definition.getMovementProfile().enabled());
        assertEquals(2, definition.getBehaviourActions(BehaviourEvent.SPAWN).size());
    }

    @Test
    void copyPreservesEveryVisibleProperty() {
        NpcDefinition source = NpcDefinition.create("Guard");
        source.setShowName(false);
        source.setLookAtPlayer(false);
        source.setItemPickup(true);
        source.setPushable(false);
        source.setColor(NpcColor.BLUE);
        source.setBehaviourActions(BehaviourEvent.RIGHT_CLICK,
                List.of(new BehaviourAction(BehaviourActionType.WAVE, null)));

        NpcDefinition copy = source.copyAs("Guard Copy");

        assertFalse(copy.isShowName());
        assertFalse(copy.isLookAtPlayer());
        assertTrue(copy.isItemPickup());
        assertFalse(copy.isPushable());
        assertEquals(NpcColor.BLUE, copy.getColor());
        assertEquals(source.getBehaviourActions(BehaviourEvent.RIGHT_CLICK),
                copy.getBehaviourActions(BehaviourEvent.RIGHT_CLICK));
    }
}
