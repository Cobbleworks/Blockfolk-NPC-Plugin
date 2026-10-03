package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ShopTest {
    @Test
    void newShopHasNoTrades() {
        Shop shop = Shop.create("bakery", "Bakery");

        assertTrue(shop.offers().isEmpty());
        assertTrue(shop.validOffers().isEmpty());
    }

    @Test
    void keysAreNormalized() {
        assertEquals("town_bakery", Shop.create("  Town Bakery! ", null).key());
        assertEquals(Shop.MAX_KEY_LENGTH, Shop.create("x".repeat(100), null).key().length());
        assertThrows(IllegalArgumentException.class, () -> Shop.create("  !! ", "Shop"));
    }

    @Test
    void emptyTradesAreDropped() {
        List<ShopOffer> offers = new ArrayList<>();
        offers.add(new ShopOffer(null, null, null));
        offers.add(null);

        assertTrue(Shop.create("shop", null).withOffers(offers).offers().isEmpty());
        assertTrue(Shop.create("shop", null).withOffers(null).offers().isEmpty());
    }

    @Test
    void emptyTradeIsNeitherValidNorKept() {
        ShopOffer offer = new ShopOffer(null, null, null);

        assertTrue(offer.isEmpty());
        assertFalse(offer.isValid());
    }

    @Test
    void nameIsTrimmedBlankFallsBackToKeyAndLongNamesAreCut() {
        assertEquals("Bakery", Shop.create("bakery", "  Bakery  ").name());
        assertEquals("bakery", Shop.create("bakery", "   ").name());
        assertEquals(Shop.MAX_NAME_LENGTH, Shop.create("bakery", "x".repeat(100)).name().length());
    }

    @Test
    void replacingTradesKeepsKeyAndName() {
        Shop shop = Shop.create("smithy", "Smithy").withOffers(List.of());

        assertEquals("smithy", shop.key());
        assertEquals("Smithy", shop.name());
    }

    @Test
    void copyKeepsTradesUnderANewKey() {
        Shop copy = Shop.create("smithy", "Smithy").copyAs("smithy_2", "Smithy 2");

        assertEquals("smithy_2", copy.key());
        assertEquals("Smithy 2", copy.name());
    }
}
