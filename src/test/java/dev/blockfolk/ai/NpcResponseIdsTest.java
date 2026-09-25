package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class NpcResponseIdsTest {

    @Test
    void makesReadableIdsStablePerInstanceAndDisambiguatesDuplicateNames() {
        UUID first = UUID.fromString("12345678-90ab-cdef-1111-222222222222");
        UUID second = UUID.fromString("fedcba98-7654-3210-3333-444444444444");

        assertEquals("npc_mr_mario_1234567890abcdef", NpcResponseIds.forInstance("Mr. Mario", first));
        assertEquals("npc_mr_mario_1234567890abcdef", NpcResponseIds.forInstance("Mr. Mario", first));
        assertNotEquals(NpcResponseIds.forInstance("Mr. Mario", first), NpcResponseIds.forInstance("Mr Mario", second));
    }

    @Test
    void handlesColorsAccentsAndNamesWithoutLatinLetters() {
        UUID first = UUID.fromString("12345678-90ab-cdef-1111-222222222222");
        UUID second = UUID.fromString("fedcba98-7654-3210-3333-444444444444");

        assertEquals("npc_eloise_1234567890abcdef", NpcResponseIds.forInstance("§aÉloïse", first));
        assertEquals("npc_unnamed_1234567890abcdef", NpcResponseIds.forInstance("李雷", first));
        assertEquals("npc_unnamed_fedcba9876543210", NpcResponseIds.forInstance("莉莉", second));
    }
}
