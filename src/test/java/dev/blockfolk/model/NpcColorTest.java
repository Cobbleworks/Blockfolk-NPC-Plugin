package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.format.NamedTextColor;

class NpcColorTest {

    @Test
    void defaultsInvalidAndMissingStoredColorsToCurrentGoldColor() {
        assertEquals(NpcColor.ORANGE, NpcColor.fromStored(null));
        assertEquals(NpcColor.ORANGE, NpcColor.fromStored("unknown"));
        assertEquals(NamedTextColor.GOLD, NpcColor.ORANGE.textColor());
    }

    @Test
    void cyclesThroughAllConcreteColorsAndWraps() {
        NpcColor color = NpcColor.WHITE;
        for (int index = 0; index < NpcColor.values().length; index++) {
            color = color.next();
        }
        assertEquals(NpcColor.WHITE, color);
    }

    @Test
    void readsStoredColorNamesLeniently() {
        assertEquals(NpcColor.LIGHT_BLUE, NpcColor.fromStored("light-blue"));
    }
}
