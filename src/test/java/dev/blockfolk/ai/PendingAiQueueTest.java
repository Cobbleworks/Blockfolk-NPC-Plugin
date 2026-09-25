package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PendingAiQueueTest {

    @Test
    void keepsRapidTurnsInOrderAndBoundsTheBacklog() {
        PendingAiQueue<String> queue = new PendingAiQueue<>(2);
        assertTrue(queue.offer("first"));
        assertTrue(queue.offer("second"));
        assertFalse(queue.offer("third"));
        assertEquals("first", queue.poll());
        assertEquals("second", queue.poll());
        assertTrue(queue.isEmpty());
    }
}
