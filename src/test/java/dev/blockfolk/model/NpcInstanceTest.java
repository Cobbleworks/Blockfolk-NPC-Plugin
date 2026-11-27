package dev.blockfolk.model;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcInstanceTest {
    @Test
    void storesDefinitionKeySeparatelyFromInstanceId() {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 1.0, 2.0, 3.0));

        assertEquals("guard", instance.getDefinitionKey());
        assertEquals(1.0, instance.getLocation().getX());
        assertNull(instance.getLocation().getWorld());
    }

    @Test
    void returnsClonedLocation() {
        Location location = new Location(null, 1.0, 2.0, 3.0);
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", location);

        assertNotSame(location, instance.getLocation());
    }

    @Test
    void keepsSpawnLocationSeparateFromCurrentLocation() {
        Location spawnLocation = new Location(null, 1.0, 2.0, 3.0);
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", spawnLocation);

        instance.setLocation(new Location(null, 10.0, 20.0, 30.0));

        assertEquals(spawnLocation, instance.getSpawnLocation());
        assertNotSame(spawnLocation, instance.getSpawnLocation());
    }

    @Test
    void canRelocateSpawnLocationIndependently() {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 1.0, 2.0, 3.0));
        Location relocatedSpawn = new Location(null, 4.0, 5.0, 6.0);

        instance.setSpawnLocation(relocatedSpawn);

        assertEquals(relocatedSpawn, instance.getSpawnLocation());
        assertNotSame(relocatedSpawn, instance.getSpawnLocation());
    }

    @Test
    void hasSeparateTemporaryInventory() {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));
        assertEquals(27, instance.getTemporaryInventoryContents().length);
        assertNotSame(instance.getTemporaryInventoryContents(), instance.getTemporaryInventoryContents());
    }

    @Test
    void retainsIdentityAndSpawnWhileAwaitingRespawn() {
        UUID id = UUID.randomUUID();
        NpcInstance instance = new NpcInstance(id, "guard", new StoredLocation("custom_world", 10, 20, 30, 0, 0),
                new StoredLocation("custom_world", 1, 2, 3, 0, 0), 12345L);

        assertEquals(id, instance.getId());
        assertTrue(instance.isAwaitingRespawn());
        assertEquals("custom_world", instance.getStoredSpawnLocation().worldName());
        instance.returnToSpawn();
        assertEquals(instance.getStoredSpawnLocation(), instance.getStoredLocation());
    }

    @Test
    void plainInstanceInheritsEverythingFromThePreset() {
        NpcDefinition preset = new NpcDefinition("guard");
        preset.setResolvedSkin("https://example.com/guard.png", "value", "signature");
        preset.setMovementProfile(MovementProfile.routing("patrol", WalkingSpeed.SLOW));
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));

        assertFalse(instance.hasOverrides());
        assertEquals("https://example.com/guard.png", instance.resolvedSkinUrl(preset));
        assertEquals("value", instance.resolvedSkinTextureValue(preset));
        assertEquals("signature", instance.resolvedSkinTextureSignature(preset));
        assertEquals(preset.getMovementProfile(), instance.defaultMovement(preset));
        assertEquals("patrol", instance.resolveRoute("patrol"));
    }

    @Test
    void variantSkinReplacesThePresetSkinAsAWhole() {
        NpcDefinition preset = new NpcDefinition("guard");
        preset.setResolvedSkin("https://example.com/guard.png", "value", "signature");
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));

        instance.setSkinUrl("https://textures.minecraft.net/texture/abc");

        assertTrue(instance.hasOverrides());
        assertEquals("https://textures.minecraft.net/texture/abc", instance.resolvedSkinUrl(preset));
        // The preset's signed texture belongs to the preset's skin, not the variant's.
        assertNull(instance.resolvedSkinTextureValue(preset));
        assertNull(instance.resolvedSkinTextureSignature(preset));

        instance.clearSkin();
        assertEquals("value", instance.resolvedSkinTextureValue(preset));
    }

    @Test
    void routeSwapRedirectsOnlyTheSwappedRoute() {
        NpcDefinition preset = new NpcDefinition("guard");
        preset.setMovementProfile(MovementProfile.routing("patrol", WalkingSpeed.FAST));
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));

        instance.setRouteSwap("Patrol", "Patrol North");

        assertEquals("patrol-north", instance.resolveRoute("patrol"));
        assertEquals("night-walk", instance.resolveRoute("night-walk"));
        assertEquals(MovementProfile.routing("patrol-north", WalkingSpeed.FAST), instance.defaultMovement(preset));
    }

    @Test
    void swappingARouteForItselfOrNothingRemovesTheSwap() {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));
        instance.setRouteSwap("patrol", "patrol-north");
        instance.setRouteSwap("patrol", "patrol");
        assertTrue(instance.getRouteSwaps().isEmpty());

        instance.setRouteSwap("patrol", "patrol-north");
        instance.setRouteSwap("patrol", null);
        assertTrue(instance.getRouteSwaps().isEmpty());
    }

    @Test
    void deletingARouteDropsSwapsFromAndToIt() {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));
        instance.setRouteSwap("patrol", "patrol-north");
        instance.setRouteSwap("market", "market-east");

        assertTrue(instance.removeRouteReferences("patrol-north"));
        assertEquals(java.util.Map.of("market", "market-east"), instance.getRouteSwaps());
        assertTrue(instance.removeRouteReferences("market"));
        assertFalse(instance.removeRouteReferences("market"));
        assertFalse(instance.hasOverrides());
    }

    @Test
    void disabledPresetMovementStaysDisabledForVariants() {
        NpcDefinition preset = new NpcDefinition("guard");
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));
        instance.setRouteSwap("patrol", "patrol-north");

        assertFalse(instance.defaultMovement(preset).enabled());
    }

    @Test
    void variantBehaviourOverridesReplaceThePresetRowPerEvent() {
        NpcDefinition preset = new NpcDefinition("guard");
        preset.setBehaviourActions(BehaviourEvent.RIGHT_CLICK,
                java.util.List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Jungle hello")));
        preset.setBehaviourActions(BehaviourEvent.SPAWN,
                java.util.List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Spawned")));
        NpcInstance variant = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));

        variant.setBehaviourOverride(BehaviourEvent.RIGHT_CLICK,
                java.util.List.of(new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Village hello")));

        assertEquals("Village hello", variant.behaviourActions(preset, BehaviourEvent.RIGHT_CLICK).getFirst().value());
        assertEquals("Spawned", variant.behaviourActions(preset, BehaviourEvent.SPAWN).getFirst().value());
        assertTrue(variant.hasOverrides());
    }

    @Test
    void anEmptyVariantRowMeansDoNothingAndNullReturnsToThePreset() {
        NpcDefinition preset = new NpcDefinition("guard");
        preset.setBehaviourActions(BehaviourEvent.RIGHT_CLICK,
                java.util.List.of(new BehaviourAction(BehaviourActionType.OPEN_SHOP, "jungle-shop")));
        NpcInstance variant = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));

        variant.setBehaviourOverride(BehaviourEvent.RIGHT_CLICK, java.util.List.of());
        assertTrue(variant.hasBehaviourOverride(BehaviourEvent.RIGHT_CLICK));
        assertTrue(variant.behaviourActions(preset, BehaviourEvent.RIGHT_CLICK).isEmpty());

        variant.setBehaviourOverride(BehaviourEvent.RIGHT_CLICK, null);
        assertFalse(variant.hasBehaviourOverride(BehaviourEvent.RIGHT_CLICK));
        assertEquals(1, variant.behaviourActions(preset, BehaviourEvent.RIGHT_CLICK).size());
        assertFalse(variant.hasOverrides());
    }

    @Test
    void deletingARouteStripsItFromVariantRowsIncludingQuestionBranches() {
        NpcInstance variant = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 0, 0));
        NpcQuestion question = new NpcQuestion(UUID.randomUUID(), "Where to?",
                java.util.List.of(new QuestionOption("Market",
                        java.util.List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "market")))),
                java.util.List.of());
        variant.setBehaviourOverride(BehaviourEvent.RIGHT_CLICK,
                java.util.List.of(new BehaviourAction(BehaviourActionType.SET_ROUTE, "market"),
                        new BehaviourAction(BehaviourActionType.SEND_DIALOG, "Off I go"), BehaviourAction.ask(question)));

        assertTrue(variant.removeRouteReferences("market"));

        java.util.List<BehaviourAction> kept = variant.getBehaviourOverride(BehaviourEvent.RIGHT_CLICK);
        assertEquals(2, kept.size());
        assertEquals(BehaviourActionType.SEND_DIALOG, kept.getFirst().type());
        assertTrue(kept.get(1).question().options().getFirst().actions().isEmpty());
        assertFalse(variant.removeRouteReferences("market"));
    }
}
