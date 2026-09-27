package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;

import org.junit.jupiter.api.Test;

class NpcResponseIdsTest {

    @Test
    void usesBareNameForSingleNpcAndNumbersDuplicateNames() {
        assertEquals(List.of("npc_mr_mario"), NpcResponseIds.forNames(List.of("Mr. Mario")));
        assertEquals(List.of("npc_mr_mario_01", "npc_mr_mario_02", "npc_mira"),
                NpcResponseIds.forNames(List.of("Mr. Mario", "Mr Mario", "Mira")));
    }

    @Test
    void handlesColorsAccentsAndNamesWithoutLatinLetters() {
        assertEquals(List.of("npc_eloise", "npc_unnamed_01", "npc_unnamed_02"),
                NpcResponseIds.forNames(List.of("§aÉloïse", "李雷", "莉莉")));
    }

    @Test
    void avoidsNumberedAliasCollidingWithAnotherNpcName() {
        assertEquals(List.of("npc_mario_02", "npc_mario_03", "npc_mario_01"),
                NpcResponseIds.forNames(List.of("Mario", "Mario", "Mario 01")));
    }
}
