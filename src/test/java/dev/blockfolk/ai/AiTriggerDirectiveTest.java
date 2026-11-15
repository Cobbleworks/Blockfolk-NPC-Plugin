package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiTriggerDirectiveTest {
    @Test
    void addsTheTriggerPromptToTheSystemPromptAsAnAuthorDirective() {
        StringBuilder system = new StringBuilder("rules");
        AiControlService.appendTriggerDirective(system, "  You now have to convince the player to buy this item. ");
        String prompt = system.toString();
        assertTrue(prompt.startsWith("rules"));
        assertTrue(prompt.contains("from the NPC's author, not from any player"));
        assertTrue(prompt.endsWith("You now have to convince the player to buy this item.\n"));
    }

    @Test
    void leavesTheSystemPromptAloneWithoutATriggerPrompt() {
        StringBuilder system = new StringBuilder("rules");
        AiControlService.appendTriggerDirective(system, null);
        AiControlService.appendTriggerDirective(system, "   ");
        assertEquals("rules", system.toString());
    }
}
