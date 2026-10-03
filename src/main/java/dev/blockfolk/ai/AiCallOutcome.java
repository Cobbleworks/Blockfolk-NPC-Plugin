package dev.blockfolk.ai;

/**
 * The validation result of one model function call. Exactly one of
 * {@code action} and {@code rejection} is set.
 */
public record AiCallOutcome(int call, String function, AiDecision.Action action, String rejection) {

    static AiCallOutcome accepted(int call, String function, AiDecision.Action action) {
        return new AiCallOutcome(call, function, action, null);
    }

    static AiCallOutcome rejected(int call, String function, String rejection) {
        return new AiCallOutcome(call, function, null, rejection);
    }

    public boolean isAccepted() {
        return action != null;
    }
}
