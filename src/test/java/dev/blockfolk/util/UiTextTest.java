package dev.blockfolk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

class UiTextTest {

    @Test
    void npcDialogHighlightsOnlyTheSpeakerName() {
        Component dialog = UiText.npcDialog("Ada", "Hello there");

        assertEquals(NamedTextColor.GOLD, dialog.color());
        assertEquals(TextDecoration.State.TRUE, dialog.decoration(TextDecoration.BOLD));
        assertEquals(2, dialog.children().size());
        assertEquals(TextDecoration.State.FALSE,
                dialog.children().get(0).decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.FALSE,
                dialog.children().get(1).decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.WHITE, dialog.children().get(1).color());
    }

    @Test
    void feedbackPrefixDoesNotMakeTheMessageBold() {
        Component feedback = UiText.success("Saved.");

        assertEquals(TextDecoration.State.TRUE, feedback.decoration(TextDecoration.BOLD));
        assertEquals(1, feedback.children().size());
        assertEquals(TextDecoration.State.FALSE,
                feedback.children().getFirst().decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.GREEN, feedback.children().getFirst().color());
    }
}
