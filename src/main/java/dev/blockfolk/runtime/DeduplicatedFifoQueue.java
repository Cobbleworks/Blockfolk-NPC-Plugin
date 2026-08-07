package dev.blockfolk.runtime;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Keeps keys reserved after polling until the caller explicitly completes them.
 */
final class DeduplicatedFifoQueue<K, V> {
    private final ArrayDeque<V> queue = new ArrayDeque<>();
    private final Set<K> reserved = new HashSet<>();
    private final Function<V, K> keyExtractor;

    DeduplicatedFifoQueue(Function<V, K> keyExtractor) {
        this.keyExtractor = keyExtractor;
    }

    boolean offer(V value) {
        if (!reserved.add(keyExtractor.apply(value)))
            return false;
        queue.addLast(value);
        return true;
    }

    V poll() {
        return queue.pollFirst();
    }

    void complete(V value) {
        reserved.remove(keyExtractor.apply(value));
    }

    void removeIf(Predicate<V> predicate) {
        Iterator<V> iterator = queue.iterator();
        while (iterator.hasNext()) {
            V value = iterator.next();
            if (!predicate.test(value))
                continue;
            iterator.remove();
            complete(value);
        }
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }
}
