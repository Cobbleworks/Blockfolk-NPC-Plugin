package dev.blockfolk.ai;

import java.util.List;

/**
 * A parsed model response plus a safe, non-content diagnostic and the per-call
 * validation outcomes used to give the model precise feedback.
 */
public record AiParseResult<T>(T value, boolean usable, String issue, List<AiCallOutcome> outcomes) {

    public AiParseResult {
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
    }

    public AiParseResult(T value, boolean usable, String issue) {
        this(value, usable, issue, List.of());
    }

    public static <T> AiParseResult<T> valid(T value) {
        return new AiParseResult<>(value, true, "");
    }

    public static <T> AiParseResult<T> invalid(T value, String issue) {
        return new AiParseResult<>(value, false, issue);
    }

    public boolean hasRejections() {
        return outcomes.stream().anyMatch(outcome -> !outcome.isAccepted());
    }
}
