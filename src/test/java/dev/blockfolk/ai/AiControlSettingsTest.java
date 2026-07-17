package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiControlSettingsTest {

    @Test
    void defaultsAreDialogueAndVisualOnly() {
        AiControlSettings settings = AiControlSettings.defaults();
        assertFalse(settings.greetOnApproach());
        assertTrue(settings.allowedActions().contains(AiActionType.SAY));
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
        AiControlSettings settings = AiControlSettings.defaults().withIdentity("A friendly guard")
                .toggle(AiActionType.SAY);

        assertTrue(settings.enabled());
        assertTrue(settings.allowedActions().contains(AiActionType.SAY));
    }

    @Test
    void nearbyChatResponsesCanBeDisabledWithoutEnablingGreetings() {
        AiControlSettings settings = AiControlSettings.defaults().withIdentity("A guard")
                .withRespondToChat(false);

        assertTrue(settings.enabled());
        assertFalse(settings.greetOnApproach());
        assertFalse(settings.respondToChat());
    }

    @Test
    void composesStructuredContextWithHeadings() {
        AiControlSettings settings = AiControlSettings.defaults()
                .withIdentity("Mira, the village guard")
                .withGoal("Protect the gate")
                .withInformation("The market closes at sunset");

        assertTrue(settings.systemContext().contains("Identity:\nMira"));
        assertTrue(settings.systemContext().contains("Goal or role:\nProtect"));
        assertTrue(settings.systemContext().contains("Knowledge and information:\nThe market"));
        assertTrue(settings.configuredSectionCount() == 3);
    }
}
