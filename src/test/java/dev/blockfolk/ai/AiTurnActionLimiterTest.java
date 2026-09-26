package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class AiTurnActionLimiterTest {

    @Test
    void keepsOnlyTheFirstSpeechWhilePreservingOtherActions() {
        AiDecision.Action first = new AiDecision.Action(AiActionType.SAY, "Hello", null, null);
        AiDecision.Action second = new AiDecision.Action(AiActionType.SAY, "Another message", null, null);
        AiDecision.Action wave = new AiDecision.Action(AiActionType.PLAY_ANIMATION, null, null, "wave");

        assertEquals(List.of(first, wave), AiTurnActionLimiter.limit(List.of(first, second, wave), 8, false));
        assertEquals(List.of(wave), AiTurnActionLimiter.limit(List.of(second, wave), 8, true));
    }

    @Test
    void stillHonorsTheRemainingActionBudget() {
        AiDecision.Action speech = new AiDecision.Action(AiActionType.SAY, "Hello", null, null);
        AiDecision.Action wave = new AiDecision.Action(AiActionType.PLAY_ANIMATION, null, null, "wave");

        assertEquals(List.of(speech), AiTurnActionLimiter.limit(List.of(speech, wave), 1, false));
    }
}
