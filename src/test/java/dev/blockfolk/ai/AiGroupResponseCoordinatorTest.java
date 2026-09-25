package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AiGroupResponseCoordinatorTest {

    private static final Map<String, AiControlSettings> PARTICIPANTS = Map.of("npc_primary",
            AiControlSettings.defaults(), "npc_other", AiControlSettings.defaults());

    @Test
    void recoversMissingPrimaryWithoutDiscardingOtherNpc() {
        AtomicInteger groupCalls = new AtomicInteger();
        AtomicInteger primaryCalls = new AtomicInteger();
        var result = AiGroupResponseCoordinator.complete((system, context) -> {
            groupCalls.incrementAndGet();
            return completed("npc_other", "Other reply");
        }, (system, context) -> {
            primaryCalls.incrementAndGet();
            assertTrue(context.contains("only Response ID npc_primary"));
            return completed("npc_primary", "Primary reply");
        }, "rules", "chat", content -> AiGroupDecisionParser.parseDetailed(content, PARTICIPANTS), "npc_primary",
                "group chat", ignored -> {
                }).join();

        assertTrue(result.usable());
        assertEquals(List.of("npc_primary", "npc_other"), List.copyOf(result.value().keySet()));
        assertEquals("Primary reply", result.value().get("npc_primary").actions().getFirst().text());
        assertEquals("Other reply", result.value().get("npc_other").actions().getFirst().text());
        assertEquals(1, groupCalls.get());
        assertEquals(1, primaryCalls.get());
    }

    @Test
    void preservesValidOtherNpcIfPrimaryRecoveryFails() {
        List<String> warnings = new ArrayList<>();
        var result = AiGroupResponseCoordinator.complete((system, context) -> completed("npc_other", "Other reply"),
                (system, context) -> CompletableFuture.completedFuture("{}"), "rules", "chat",
                content -> AiGroupDecisionParser.parseDetailed(content, PARTICIPANTS), "npc_primary", "group chat",
                warnings::add).join();

        assertTrue(result.usable());
        assertEquals(List.of("npc_other"), List.copyOf(result.value().keySet()));
        assertEquals(1, warnings.size());
    }

    @Test
    void skipsRecoveryWhenPrimaryAlreadyResponded() {
        AtomicInteger primaryCalls = new AtomicInteger();
        var result = AiGroupResponseCoordinator
                .complete((system, context) -> completed("npc_primary", "Hello"), (system, context) -> {
                    primaryCalls.incrementAndGet();
                    return completed("npc_primary", "Duplicate");
                }, "rules", "chat", content -> AiGroupDecisionParser.parseDetailed(content, PARTICIPANTS),
                        "npc_primary", "group chat", ignored -> {
                        })
                .join();

        assertTrue(result.usable());
        assertEquals(0, primaryCalls.get());
    }

    private static CompletableFuture<String> completed(String npc, String text) {
        return CompletableFuture.completedFuture("{\"responses\":[{\"npc\":\"" + npc
                + "\",\"actions\":[{\"type\":\"SAY\",\"text\":\"" + text + "\"}]}]}");
    }
}
