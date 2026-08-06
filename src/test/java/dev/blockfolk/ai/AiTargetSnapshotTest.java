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
        AiTargetSnapshot snapshot = new AiTargetSnapshot(Map.of(), Map.of(),
                Map.of("nearby_location_1", original));
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
        AiTargetSnapshot snapshot = new AiTargetSnapshot(
                Map.of("nearby_player_1", entityId),
                Map.of("nearby_npc_1", npcId), Map.of());

        assertEquals(entityId, snapshot.entityId("nearby_player_1").orElseThrow());
        assertEquals(npcId, snapshot.npcInstanceId("nearby_npc_1").orElseThrow());
    }
}
