package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.OfferAvailability;
import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopAssortment;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.ShopState;
import dev.blockfolk.model.StockScope;
import dev.blockfolk.repository.ShopRepository;
import dev.blockfolk.repository.ShopStateRepository;
import dev.blockfolk.testsupport.TestItems;

class NpcShopServiceTest {
    private static final long MINUTE = 60_000L;

    private final ShopRepository shops = mock(ShopRepository.class);
    private final ShopStateRepository states = mock(ShopStateRepository.class);
    private final Map<String, ShopState> stored = new HashMap<>();
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private NpcShopService service;

    @BeforeEach
    void setUp() {
        when(states.get(org.mockito.ArgumentMatchers.anyString(), anyLong())).thenAnswer(
                call -> stored.computeIfAbsent(call.getArgument(0), ignored -> ShopState.fresh(call.getArgument(1))));
        service = new NpcShopService(null, shops, states, ShopEconomy.none(), now::get, new Random(11));
    }

    @Test
    void rotatingShopShowsTheSameTradesUntilTheIntervalEnds() {
        Shop shop = rotatingShop(30, 10, 60);
        List<ShopOffer> first = service.assortment(shop).offers();
        now.addAndGet(59 * MINUTE);

        assertEquals(10, first.size());
        assertEquals(first, service.assortment(shop).offers(), "same assortment within the hour");
    }

    @Test
    void afterTheIntervalTenNewTradesAreOnSale() {
        Shop shop = rotatingShop(30, 10, 60);
        Set<String> first = ids(service.assortment(shop).offers());
        now.addAndGet(60 * MINUTE);

        Set<String> second = ids(service.assortment(shop).offers());

        assertEquals(10, second.size());
        assertTrue(second.stream().noneMatch(first::contains), "30 trades allow an entirely new assortment");
    }

    @Test
    void countdownPointsAtTheNextRestock() {
        Shop shop = rotatingShop(30, 10, 60);
        long opened = now.get();
        service.assortment(shop);
        now.addAndGet(150 * MINUTE);

        NpcShopService.Assortment assortment = service.assortment(shop);

        // Periods keep their schedule: 120 minutes in, the next one starts at 180.
        assertEquals(opened + 180 * MINUTE, assortment.nextRefreshAt());
    }

    @Test
    void alwaysOfferedTradesAreShownOnTopOfTheRotation() {
        ShopOffer pinned = new ShopOffer(null, TestItems.item(), null, TestItems.item(),
                OfferTerms.DEFAULT.withAvailability(OfferAvailability.ALWAYS));
        List<ShopOffer> offers = new java.util.ArrayList<>(TestItems.offers(8));
        offers.add(pinned);
        Shop shop = new Shop("market", "Market", offers, new ShopAssortment(3, 60));

        List<ShopOffer> visible = service.assortment(shop).offers();

        assertEquals(4, visible.size());
        assertTrue(visible.contains(pinned));
    }

    @Test
    void sharedStockRunsOutForEveryoneAndRestocksWithTheNextPeriod() {
        ShopOffer sword = new ShopOffer(null, TestItems.item(), null, TestItems.item(),
                OfferTerms.DEFAULT.withPrice(100).withStockLimit(2));
        Shop shop = new Shop("armory", "Armory", List.of(sword), new ShopAssortment(0, 30));
        UUID alex = UUID.randomUUID();
        UUID sam = UUID.randomUUID();
        service.assortment(shop);
        stored.get("armory").recordSale(sword.id(), StockScope.SHARED, alex, 2);

        assertEquals(0, service.remaining(shop, sword, alex));
        assertEquals(0, service.remaining(shop, sword, sam));
        assertEquals(2, service.soldThisPeriod(shop, sword));

        now.addAndGet(30 * MINUTE);
        service.assortment(shop);

        assertEquals(2, service.remaining(shop, sword, sam));
    }

    @Test
    void perPlayerStockIsCountedForEachBuyer() {
        ShopOffer apple = new ShopOffer(null, TestItems.item(), null, TestItems.item(),
                OfferTerms.DEFAULT.withStockLimit(1).withStockScope(StockScope.PER_PLAYER));
        Shop shop = new Shop("farm", "Farm", List.of(apple));
        UUID alex = UUID.randomUUID();
        service.assortment(shop);
        stored.get("farm").recordSale(apple.id(), StockScope.PER_PLAYER, alex, 1);

        assertEquals(0, service.remaining(shop, apple, alex));
        assertEquals(1, service.remaining(shop, apple, UUID.randomUUID()));
    }

    @Test
    void restockNowRefillsAndDrawsANewRotationImmediately() {
        Shop shop = rotatingShop(20, 5, 0);
        Set<String> before = ids(service.assortment(shop).offers());
        ShopOffer sold = shop.findOffer(before.iterator().next()).orElseThrow();
        stored.get(shop.key()).recordSale(sold.id(), StockScope.SHARED, UUID.randomUUID(), 1);

        service.restockNow(shop);
        Set<String> after = ids(service.assortment(shop).offers());

        assertNotEquals(before, after);
        assertEquals(0, stored.get(shop.key()).sold(sold.id(), StockScope.SHARED, UUID.randomUUID()));
    }

    @Test
    void shopsWithoutIntervalKeepTheirAssortmentUntilRestockedByHand() {
        Shop shop = rotatingShop(20, 5, 0);
        Set<String> first = ids(service.assortment(shop).offers());
        now.addAndGet(365L * 24 * 60 * MINUTE);

        assertEquals(first, ids(service.assortment(shop).offers()));
        assertFalse(service.assortment(shop).refreshes());
    }

    @Test
    void removedTradesAreReplacedWithoutWaitingForTheNextRotation() {
        Shop shop = rotatingShop(20, 5, 60);
        List<ShopOffer> visible = service.assortment(shop).offers();
        ShopOffer removed = visible.getFirst();
        Shop edited = shop.withOffers(shop.offers().stream().filter(offer -> offer != removed).toList());

        List<ShopOffer> after = service.assortment(edited).offers();

        assertEquals(5, after.size());
        assertFalse(after.contains(removed));
    }

    @Test
    void unlimitedTradesNeverRunOut() {
        ShopOffer bread = new ShopOffer(TestItems.item(), null, TestItems.item());
        Shop shop = new Shop("bakery", "Bakery", List.of(bread));

        assertEquals(Integer.MAX_VALUE, service.remaining(shop, bread, UUID.randomUUID()));
        assertFalse(shop.needsShopMenu(), "item trades keep the vanilla trading screen");
    }

    private static Shop rotatingShop(int trades, int slots, int minutes) {
        return new Shop("market", "Market", TestItems.offers(trades), new ShopAssortment(slots, minutes));
    }

    private static Set<String> ids(List<ShopOffer> offers) {
        Set<String> ids = new HashSet<>();
        offers.forEach(offer -> ids.add(offer.id()));
        return ids;
    }
}
