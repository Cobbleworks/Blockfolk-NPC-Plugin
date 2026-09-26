package dev.blockfolk.ai;

import java.util.ArrayList;
import java.util.List;

/** Keeps one NPC's chained action turn within its action and speech limits. */
final class AiTurnActionLimiter {

    private AiTurnActionLimiter() {
    }

    static List<AiDecision.Action> limit(List<AiDecision.Action> proposed, int remaining, boolean alreadySpoke) {
        List<AiDecision.Action> accepted = new ArrayList<>();
        boolean spoke = alreadySpoke;
        for (AiDecision.Action action : proposed) {
            if (accepted.size() >= remaining) {
                break;
            }
            if (action.type() == AiActionType.SAY) {
                if (spoke) {
                    continue;
                }
                spoke = true;
            }
            accepted.add(action);
        }
        return List.copyOf(accepted);
    }
}
