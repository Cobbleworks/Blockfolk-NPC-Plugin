package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.OfferAvailability;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopAssortment;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.testsupport.TestItems;

class ShopRotationTest {
    private static final long HOUR = 3_600_000L;

    @Test
    void periodLastsUntilTheIntervalPassesAndKeepsItsSchedule() {
        assertEquals(1_000, ShopRotation.currentPeriodStart(1_000, HOUR, 1_000 + HOUR - 1));
        assertEquals(1_000 + HOUR, ShopRotation.currentPeriodStart(1_000, HOUR, 1_000 + HOUR));
        // Three and a half hours later (for example after downtime), the period that
        // started three intervals in is current, not one starting "now".
        assertEquals(1_000 + 3 * HOUR, ShopRotation.currentPeriodStart(1_000, HOUR, 1_000 + 3 * HOUR + HOUR / 2));
    }

    @Test
    void shopsWithoutIntervalNeverStartANewPeriod() {
        assertEquals(1_000, ShopRotation.currentPeriodStart(1_000, 0, Long.MAX_VALUE / 2));
    }

    @Test
    void drawPicksTheRequestedNumberWithoutDuplicates() {
        List<ShopOffer> pool = TestItems.offers(30);

        List<String> picks = ShopRotation.draw(pool, 10, Set.of(), new Random(1));

        assertEquals(10, picks.size());
        assertEquals(10, new HashSet<>(picks).size());
    }

    @Test
    void drawPrefersTradesThatWereNotOnSaleLastTime() {
        List<ShopOffer> pool = TestItems.offers(20);
        Set<String> previous = new HashSet<>(ShopRotation.draw(pool, 10, Set.of(), new Random(2)));

        List<String> next = ShopRotation.draw(pool, 10, previous, new Random(3));

        assertTrue(next.stream().noneMatch(previous::contains), "20 trades allow ten entirely new ones");
    }

    @Test
    void drawRepeatsTradesOnlyWhenThePoolIsTooSmall() {
        List<ShopOffer> pool = TestItems.offers(12);
        Set<String> previous = new HashSet<>(ShopRotation.draw(pool, 10, Set.of(), new Random(4)));

        List<String> next = ShopRotation.draw(pool, 10, previous, new Random(5));

        assertEquals(10, next.size());
        assertEquals(2, next.stream().filter(id -> !previous.contains(id)).count(), "both unused trades come first");
    }

    @Test
    void heavierTradesAreDrawnMoreOften() {
        ShopOffer common = TestItems.offer(OfferAvailability.OFFERED, 40);
        ShopOffer rare = TestItems.offer(OfferAvailability.OFFERED, 1);
        List<ShopOffer> pool = List.of(common, rare);
        Map<String, Integer> wins = new HashMap<>();
        Random random = new Random(6);
        for (int round = 0; round < 2_000; round++)
            wins.merge(ShopRotation.draw(pool, 1, Set.of(), random).getFirst(), 1, Integer::sum);

        assertTrue(wins.getOrDefault(common.id(), 0) > 1_800, wins.toString());
        assertTrue(wins.getOrDefault(rare.id(), 0) > 0, "a rare trade still comes up sometimes");
    }

    @Test
    void reconcileKeepsPicksDropsRemovedTradesAndTopsUp() {
        List<ShopOffer> pool = TestItems.offers(6);
        List<String> picks = List.of(pool.get(0).id(), "removed", pool.get(1).id());

        List<String> reconciled = ShopRotation.reconcile(picks, pool, 3, new Random(7));

        assertEquals(3, reconciled.size());
        assertEquals(pool.get(0).id(), reconciled.get(0));
        assertEquals(pool.get(1).id(), reconciled.get(1));
        assertTrue(!reconciled.contains("removed"));
    }

    @Test
    void reconcileTrimsWhenFewerTradesRotate() {
        List<ShopOffer> pool = TestItems.offers(6);
        List<String> picks = pool.stream().limit(5).map(ShopOffer::id).toList();

        assertEquals(picks.subList(0, 2), ShopRotation.reconcile(picks, pool, 2, new Random(8)));
    }

    @Test
    void rotatingShopShowsAlwaysOfferedTradesAndPicksInEditorOrder() {
        ShopOffer first = TestItems.offer(OfferAvailability.OFFERED, 10);
        ShopOffer always = TestItems.offer(OfferAvailability.ALWAYS, 10);
        ShopOffer hidden = TestItems.offer(OfferAvailability.HIDDEN, 10);
        ShopOffer last = TestItems.offer(OfferAvailability.OFFERED, 10);
        Shop shop = new Shop("smithy", "Smithy", List.of(first, always, hidden, last), new ShopAssortment(1, 60));

        assertEquals(List.of(always, last), ShopRotation.visible(shop, List.of(last.id())));
        assertEquals(List.of(first, last), shop.rotationPool());
    }

    @Test
    void shopWithoutRotationShowsEveryOfferedTrade() {
        ShopOffer offered = TestItems.offer(OfferAvailability.OFFERED, 10);
        ShopOffer always = TestItems.offer(OfferAvailability.ALWAYS, 10);
        ShopOffer hidden = TestItems.offer(OfferAvailability.HIDDEN, 10);
        Shop shop = new Shop("smithy", "Smithy", List.of(offered, always, hidden));

        assertEquals(List.of(offered, always), ShopRotation.visible(shop, List.of()));
    }
}
