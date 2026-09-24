package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

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
    void pairedCapabilitiesToggleTogether() {
        AiControlSettings settings = AiControlSettings.defaults();
        AiActionType[][] pairs = {{AiActionType.START_COMBAT, AiActionType.STOP_COMBAT},
                {AiActionType.FOLLOW, AiActionType.UNFOLLOW}, {AiActionType.START_ROUTE, AiActionType.PAUSE_ROUTE}};
        for (AiActionType[] pair : pairs) {
            settings = settings.toggle(pair[0]);
            assertTrue(settings.allowedActions().contains(pair[0]));
            assertTrue(settings.allowedActions().contains(pair[1]));
            settings = settings.toggle(pair[0]);
            assertFalse(settings.allowedActions().contains(pair[0]));
            assertFalse(settings.allowedActions().contains(pair[1]));
        }
    }

    @Test
    void olderSingleActionSettingsEnableBothSidesOfPair() {
        AiControlSettings settings = new AiControlSettings("", "", "", "", "",
                EnumSet.of(AiActionType.STOP_COMBAT, AiActionType.FOLLOW, AiActionType.PAUSE_ROUTE), false, true, false,
                false);

        assertTrue(settings.allowedActions().containsAll(EnumSet.of(AiActionType.START_COMBAT, AiActionType.STOP_COMBAT,
                AiActionType.FOLLOW, AiActionType.UNFOLLOW, AiActionType.START_ROUTE, AiActionType.PAUSE_ROUTE)));
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
        AiControlSettings settings = AiControlSettings.defaults().withIdentity("A guard").withRespondToChat(false);

        assertTrue(settings.enabled());
        assertFalse(settings.respondToChat());
    }

    @Test
    void composesStructuredContextWithHeadings() {
        AiControlSettings settings = AiControlSettings.defaults().withIdentity("Mira, the village guard")
                .withLikesDislikes("Likes cake; dislikes zombies").withGoal("Protect the gate")
                .withInformation("The market closes at sunset");

        assertTrue(settings.systemContext().contains("Identity:\nMira"));
        assertTrue(settings.systemContext().contains("Goal or role:\nProtect"));
        assertTrue(settings.systemContext().contains("Likes and dislikes:\nLikes cake"));
        assertTrue(settings.systemContext().contains("Knowledge and information:\nThe market"));
        assertTrue(settings.configuredSectionCount() == 4);
    }

    @Test
    void memorySettingIsOptInAndPreservedByOtherChanges() {
        AiControlSettings settings = AiControlSettings.defaults().withMemoryEnabled(true).withIdentity("A guard")
                .withRespondToChat(false);

        assertTrue(settings.memoryEnabled());
    }

    @Test
    void sharedConversationSettingIsOptInAndPreservedByOtherChanges() {
        AiControlSettings settings = AiControlSettings.defaults().withSharedConversation(true)
                .withIdentity("A communal storyteller");

        assertTrue(settings.sharedConversation());
        assertFalse(AiControlSettings.defaults().sharedConversation());
    }
}
