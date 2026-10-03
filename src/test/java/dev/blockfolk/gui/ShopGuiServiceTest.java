package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

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
    void editedPageReplacesOnlyItsOwnTrades() {
        List<Integer> offers = IntStream.range(0, 15).boxed().toList();
        List<Integer> edited = List.of(100, 101);

        List<Integer> result = ShopGuiService.replacePage(offers, 1, edited);

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 100, 101), result);
    }

    @Test
    void editingAnEmptyNextPageAppendsTrades() {
        List<Integer> offers = IntStream.range(0, 10).boxed().toList();

        assertEquals(11, ShopGuiService.replacePage(offers, 1, List.of(42)).size());
    }

    @Test
    void anotherPageOpensOnlyWhenTheLastOneIsFull() {
        assertEquals(1, ShopGuiService.pageCount(0));
        assertEquals(1, ShopGuiService.pageCount(9));
        assertEquals(2, ShopGuiService.pageCount(10));
        assertEquals(ShopGuiService.MAX_PAGES, ShopGuiService.pageCount(500));
    }
}
