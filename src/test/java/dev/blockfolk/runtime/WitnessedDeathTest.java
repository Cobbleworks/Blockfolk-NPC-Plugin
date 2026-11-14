package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WitnessedDeathTest {

    @Test
    void namesTheKillerAndVictim() {
        assertEquals("The player Alex killed the NPC Bob nearby.",
                WitnessedDeath.describe("the player Alex", "the NPC Bob", "player_attack"));
    }

    @Test
    void deathsWithoutAKillerNameTheCause() {
        assertEquals("The player Alex died nearby (fall).", WitnessedDeath.describe(null, "the player Alex", "fall"));
        assertEquals("The NPC Bob died nearby.", WitnessedDeath.describe(null, "the NPC Bob", null));
    }
}
