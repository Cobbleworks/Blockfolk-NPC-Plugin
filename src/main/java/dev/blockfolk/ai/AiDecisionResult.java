package dev.blockfolk.ai;

/**
 * A parsed model decision together with the exact targets shown to the model.
 */
public record AiDecisionResult(AiDecision decision, AiTargetSnapshot targets) {
}
