package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AiResponseCoordinatorTest {

    @Test
    void retriesUnusableJsonOnceAndAddsCorrection() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> contexts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AiParseResult<AiDecision> result = AiResponseCoordinator.completeValidated((system, context) -> {
            contexts.add(context);
            return CompletableFuture.completedFuture(
                    attempts.getAndIncrement() == 0 ? "bad" : "{\"actions\":[{\"type\":\"SAY\",\"text\":\"Hello\"}]}");
        }, "rules", "Player said hello",
                content -> AiDecisionParser.parseDetailed(content, AiControlSettings.defaults()), "chat", warnings::add)
                .join();

        assertTrue(result.usable());
        assertEquals(2, attempts.get());
        assertTrue(contexts.get(1).contains("unusable output"));
        assertEquals(1, warnings.size());
    }

    @Test
    void stopsAfterSecondUnusableResponse() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> warnings = new ArrayList<>();
        AiParseResult<AiDecision> result = AiResponseCoordinator.completeValidated((system, context) -> {
            attempts.incrementAndGet();
            return CompletableFuture.completedFuture("bad");
        }, "rules", "chat", content -> AiDecisionParser.parseDetailed(content, AiControlSettings.defaults()), "chat",
                warnings::add).join();

        assertFalse(result.usable());
        assertEquals(2, attempts.get());
        assertEquals(2, warnings.size());
    }
}
