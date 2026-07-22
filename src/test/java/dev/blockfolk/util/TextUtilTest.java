package dev.blockfolk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TextUtilTest {

    @Test
    void stripsMarkdownCodeFences() {
        assertEquals("{\"actions\":[]}", TextUtil.stripCodeFence("```json\n{\"actions\":[]}\n```"));
        assertEquals("plain", TextUtil.stripCodeFence(" plain "));
    }

    @Test
    void abbreviatesSingleLineText() {
        assertEquals("one two", TextUtil.abbreviateSingleLine("one\ntwo", 20));
        assertEquals("abcd...", TextUtil.abbreviate("abcdefghij", 7));
        assertThrows(IllegalArgumentException.class, () -> TextUtil.abbreviate("text", 3));
    }
}
