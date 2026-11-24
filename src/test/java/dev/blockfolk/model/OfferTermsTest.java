package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.blockfolk.testsupport.TestItems;

class OfferTermsTest {
    @Test
    void moneyIsRoundedToCentsAndKeptInRange() {
        assertEquals(12.35, OfferTerms.money(12.345));
        assertEquals(0, OfferTerms.money(-3));
        assertEquals(0, OfferTerms.money(Double.NaN));
        assertEquals(0, OfferTerms.money(Double.POSITIVE_INFINITY));
        assertEquals(OfferTerms.MAX_MONEY, OfferTerms.money(1e15));
    }

    @Test
    void weightAndStockStayInTheirRanges() {
        OfferTerms terms = OfferTerms.DEFAULT.withWeight(0).withStockLimit(-4);
        assertEquals(OfferTerms.MIN_WEIGHT, terms.weight());
        assertFalse(terms.limited());
        assertEquals(OfferTerms.MAX_WEIGHT, OfferTerms.DEFAULT.withWeight(1_000).weight());
        assertEquals(OfferTerms.MAX_STOCK, OfferTerms.DEFAULT.withStockLimit(Integer.MAX_VALUE).stockLimit());
    }

    @Test
    void aTradeNeedsSomethingToGiveAndSomethingToGet() {
        var item = TestItems.item();
        assertTrue(new ShopOffer(null, item, null, item, OfferTerms.DEFAULT).isValid(), "item for item");
        assertTrue(new ShopOffer(null, null, null, item, OfferTerms.DEFAULT.withPrice(5)).isValid(), "buy for money");
        assertTrue(new ShopOffer(null, item, null, null, OfferTerms.DEFAULT.withPayout(5)).isValid(), "sell for money");
        assertFalse(new ShopOffer(null, null, null, item, OfferTerms.DEFAULT).isValid(), "free item");
        assertFalse(new ShopOffer(null, item, null, null, OfferTerms.DEFAULT).isValid(), "nothing in return");
    }

    @Test
    void secondCostAloneMovesIntoTheFirstSlot() {
        var item = TestItems.item();
        ShopOffer offer = new ShopOffer(null, null, item, item, OfferTerms.DEFAULT);

        assertSame(item, offer.cost());
        assertNull(offer.secondCost());
    }

    @Test
    void onlyMoneyOrStockNeedTheShopMenu() {
        var item = TestItems.item();
        assertFalse(new ShopOffer(null, item, null, item, OfferTerms.DEFAULT).needsShopMenu());
        assertTrue(new ShopOffer(null, item, null, item, OfferTerms.DEFAULT.withStockLimit(1)).needsShopMenu());
        assertTrue(new ShopOffer(null, item, null, item, OfferTerms.DEFAULT.withPayout(1)).needsShopMenu());
    }

    @Test
    void editingItemsKeepsTheIdAndTerms() {
        var item = TestItems.item();
        OfferTerms terms = OfferTerms.DEFAULT.withPrice(9).withStockLimit(3);
        ShopOffer offer = new ShopOffer("abc123", item, null, item, terms);

        ShopOffer edited = offer.withItems(TestItems.item(), null, item);

        assertEquals("abc123", edited.id());
        assertEquals(terms, edited.terms());
    }

    @Test
    void invalidIdsAreReplaced() {
        var item = TestItems.item();
        assertNotEquals("bad id!", new ShopOffer("bad id!", item, null, item, null).id());
        assertTrue(ShopOffer.isValidId(new ShopOffer(item, null, item).id()));
    }

    @Test
    void duplicateIdsInAShopAreMadeUnique() {
        var item = TestItems.item();
        ShopOffer first = new ShopOffer("same", item, null, item, OfferTerms.DEFAULT);
        ShopOffer copy = new ShopOffer("same", item, null, item, OfferTerms.DEFAULT.withPrice(4));

        Shop shop = Shop.create("smithy", null).withOffers(List.of(first, copy));

        assertEquals("same", shop.offers().get(0).id());
        assertNotEquals("same", shop.offers().get(1).id());
        assertEquals(4, shop.offers().get(1).terms().price());
    }

    @Test
    void shopStateCountsSharedAndPerPlayerStockSeparately() {
        UUID alex = UUID.randomUUID();
        UUID sam = UUID.randomUUID();
        ShopState state = ShopState.fresh(0);

        state.recordSale("sword", StockScope.SHARED, alex, 2);
        state.recordSale("apple", StockScope.PER_PLAYER, alex, 3);

        assertEquals(2, state.sold("sword", StockScope.SHARED, sam), "shared stock is the same for everyone");
        assertEquals(3, state.sold("apple", StockScope.PER_PLAYER, alex));
        assertEquals(0, state.sold("apple", StockScope.PER_PLAYER, sam));

        state.restock(100);

        assertEquals(100, state.periodStart());
        assertEquals(0, state.sold("sword", StockScope.SHARED, alex));
        assertEquals(0, state.sold("apple", StockScope.PER_PLAYER, alex));
    }
}
