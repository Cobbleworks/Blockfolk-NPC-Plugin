package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.ShopPageMode;
import dev.blockfolk.model.ShopState;
import dev.blockfolk.model.StockScope;
import dev.blockfolk.repository.ShopRepository;
import dev.blockfolk.repository.ShopStateRepository;
import dev.blockfolk.testsupport.TestItems;

class NpcShopServiceTest {
    private static final long DAY = 24_000L;

    private final ShopRepository shops = mock(ShopRepository.class);
    private final ShopStateRepository states = mock(ShopStateRepository.class);
    private final Map<String, ShopState> stored = new HashMap<>();
    private long fullTime = 10 * DAY + 1_000;
    private boolean timeAdvances = true;
    private NpcShopService service;

    @BeforeEach
    void setUp() {
        when(states.get(anyString(), anyLong())).thenAnswer(
                call -> stored.computeIfAbsent(call.getArgument(0), ignored -> ShopState.fresh(call.getArgument(1))));
        NpcShopService.DayClock clock = new NpcShopService.DayClock() {
            @Override
            public long fullTime() {
                return fullTime;
            }

            @Override
            public boolean advances() {
                return timeAdvances;
            }
        };
        service = new NpcShopService(null, shops, states, ShopEconomy.none(), clock);
    }

    @Test
    void allPagesAreOnSaleByDefault() {
        Shop shop = new Shop("market", "Market", tradesOnPages(0, 1, 3));

        assertEquals(6, service.assortment(shop).offers().size(), "two trades on each of three pages");
    }

    @Test
    void oneShopPagePerDayInOrderSkippingEmptyPages() {
        Shop shop = dailyShop(0, 1, 3);

        assertEquals(List.of(0, 1, 3, 0), pagesOverDays(shop, 4));
    }

    @Test
    void theSamePageStaysOnSaleAllDay() {
        Shop shop = dailyShop(0, 1);
        List<ShopOffer> morning = service.assortment(shop).offers();
        fullTime += 20_000;

        assertEquals(morning, service.assortment(shop).offers());
    }

    @Test
    void daysWithoutVisitorsStillCountSoTheScheduleKeepsItsOrder() {
        Shop shop = dailyShop(0, 1, 2);
        service.assortment(shop);
        fullTime += 2 * DAY;

        assertEquals(2, service.assortment(shop).page());
    }

    @Test
    void settingTheTimeBackStillTurnsOnePage() {
        Shop shop = dailyShop(0, 1, 2);
        service.assortment(shop);
        fullTime = 1_000;

        assertEquals(1, service.assortment(shop).page());
    }

    @Test
    void aPageThatLosesItsTradesHandsOverToTheNextPage() {
        Shop shop = dailyShop(0, 2);
        service.assortment(shop);
        Shop edited = shop.withOffers(shop.offers().stream().filter(offer -> offer.page() != 0).toList());

        assertEquals(2, service.assortment(edited).page());
    }

    @Test
    void sharedStockRunsOutForEveryoneAndRestocksAtSunrise() {
        ShopOffer sword = new ShopOffer(null, TestItems.item(), null, TestItems.item(),
                OfferTerms.DEFAULT.withPrice(100).withStockLimit(2));
        Shop shop = new Shop("armory", "Armory", List.of(sword));
        sword = shop.offers().getFirst();
        UUID alex = UUID.randomUUID();
        UUID sam = UUID.randomUUID();
        service.assortment(shop);
        stored.get("armory").recordSale(sword.id(), StockScope.SHARED, alex, 2);

        assertEquals(0, service.remaining(shop, sword, alex));
        assertEquals(0, service.remaining(shop, sword, sam));
        assertEquals(2, service.soldSinceRestock(shop, sword));

        fullTime += DAY;
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
    void restockNowRefillsAndTurnsToTheNextPage() {
        Shop shop = dailyShop(0, 1);
        ShopOffer sold = service.assortment(shop).offers().getFirst();
        stored.get(shop.key()).recordSale(sold.id(), StockScope.SHARED, UUID.randomUUID(), 1);

        service.restockNow(shop);

        assertEquals(1, service.assortment(shop).page());
        assertEquals(0, stored.get(shop.key()).sold(sold.id(), StockScope.SHARED, UUID.randomUUID()));
    }

    @Test
    void stateFromAnOlderVersionAdoptsTodayWithoutRestocking() {
        Shop shop = dailyShop(0, 1);
        ShopState old = new ShopState(ShopState.UNKNOWN_DAY, 0, Map.of("x", 1), Map.of());
        stored.put(shop.key(), old);
        String first = shop.offers().getFirst().id();
        old.recordSale(first, StockScope.SHARED, UUID.randomUUID(), 1);

        assertEquals(0, service.assortment(shop).page());
        assertEquals(10, old.day());
        assertEquals(1, old.sold(first, StockScope.SHARED, UUID.randomUUID()));
    }

    @Test
    void countdownRunsToSunriseOnlyWhileTimeAdvances() {
        Shop shop = dailyShop(0);

        assertEquals((DAY - 1_000) * 50, service.assortment(shop).millisUntilRestock());

        timeAdvances = false;

        assertFalse(service.assortment(shop).restocks());
    }

    @Test
    void unlimitedTradesNeverRunOut() {
        ShopOffer bread = new ShopOffer(TestItems.item(), null, TestItems.item());
        Shop shop = new Shop("bakery", "Bakery", List.of(bread));

        assertEquals(Integer.MAX_VALUE, service.remaining(shop, bread, UUID.randomUUID()));
        assertFalse(shop.needsShopMenu(), "item trades keep the vanilla trading screen");
        assertTrue(service.assortment(shop).offers().contains(shop.offers().getFirst()));
    }

    private List<Integer> pagesOverDays(Shop shop, int days) {
        List<Integer> pages = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            NpcShopService.Assortment assortment = service.assortment(shop);
            assertTrue(assortment.offers().stream().allMatch(offer -> offer.page() == assortment.page()));
            pages.add(assortment.page());
            fullTime += DAY;
        }
        return pages;
    }

    private static Shop dailyShop(int... pages) {
        return new Shop("market", "Market", tradesOnPages(pages), ShopPageMode.ONE_PER_DAY);
    }

    /** Two trades on each of {@code pages}. */
    private static List<ShopOffer> tradesOnPages(int... pages) {
        List<ShopOffer> offers = new ArrayList<>();
        for (int page : pages) {
            offers.add(TestItems.offer(page * Shop.TRADES_PER_PAGE));
            offers.add(TestItems.offer(page * Shop.TRADES_PER_PAGE + 3));
        }
        return offers;
    }
}
