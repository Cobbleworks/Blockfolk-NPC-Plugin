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
    void disabledShopHasNoTitleOrTrades() {
        ShopProfile shop = ShopProfile.disabled();

        assertFalse(shop.enabled());
        assertNull(shop.title());
        assertTrue(shop.offers().isEmpty());
        assertFalse(shop.isOpenable());
    }

    @Test
    void enabledShopWithoutCompleteTradesCannotBeOpened() {
        ShopProfile shop = ShopProfile.disabled().withEnabled(true);

        assertTrue(shop.enabled());
        assertTrue(shop.validOffers().isEmpty());
        assertFalse(shop.isOpenable());
    }

    @Test
    void emptyTradesAreDropped() {
        List<ShopOffer> offers = new ArrayList<>();
        offers.add(new ShopOffer(null, null, null));
        offers.add(null);

        assertTrue(ShopProfile.disabled().withOffers(offers).offers().isEmpty());
        assertTrue(ShopProfile.disabled().withOffers(null).offers().isEmpty());
    }

    @Test
    void emptyTradeIsNeitherValidNorKept() {
        ShopOffer offer = new ShopOffer(null, null, null);

        assertTrue(offer.isEmpty());
        assertFalse(offer.isValid());
    }

    @Test
    void titleIsTrimmedBlankBecomesNullAndLongTitlesAreCut() {
        assertEquals("Bakery", ShopProfile.disabled().withTitle("  Bakery  ").title());
        assertNull(ShopProfile.disabled().withTitle("   ").title());
        assertEquals(ShopProfile.MAX_TITLE_LENGTH, ShopProfile.disabled().withTitle("x".repeat(100)).title().length());
    }

    @Test
    void withMethodsKeepTheOtherFields() {
        ShopProfile shop = ShopProfile.disabled().withTitle("Smithy").withEnabled(true);

        assertEquals("Smithy", shop.title());
        assertTrue(shop.enabled());
        assertEquals("Smithy", shop.withEnabled(false).title());
    }
}
