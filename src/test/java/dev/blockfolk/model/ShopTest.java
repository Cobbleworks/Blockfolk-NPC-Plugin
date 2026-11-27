package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.testsupport.TestItems;

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
    void tradesKeepTheirSlotSoEmptyRowsDoNotShiftLaterPages() {
        ShopOffer first = TestItems.offer(0);
        ShopOffer onPageTwo = TestItems.offer(12);
        Shop shop = Shop.create("market", null).withOffers(List.of(onPageTwo, first));

        Shop edited = shop.withPage(0, List.of(new ShopOffer(null, 0, null, null, null, null)));

        assertEquals(List.of(onPageTwo), edited.offers());
        assertEquals(List.of(1), edited.pagesWithTrades());
        assertEquals(1, edited.offers().getFirst().page());
    }

    @Test
    void tradesWithoutOrWithATakenSlotMoveToTheFirstFreeOne() {
        ShopOffer placed = TestItems.offer(1);
        ShopOffer clash = TestItems.offer(1);
        ShopOffer unplaced = new ShopOffer(TestItems.item(), null, TestItems.item());

        Shop shop = Shop.create("market", null).withOffers(List.of(placed, clash, unplaced));

        assertEquals(List.of(0, 1, 2), shop.offers().stream().map(ShopOffer::slot).toList());
        assertEquals(placed, shop.offerAt(1).orElseThrow());
    }

    @Test
    void aShopHoldsAtMostFivePagesOfTrades() {
        List<ShopOffer> offers = new ArrayList<>();
        for (int index = 0; index < Shop.MAX_TRADES + 5; index++)
            offers.add(new ShopOffer(TestItems.item(), null, TestItems.item()));

        assertEquals(Shop.MAX_TRADES, Shop.create("market", null).withOffers(offers).offers().size());
    }

    @Test
    void copyKeepsTradesUnderANewKey() {
        Shop copy = Shop.create("smithy", "Smithy").copyAs("smithy_2", "Smithy 2");

        assertEquals("smithy_2", copy.key());
        assertEquals("Smithy 2", copy.name());
    }
}
