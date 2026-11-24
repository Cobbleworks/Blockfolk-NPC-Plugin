package dev.blockfolk.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What changes while a shop is used, stored apart from its definition: when the
 * current stock period began, which rotating trades were drawn for it, and how
 * often each limited trade was bought since.
 */
public final class ShopState {
    private long periodStart;
    private List<String> picks;
    private final Map<String, Integer> sold;
    private final Map<String, Map<UUID, Integer>> soldTo;

    public ShopState(long periodStart, List<String> picks, Map<String, Integer> sold,
            Map<String, Map<UUID, Integer>> soldTo) {
        this.periodStart = periodStart;
        this.picks = List.copyOf(picks);
        this.sold = new LinkedHashMap<>(sold);
        this.soldTo = new LinkedHashMap<>();
        soldTo.forEach((offer, buyers) -> this.soldTo.put(offer, new HashMap<>(buyers)));
    }

    /** A state whose first period starts at {@code now} without any picks. */
    public static ShopState fresh(long now) {
        return new ShopState(now, List.of(), Map.of(), Map.of());
    }

    public long periodStart() {
        return periodStart;
    }

    public List<String> picks() {
        return picks;
    }

    public void setPicks(List<String> picks) {
        this.picks = List.copyOf(picks);
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

    /** Starts a new period: every limited trade is fully stocked again. */
    public void restock(long periodStart) {
        this.periodStart = periodStart;
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
