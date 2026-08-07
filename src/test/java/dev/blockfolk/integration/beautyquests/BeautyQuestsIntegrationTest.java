package dev.blockfolk.integration.beautyquests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BeautyQuestsIntegrationTest {

    private static final UUID INSTANCE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void findsNpcIdInQuestStarterLore() {
        assertEquals(INSTANCE_ID, BeautyQuestsIntegration.referencedInstanceId(
                List.of("§7Choose the NPC that starts this quest.", "§7Guard §8(blockfolk#" + INSTANCE_ID + ")")));
    }

    @Test
    void findsNpcIdInStageLore() {
        assertEquals(INSTANCE_ID,
                BeautyQuestsIntegration.referencedInstanceId(List.of("§8ID: §lblockfolk#" + INSTANCE_ID)));
    }

    @Test
    void ignoresOtherNpcFactoriesAndMissingLore() {
        assertNull(BeautyQuestsIntegration.referencedInstanceId(List.of("§7citizens#42")));
        assertNull(BeautyQuestsIntegration.referencedInstanceId(null));
    }
}
