package dev.blockfolk.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Recovers a missing intended speaker without discarding other NPCs' valid
 * calls.
 */
final class AiGroupResponseCoordinator {

    private AiGroupResponseCoordinator() {
    }

    static CompletableFuture<AiParseResult<Map<String, AiDecision>>> complete(
            BiFunction<String, String, CompletableFuture<String>> groupCompletion,
            BiFunction<String, String, CompletableFuture<String>> primaryCompletion, String system, String context,
            Function<String, AiParseResult<Map<String, AiDecision>>> parser, String primaryResponseId,
            String description, Consumer<String> warning) {
        return AiResponseCoordinator.completeValidated(groupCompletion, system, context, parser, description, warning)
                .thenCompose(first -> {
                    if (!first.usable() || first.value().containsKey(primaryResponseId))
                        return CompletableFuture.completedFuture(first);
                    String correction = context
                            + "\n\nThe intended speaker was omitted. Call a function for only Response ID "
                            + primaryResponseId + ". If this NPC should remain silent, call do_nothing for that ID.";
                    return primaryCompletion.apply(system, correction).handle((content, error) -> {
                        if (error != null) {
                            warning.accept(description + " could not recover the intended speaker; valid actions from "
                                    + "other NPCs will still be applied (" + error.getMessage() + ").");
                            return first;
                        }
                        AiParseResult<Map<String, AiDecision>> retry = parser.apply(content);
                        AiDecision primary = retry.value().get(primaryResponseId);
                        if (!retry.usable() || primary == null) {
                            warning.accept(description + " still omitted the intended speaker; valid actions from "
                                    + "other NPCs will still be applied.");
                            return first;
                        }
                        Map<String, AiDecision> merged = new LinkedHashMap<>();
                        merged.put(primaryResponseId, primary);
                        first.value().forEach(merged::putIfAbsent);
                        return new AiParseResult<>(Collections.unmodifiableMap(merged), true, first.issue());
                    });
                });
    }
}
