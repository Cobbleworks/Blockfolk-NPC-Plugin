package dev.blockfolk.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

/**
 * Main-thread-only, bounded FIFO for interactions waiting on an NPC or player.
 */
final class PendingAiQueue<T> {

    private final int capacity;
    private final Deque<T> entries = new ArrayDeque<>();

    PendingAiQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    boolean offer(T entry) {
        if (entries.size() >= capacity) {
            return false;
        }
        entries.addLast(entry);
        return true;
    }

    T peek() {
        return entries.peekFirst();
    }

    T poll() {
        return entries.pollFirst();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    void removeIf(Predicate<T> predicate) {
        entries.removeIf(predicate);
    }
}
