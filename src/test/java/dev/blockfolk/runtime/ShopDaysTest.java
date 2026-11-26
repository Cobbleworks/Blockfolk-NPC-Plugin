package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ShopDaysTest {
    @Test
    void aNewDayStartsAtSunrise() {
        assertEquals(0, ShopDays.day(23_999));
        assertEquals(1, ShopDays.day(24_000));
        assertEquals(-1, ShopDays.day(-1));
        assertEquals(24_000, ShopDays.ticksUntilNextDay(48_000));
        assertEquals(1, ShopDays.ticksUntilNextDay(47_999));
    }

    @Test
    void pagesFollowInOrderAndWrapAround() {
        List<Integer> pages = List.of(0, 2, 4);

        assertEquals(2, ShopDays.pageAfter(pages, 0, 1));
        assertEquals(4, ShopDays.pageAfter(pages, 0, 2));
        assertEquals(0, ShopDays.pageAfter(pages, 4, 1));
        assertEquals(2, ShopDays.pageAfter(pages, 2, 3 * 1_000_000_000L), "whole cycles change nothing");
    }

    @Test
    void anEmptiedPageGivesWayToTheNextPageWithTrades() {
        List<Integer> pages = List.of(0, 2, 4);

        assertEquals(2, ShopDays.pageOnSale(pages, 1));
        assertEquals(0, ShopDays.pageOnSale(pages, 3 + 2), "past the last page wraps to the first");
        assertEquals(4, ShopDays.pageOnSale(pages, 4));
        assertEquals(3, ShopDays.pageOnSale(List.of(), 3), "a shop without trades keeps its page");
    }
}
