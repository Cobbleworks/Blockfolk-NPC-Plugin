package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NpcConversationServiceTest {

    private static final UUID TAVERN_KEEPER = UUID.randomUUID();
    private static final UUID GUARD = UUID.randomUUID();

    @Test
    void npcNeverRespondsToItsOwnLines() {
        // For example repeated idle Holo Dialog flavour text.
        assertFalse(NpcConversationService.heardOthers(TAVERN_KEEPER, List.of(TAVERN_KEEPER, TAVERN_KEEPER)));
    }

    @Test
    void npcRespondsWhenAnotherNpcSpokeInTheRound() {
        assertTrue(NpcConversationService.heardOthers(TAVERN_KEEPER, List.of(GUARD, TAVERN_KEEPER)));
        assertTrue(NpcConversationService.heardOthers(GUARD, List.of(TAVERN_KEEPER)));
    }

    @Test
    void formattingCodesAreRemovedFromHeardLines() {
        assertEquals("Fresh bread!", NpcConversationService.stripFormatting(" §6Fresh §lbread! "));
    }
}
