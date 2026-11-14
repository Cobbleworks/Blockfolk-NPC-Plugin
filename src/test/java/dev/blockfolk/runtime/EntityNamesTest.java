package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EntityNamesTest {

    @Test
    void choosesTheArticleFromTheFirstLetter() {
        assertEquals("a zombie", EntityNames.withArticle("ZOMBIE"));
        assertEquals("an iron golem", EntityNames.withArticle("IRON_GOLEM"));
        assertEquals("an oak button", EntityNames.withArticle("OAK_BUTTON"));
    }
}
