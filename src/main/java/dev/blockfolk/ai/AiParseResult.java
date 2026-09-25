package dev.blockfolk.ai;

/** A parsed model response plus a safe, non-content diagnostic. */
public record AiParseResult<T>(T value, boolean usable, String issue) {

    public static <T> AiParseResult<T> valid(T value) {
        return new AiParseResult<>(value, true, "");
    }

    public static <T> AiParseResult<T> invalid(T value, String issue) {
        return new AiParseResult<>(value, false, issue);
    }
}
