package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeduplicatedFifoQueueTest {

    @Test
    void preservesFifoAndDeduplicatesQueuedAndPolledEntries() {
        DeduplicatedFifoQueue<String, Entry> queue = new DeduplicatedFifoQueue<>(Entry::key);
        Entry first = new Entry("npc-a/question", 1);
        Entry second = new Entry("npc-b/question", 2);

        assertTrue(queue.offer(first));
        assertTrue(queue.offer(second));
        assertFalse(queue.offer(new Entry(first.key(), 3)));
        assertEquals(first, queue.poll());
        assertFalse(queue.offer(new Entry(first.key(), 4)), "polled keys remain reserved while resolving");
        queue.complete(first);
        assertTrue(queue.offer(new Entry(first.key(), 5)));
        assertEquals(second, queue.poll());
    }

    @Test
    void removingQueuedEntriesReleasesTheirKeys() {
        DeduplicatedFifoQueue<String, Entry> queue = new DeduplicatedFifoQueue<>(Entry::key);
        Entry removed = new Entry("gone", 1);
        queue.offer(removed);
        queue.removeIf(entry -> entry.key().equals("gone"));

        assertNull(queue.poll());
        assertTrue(queue.offer(new Entry("gone", 2)));
    }

    private record Entry(String key, int value) { }
}
