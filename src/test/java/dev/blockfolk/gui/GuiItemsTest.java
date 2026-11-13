package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.AttackReaction;

class GuiItemsTest {
    @Test
    void leftClickAdvancesAndRightClickGoesBack() {
        assertEquals(1, GuiItems.cycleDirection(ClickType.LEFT));
        assertEquals(1, GuiItems.cycleDirection(ClickType.SHIFT_LEFT));
        assertEquals(-1, GuiItems.cycleDirection(ClickType.RIGHT));
        assertEquals(-1, GuiItems.cycleDirection(ClickType.SHIFT_RIGHT));
        assertEquals(0, GuiItems.cycleDirection(ClickType.MIDDLE));
        assertEquals(0, GuiItems.cycleDirection(ClickType.DROP));
    }

    @Test
    void stepWrapsInBothDirections() {
        assertEquals(AttackReaction.FIGHT_BACK, GuiItems.step(AttackReaction.IGNORE, 1));
        assertEquals(AttackReaction.HUNTING, GuiItems.step(AttackReaction.IGNORE, -1));
        assertEquals(AttackReaction.IGNORE, GuiItems.step(AttackReaction.HUNTING, 1));
        assertEquals(AttackReaction.IGNORE, GuiItems.step(AttackReaction.IGNORE, 0));
    }
}
