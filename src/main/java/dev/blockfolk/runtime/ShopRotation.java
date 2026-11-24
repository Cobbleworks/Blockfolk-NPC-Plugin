package dev.blockfolk.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import dev.blockfolk.model.OfferAvailability;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;

/**
 * Pure rules for rotating assortments and stock periods: when a period ends,
 * which trades are drawn, and which trades a shop shows.
 */
public final class ShopRotation {

    private ShopRotation() {
    }

    /**
     * The start of the period that contains {@code now}. Periods follow each other
     * at a fixed interval from {@code periodStart}, so a restart or a quiet shop
     * does not shift the schedule. Returns {@code periodStart} while the current
     * period lasts or when the shop never refreshes.
     */
    public static long currentPeriodStart(long periodStart, long intervalMillis, long now) {
        if (intervalMillis <= 0 || now < periodStart + intervalMillis)
            return periodStart;
        return periodStart + (now - periodStart) / intervalMillis * intervalMillis;
    }

    /**
     * Draws {@code slots} trade ids from {@code pool}, each with a chance
     * proportional to its weight. Trades that were not in {@code previous} are
     * preferred, so a new rotation shows new trades whenever the pool allows it.
     */
    public static List<String> draw(List<ShopOffer> pool, int slots, Set<String> previous, RandomGenerator random) {
        List<ShopOffer> fresh = pool.stream().filter(offer -> !previous.contains(offer.id())).toList();
        List<String> picks = new ArrayList<>(sample(fresh, slots, random));
        if (picks.size() < slots) {
            Set<String> taken = new HashSet<>(picks);
            List<ShopOffer> rest = pool.stream().filter(offer -> !taken.contains(offer.id())).toList();
            picks.addAll(sample(rest, slots - picks.size(), random));
        }
        return picks;
    }

    /**
     * Keeps the picks still in the pool (up to {@code slots}) and draws more when
     * trades were removed or the number of slots grew, so edits never leave the
     * assortment short.
     */
    public static List<String> reconcile(List<String> picks, List<ShopOffer> pool, int slots, RandomGenerator random) {
        Set<String> poolIds = new HashSet<>();
        pool.forEach(offer -> poolIds.add(offer.id()));
        List<String> kept = new ArrayList<>(
                picks.stream().filter(poolIds::contains).distinct().limit(Math.max(0, slots)).toList());
        if (kept.size() < slots) {
            Set<String> taken = new HashSet<>(kept);
            List<ShopOffer> rest = pool.stream().filter(offer -> !taken.contains(offer.id())).toList();
            kept.addAll(sample(rest, slots - kept.size(), random));
        }
        return kept;
    }

    /**
     * The trades players see, in editor order: every offered trade when the shop
     * does not rotate, otherwise the always-offered trades plus the picks.
     */
    public static List<ShopOffer> visible(Shop shop, List<String> picks) {
        if (!shop.assortment().rotates())
            return shop.offeredTrades();
        Set<String> picked = new HashSet<>(picks);
        return shop.offeredTrades().stream().filter(
                offer -> offer.terms().availability() == OfferAvailability.ALWAYS || picked.contains(offer.id()))
                .toList();
    }

    /**
     * Weighted sampling without replacement (Efraimidis and Spirakis): every trade
     * gets the key {@code u^(1/weight)} and the largest keys win.
     */
    static List<String> sample(List<ShopOffer> candidates, int count, RandomGenerator random) {
        if (count <= 0 || candidates.isEmpty())
            return List.of();
        record Keyed(String id, double key) {
        }
        return candidates.stream()
                .map(offer -> new Keyed(offer.id(),
                        Math.pow(random.nextDouble(Double.MIN_VALUE, 1.0), 1.0 / offer.terms().weight())))
                .sorted(Comparator.comparingDouble(Keyed::key).reversed()).limit(count).map(Keyed::id).toList();
    }
}
