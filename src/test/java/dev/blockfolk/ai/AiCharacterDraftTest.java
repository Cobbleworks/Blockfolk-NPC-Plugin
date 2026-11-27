package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiCharacterDraftTest {

    @Test
    void parsesAllFiveSections() {
        AiCharacterDraft draft = AiCharacterDraft.parse("""
                {"identity":" You are Brom. ","behaviour":"Gruff.","goal":"Sell bread.",
                 "information":"The mill is north.","likes_dislikes":"Likes rye; hates rats."}""");
        assertEquals("You are Brom.", draft.identity());
        assertEquals("Gruff.", draft.behaviour());
        assertEquals("Sell bread.", draft.goal());
        assertEquals("The mill is north.", draft.information());
        assertEquals("Likes rye; hates rats.", draft.likesDislikes());
        assertEquals(5, draft.sectionCount());
    }

    @Test
    void toleratesCodeFencesAlternateKeysAndArrays() {
        AiCharacterDraft draft = AiCharacterDraft.parse("""
                ```json
                {"identity":"You are Ada.","behavior":"Calm.","likes":["Books.","Tea."],"goal":null}
                ```""");
        assertEquals("Calm.", draft.behaviour());
        assertEquals("Books. Tea.", draft.likesDislikes());
        assertEquals("", draft.goal());
        assertEquals(3, draft.sectionCount());
    }

    @Test
    void rejectsEmptyOrMalformedAnswers() {
        assertThrows(IllegalStateException.class, () -> AiCharacterDraft.parse("no json here"));
        assertThrows(IllegalStateException.class, () -> AiCharacterDraft.parse("{\"identity\": "));
        assertThrows(IllegalStateException.class, () -> AiCharacterDraft.parse("{\"identity\":\"  \"}"));
        assertThrows(IllegalStateException.class, () -> AiCharacterDraft.parse(null));
    }

    @Test
    void capsRunawaySections() {
        String longText = "x".repeat(AiCharacterDraft.MAX_SECTION_LENGTH + 500);
        AiCharacterDraft draft = AiCharacterDraft.parse("{\"identity\":\"" + longText + "\"}");
        assertEquals(AiCharacterDraft.MAX_SECTION_LENGTH, draft.identity().length());
    }

    @Test
    void applyingReplacesEveryCharacterSectionAndActivatesFirstContext() {
        AiControlSettings settings = AiControlSettings.defaults().withGoal("old goal");
        AiCharacterDraft draft = new AiCharacterDraft("You are Brom.", "Gruff.", "", "Lore.", "Rye.");
        AiControlSettings applied = draft.applyTo(AiControlSettings.defaults());
        assertTrue(applied.enabled());
        AiControlSettings replaced = draft.applyTo(settings);
        assertEquals("", replaced.goal());
        assertEquals("You are Brom.", replaced.identity());
        assertEquals("Rye.", replaced.likesDislikes());
    }
}
