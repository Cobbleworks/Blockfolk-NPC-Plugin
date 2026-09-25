package dev.blockfolk.ai;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** Retries a response once only when its parsed content is unusable. */
final class AiResponseCoordinator {

    private AiResponseCoordinator() {
    }

    static <T> CompletableFuture<AiParseResult<T>> completeValidated(
            BiFunction<String, String, CompletableFuture<String>> completion, String system, String context,
            Function<String, AiParseResult<T>> parser, String description, Consumer<String> warning) {
        return completion.apply(system, context).thenCompose(content -> {
            AiParseResult<T> first = parser.apply(content);
            if (first.usable()) {
                if (!first.issue().isEmpty())
                    warning.accept(description + ": " + first.issue());
                return CompletableFuture.completedFuture(first);
            }
            warning.accept(description + " returned unusable output (" + first.issue() + "); retrying once.");
            String correction = context + "\n\nThe preceding attempt returned unusable output (" + first.issue()
                    + "). Return the requested format with valid actions and aliases.";
            return completion.apply(system, correction).thenApply(retryContent -> {
                AiParseResult<T> retry = parser.apply(retryContent);
                if (!retry.usable())
                    warning.accept(description + " returned unusable output again (" + retry.issue()
                            + "); no actions were applied.");
                else if (!retry.issue().isEmpty())
                    warning.accept(description + ": " + retry.issue());
                return retry;
            });
        });
    }
}
