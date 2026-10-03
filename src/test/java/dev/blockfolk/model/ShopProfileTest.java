package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShopProfileTest {
    @Test
    void emptyShopHasNoTitleOrTrades() {
        ShopProfile shop = ShopProfile.empty();

        assertNull(shop.title());
        assertTrue(shop.offers().isEmpty());
        assertTrue(shop.validOffers().isEmpty());
        assertTrue(shop.isEmpty());
    }

    @Test
    void shopWithOnlyATitleIsNotEmpty() {
        assertFalse(ShopProfile.empty().withTitle("Bakery").isEmpty());
    }

    @Test
    void emptyTradesAreDropped() {
        List<ShopOffer> offers = new ArrayList<>();
        offers.add(new ShopOffer(null, null, null));
        offers.add(null);

        assertTrue(ShopProfile.empty().withOffers(offers).offers().isEmpty());
        assertTrue(ShopProfile.empty().withOffers(null).offers().isEmpty());
    }

    @Test
    void emptyTradeIsNeitherValidNorKept() {
        ShopOffer offer = new ShopOffer(null, null, null);

        assertTrue(offer.isEmpty());
        assertFalse(offer.isValid());
    }

    @Test
    void titleIsTrimmedBlankBecomesNullAndLongTitlesAreCut() {
        assertEquals("Bakery", ShopProfile.empty().withTitle("  Bakery  ").title());
        assertNull(ShopProfile.empty().withTitle("   ").title());
        assertEquals(ShopProfile.MAX_TITLE_LENGTH, ShopProfile.empty().withTitle("x".repeat(100)).title().length());
    }

    @Test
    void replacingTradesKeepsTheTitle() {
        ShopProfile shop = ShopProfile.empty().withTitle("Smithy");

        assertEquals("Smithy", shop.withOffers(List.of()).title());
    }
}
