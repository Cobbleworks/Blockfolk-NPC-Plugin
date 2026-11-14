package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class NpcPerceptionTest {

    @Test
    void closeEntitiesAreNoticedWithoutSightAndWithoutATrace() {
        AtomicBoolean traced = new AtomicBoolean();
        assertTrue(NpcPerception.perceives(3.0 * 3.0, 4.0, () -> {
            traced.set(true);
            return false;
        }));
        assertFalse(traced.get());
    }

    @Test
    void entitiesBeyondTheAwarenessRangeNeedLineOfSight() {
        assertFalse(NpcPerception.perceives(10.0 * 10.0, 4.0, () -> false));
        assertTrue(NpcPerception.perceives(10.0 * 10.0, 4.0, () -> true));
    }
}
