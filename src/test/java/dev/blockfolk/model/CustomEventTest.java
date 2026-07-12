package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CustomEventTest {
    @Test
    void acceptsGroupedEventNamesWithoutChangingCase() {
        CustomEvent event = new CustomEvent("jungle/idolWasStolen");
        assertEquals("jungle/idolWasStolen", event.getName());
    }

    @Test
    void rejectsEmptySegmentsAndUnsafePathCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new CustomEvent("jungle//opened"));
        assertThrows(IllegalArgumentException.class, () -> new CustomEvent("../opened"));
    }
}
