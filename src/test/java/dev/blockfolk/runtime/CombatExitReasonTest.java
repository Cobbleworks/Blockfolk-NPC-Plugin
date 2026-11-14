package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class CombatExitReasonTest {

    @Test
    void namesTheOpponentAndTheOutcome() {
        assertEquals("Combat ended: you defeated a zombie.", CombatExitReason.DEFEATED_TARGET.describe("a zombie"));
        assertEquals("Combat ended: the player Alex died, but not by your hand.",
                CombatExitReason.TARGET_DIED.describe("the player Alex"));
    }

    @Test
    void everyReasonHasAnUnnamedFallback() {
        for (CombatExitReason reason : CombatExitReason.values()) {
            String description = reason.describe(null);
            assertFalse(description.contains("null"), reason.name());
        }
    }
}
