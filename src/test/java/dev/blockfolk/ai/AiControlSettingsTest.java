package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiControlSettingsTest {

    @Test
    void defaultsAreDialogueAndVisualOnly() {
        AiControlSettings settings = AiControlSettings.defaults();
        assertEquals(AiMode.RESPOND, settings.mode());
        assertTrue(settings.allowedActions().contains(AiActionType.SAY));
        assertTrue(settings.allowedActions().contains(AiActionType.LOOK_AT));
        assertTrue(settings.allowedActions().contains(AiActionType.PLAY_ANIMATION));
        assertTrue(!settings.allowedActions().contains(AiActionType.START_COMBAT));
    }

    @Test
    void doNothingCannotBeDisabled() {
        AiControlSettings settings = AiControlSettings.defaults().toggle(AiActionType.DO_NOTHING);
        assertTrue(settings.allowedActions().contains(AiActionType.DO_NOTHING));
    }

    @Test
    void savingPromptEnablesConversationAndSpeechIsIntrinsic() {
        AiControlSettings settings = AiControlSettings.defaults().withPrompt("Be a friendly guard")
                .toggle(AiActionType.SAY);

        assertTrue(settings.enabled());
        assertTrue(settings.allowedActions().contains(AiActionType.SAY));
    }
}
