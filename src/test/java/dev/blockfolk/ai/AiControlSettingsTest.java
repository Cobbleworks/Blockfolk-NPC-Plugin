package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiControlSettingsTest {

    @Test
    void defaultsAreDialogueAndVisualOnly() {
        AiControlSettings settings = AiControlSettings.defaults();
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
    void savingPromptEnablesAiAndSpeechStartsEnabled() {
        AiControlSettings settings = AiControlSettings.defaults().withIdentity("A friendly guard");

        assertTrue(settings.enabled());
        assertTrue(settings.allowedActions().contains(AiActionType.SAY));
    }

    @Test
    void speechCapabilityCanBeDisabledInStoredSettings() {
        AiControlSettings settings = AiControlSettings.defaults().withIdentity("A guard")
                .toggle(AiActionType.SAY);

        assertTrue(settings.enabled());
        assertFalse(settings.allowedActions().contains(AiActionType.SAY));
    }

    @Test
    void requiredCapabilityCanBeEnabledWithoutChangingOtherSettings() {
        AiControlSettings settings = AiControlSettings.defaults().withIdentity("A quiet guard")
                .toggle(AiActionType.SAY).withMemoryEnabled(true)
                .withActionEnabled(AiActionType.SAY);

        assertTrue(settings.allowedActions().contains(AiActionType.SAY));
        assertTrue(settings.memoryEnabled());
        assertTrue(settings.enabled());
    }

    @Test
    void composesStructuredContextWithHeadings() {
        AiControlSettings settings = AiControlSettings.defaults()
                .withIdentity("Mira, the village guard")
                .withLikesDislikes("Likes cake; dislikes zombies")
                .withGoal("Protect the gate")
                .withInformation("The market closes at sunset");

        assertTrue(settings.systemContext().contains("Identity:\nMira"));
        assertTrue(settings.systemContext().contains("Goal or role:\nProtect"));
        assertTrue(settings.systemContext().contains("Likes and dislikes:\nLikes cake"));
        assertTrue(settings.systemContext().contains("Knowledge and information:\nThe market"));
        assertTrue(settings.configuredSectionCount() == 4);
    }

    @Test
    void memorySettingIsOptInAndPreservedByOtherChanges() {
        AiControlSettings settings = AiControlSettings.defaults().withMemoryEnabled(true)
                .withIdentity("A guard");

        assertTrue(settings.memoryEnabled());
    }

    @Test
    void inventorySettingIsOptInAndPreservedByOtherChanges() {
        AiControlSettings settings = AiControlSettings.defaults().withInventoryEnabled(true)
                .withIdentity("A courier");

        assertTrue(settings.inventoryEnabled());
        assertFalse(AiControlSettings.defaults().inventoryEnabled());
    }

    @Test
    void conversationScopeDefaultsToPrivateAndIsPreserved() {
        AiControlSettings settings = AiControlSettings.defaults().withSharedConversations(true)
                .withIdentity("A tavern keeper").withMemoryEnabled(true);

        assertTrue(settings.sharedConversations());
        assertFalse(AiControlSettings.defaults().sharedConversations());
    }

}
