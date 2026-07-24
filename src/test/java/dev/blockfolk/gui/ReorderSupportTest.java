package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReorderSupportTest {

    @Test
    void movesTheSelectedEntryAndKeepsAllKeys() {
        ReorderSupport.ReorderState state = new ReorderSupport.ReorderState(
                new ArrayList<>(List.of("first", "second", "third")));

        assertTrue(state.select(0));
        assertFalse(state.select(1));
        state.moveSelectedTo(2);
        state.clearSelection();

        assertEquals(List.of("second", "third", "first"), state.keys);
        assertEquals(null, state.selectedKey);
    }
}
