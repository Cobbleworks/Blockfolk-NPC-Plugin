package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShopGuiServiceTest {
    @Test
    void tradesFillTwoColumnsPerRow() {
        assertEquals(0, ShopGuiService.tradeBaseSlot(0));
        assertEquals(5, ShopGuiService.tradeBaseSlot(1));
        assertEquals(9, ShopGuiService.tradeBaseSlot(2));
        assertEquals(41, ShopGuiService.tradeBaseSlot(9));
    }

    @Test
    void onlyCostAndResultSlotsAreEditable() {
        for (int index = 0; index < ShopGuiService.TRADES_PER_PAGE; index++) {
            int base = ShopGuiService.tradeBaseSlot(index);
            assertTrue(ShopGuiService.isItemSlot(base));
            assertTrue(ShopGuiService.isItemSlot(base + 1));
            assertFalse(ShopGuiService.isItemSlot(base + 2), "arrow slot");
            assertTrue(ShopGuiService.isItemSlot(base + 3));
        }
        assertFalse(ShopGuiService.isItemSlot(4), "divider");
        assertFalse(ShopGuiService.isItemSlot(45), "button row");
        assertFalse(ShopGuiService.isItemSlot(-1));
    }

    @Test
    void rowsMapToShopSlotsPageByPage() {
        assertEquals(0, ShopGuiService.tradeSlot(0, 0));
        assertEquals(19, ShopGuiService.tradeSlot(1, 9));
        assertEquals(42, ShopGuiService.tradeSlot(4, 2));
    }

    @Test
    void arrowsMapToTheirTradeRow() {
        for (int index = 0; index < ShopGuiService.TRADES_PER_PAGE; index++)
            assertEquals(index, ShopGuiService.arrowRow(ShopGuiService.tradeBaseSlot(index) + 2));
        assertEquals(-1, ShopGuiService.arrowRow(0), "cost slot");
        assertEquals(-1, ShopGuiService.arrowRow(4), "divider");
        assertEquals(-1, ShopGuiService.arrowRow(47), "button row");
    }
}
