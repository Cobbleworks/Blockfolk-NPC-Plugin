package dev.blockfolk.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What changes while a shop is used, stored apart from its definition: the
 * Minecraft day the current stock belongs to, which page is on sale in a
 * one-page-per-day shop, and how often each limited trade was bought since the
 * last restock.
 */
public final class ShopState {
    /** The day is not known yet, for example in a state from an older version. */
    public static final long UNKNOWN_DAY = Long.MIN_VALUE;

    private long day;
    private int page;
    private final Map<String, Integer> sold;
    private final Map<String, Map<UUID, Integer>> soldTo;

    public ShopState(long day, int page, Map<String, Integer> sold, Map<String, Map<UUID, Integer>> soldTo) {
        this.day = day;
        this.page = Math.max(0, page);
        this.sold = new LinkedHashMap<>(sold);
        this.soldTo = new LinkedHashMap<>();
        soldTo.forEach((offer, buyers) -> this.soldTo.put(offer, new HashMap<>(buyers)));
    }

    /** A state for {@code day} that starts on the first page without sales. */
    public static ShopState fresh(long day) {
        return new ShopState(day, 0, Map.of(), Map.of());
    }

    public long day() {
        return day;
    }

    public boolean knowsDay() {
        return day != UNKNOWN_DAY;
    }

    /** Takes {@code day} as the current day without restocking. */
    public void setDay(long day) {
        this.day = day;
    }

    /** The page on sale in a one-page-per-day shop. */
    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    /** Trades of {@code offerId} counted against its limit for {@code buyer}. */
    public int sold(String offerId, StockScope scope, UUID buyer) {
        if (scope == StockScope.PER_PLAYER)
            return soldTo.getOrDefault(offerId, Map.of()).getOrDefault(buyer, 0);
        return sold.getOrDefault(offerId, 0);
    }

    public void recordSale(String offerId, StockScope scope, UUID buyer, int trades) {
        if (trades <= 0)
            return;
        if (scope == StockScope.PER_PLAYER)
            soldTo.computeIfAbsent(offerId, ignored -> new HashMap<>()).merge(buyer, trades, Integer::sum);
        else
            sold.merge(offerId, trades, Integer::sum);
    }

    /** Starts {@code day}: every limited trade is fully stocked again. */
    public void restock(long day) {
        this.day = day;
        sold.clear();
        soldTo.clear();
    }

    /** Drops counts of trades that no longer exist. */
    public boolean retainOffers(java.util.Set<String> offerIds) {
        boolean changed = sold.keySet().retainAll(offerIds);
        changed |= soldTo.keySet().retainAll(offerIds);
        return changed;
    }

    public Map<String, Integer> sharedSales() {
        return Map.copyOf(sold);
    }

    public Map<String, Map<UUID, Integer>> playerSales() {
        Map<String, Map<UUID, Integer>> copy = new LinkedHashMap<>();
        soldTo.forEach((offer, buyers) -> copy.put(offer, Map.copyOf(buyers)));
        return copy;
    }
}
