package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class AiTargetSnapshotTest {

    @Test
    void locationsAreDefensivelyCopied() {
        Location original = new Location(null, 1, 64, 2);
        AiTargetSnapshot snapshot = new AiTargetSnapshot(Map.of(), Map.of(), Map.of("nearby_location_1", original));
        original.setX(100);

        Location resolved = snapshot.location("nearby_location_1").orElseThrow();
        assertEquals(1, resolved.getX());
        resolved.setX(200);

        assertEquals(1, snapshot.location("nearby_location_1").orElseThrow().getX());
        assertTrue(snapshot.locations().containsKey("nearby_location_1"));
    }

    @Test
    void stableIdentityBindingsAreAvailableByAlias() {
        UUID entityId = UUID.randomUUID();
        UUID npcId = UUID.randomUUID();
        AiTargetSnapshot snapshot = new AiTargetSnapshot(Map.of("nearby_player_1", entityId),
                Map.of("nearby_npc_1", npcId), Map.of());

        assertEquals(entityId, snapshot.entityId("nearby_player_1").orElseThrow());
        assertEquals(npcId, snapshot.npcInstanceId("nearby_npc_1").orElseThrow());
    }

    @Test
    void looseNamesResolveToCanonicalAliases() {
        AiTargetSnapshot.Builder builder = AiTargetSnapshot.builder();
        builder.bindLocation("nearby_location_1", new Location(null, 10, 64, 10));
        builder.synonym("town/market", "nearby_location_1");
        builder.synonym("market", "nearby_location_1");
        builder.bindLocation("nearby_location_2", new Location(null, 20, 64, 20));
        builder.synonym("Docks/Stall", "nearby_location_2");
        builder.synonym("stall", "nearby_location_2");
        builder.bindLocation("nearby_location_3", new Location(null, 30, 64, 30));
        builder.synonym("stall", "nearby_location_3");
        AiTargetSnapshot snapshot = builder.build();

        assertEquals("nearby_location_1", snapshot.canonical("Market"));
        assertEquals("nearby_location_1", snapshot.canonical("\"Town/Market\""));
        assertEquals("nearby_location_1", snapshot.canonical("location_1"));
        assertEquals("nearby_location_2", snapshot.canonical("nearby_location_2: Docks/Stall"));
        // Two locations named "stall" make that synonym ambiguous, so it is not
        // guessed.
        assertEquals("stall", snapshot.canonical("stall"));
    }

    @Test
    void reservedAliasesCannotBeShadowedBySynonyms() {
        AiTargetSnapshot.Builder builder = AiTargetSnapshot.builder();
        builder.bindLocation("nearby_location_1", new Location(null, 1, 64, 1));
        builder.synonym("nearest_player", "nearby_location_1");

        assertEquals("nearest_player", builder.build().canonical("nearest_player"));
    }

    @Test
    void coordinatesAreNormalizedButNeedAnOriginToResolve() {
        AiTargetSnapshot snapshot = AiTargetSnapshot.builder().build();

        assertEquals("100,64,-20", snapshot.canonical("(100, 64, -20)"));
        assertEquals("100,64,-20", snapshot.canonical("100 64 -20"));
        assertTrue(snapshot.location("100,64,-20").isEmpty());
    }
}
