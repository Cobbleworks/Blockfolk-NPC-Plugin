package dev.blockfolk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

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

    @Test
    void wrapsLongTooltipTextWithoutDroppingWords() {
        assertEquals(List.of("A long fact", "about an", "NPC"), TextUtil.wrap("A long fact about an NPC", 11));
        assertEquals(List.of("abcde", "fghij", "kl"), TextUtil.wrap("abcdefghijkl", 5));
        assertEquals(List.of("first", "second"), TextUtil.wrap("first\nsecond", 6));
        assertThrows(IllegalArgumentException.class, () -> TextUtil.wrap("text", 0));
    }
}
