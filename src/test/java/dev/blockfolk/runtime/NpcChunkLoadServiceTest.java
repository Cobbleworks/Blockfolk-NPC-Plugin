package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NpcChunkLoadServiceTest {

    private static final UUID WORLD = UUID.randomUUID();

    @Test
    void ticketsTheSquareAroundTheNpcChunk() {
        Set<NpcChunkLoadService.ChunkKey> chunks = NpcChunkLoadService.chunksAround(WORLD, -1, 4, 1);

        assertEquals(9, chunks.size());
        assertTrue(chunks.contains(new NpcChunkLoadService.ChunkKey(WORLD, -2, 3)));
        assertTrue(chunks.contains(new NpcChunkLoadService.ChunkKey(WORLD, 0, 5)));
        assertFalse(chunks.contains(new NpcChunkLoadService.ChunkKey(WORLD, 1, 4)));
        assertEquals(Set.of(new NpcChunkLoadService.ChunkKey(WORLD, 7, 7)),
                NpcChunkLoadService.chunksAround(WORLD, 7, 7, 0));
    }

    @Test
    void radiusIsClampedToASafeRange() {
        assertEquals(0, NpcChunkLoadService.clampRadius(-4));
        assertEquals(2, NpcChunkLoadService.clampRadius(2));
        assertEquals(NpcChunkLoadService.MAX_RADIUS, NpcChunkLoadService.clampRadius(50));
    }
}
