package dev.blockfolk.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Everything about a trade besides its items: money the player pays
 * ({@code price}) and receives ({@code payout}), and how often it can be bought
 * before the shop restocks at the next sunrise.
 *
 * @param price
 *            money the player pays per trade, 0 for none
 * @param payout
 *            money the player receives per trade, 0 for none
 * @param stockLimit
 *            trades available until the next restock, 0 for unlimited
 * @param stockScope
 *            whether the limit is shared or counted per player
 */
public record OfferTerms(double price, double payout, int stockLimit, StockScope stockScope) {

    public static final double MAX_MONEY = 1_000_000_000d;
    public static final int MAX_STOCK = 10_000;
    public static final OfferTerms DEFAULT = new OfferTerms(0, 0, 0, StockScope.SHARED);

    public OfferTerms {
        price = money(price);
        payout = money(payout);
        stockLimit = Math.clamp(stockLimit, 0, MAX_STOCK);
        stockScope = Objects.requireNonNullElse(stockScope, StockScope.SHARED);
    }

    /**
     * Rounds to cents and keeps the amount between 0 and {@link #MAX_MONEY};
     * invalid numbers become 0.
     */
    public static double money(double amount) {
        if (!Double.isFinite(amount) || amount <= 0)
            return 0;
        return BigDecimal.valueOf(Math.min(amount, MAX_MONEY)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public boolean usesMoney() {
        return price > 0 || payout > 0;
    }

    public boolean limited() {
        return stockLimit > 0;
    }

    public OfferTerms withPrice(double price) {
        return new OfferTerms(price, payout, stockLimit, stockScope);
    }

    public OfferTerms withPayout(double payout) {
        return new OfferTerms(price, payout, stockLimit, stockScope);
    }

    public OfferTerms withStockLimit(int stockLimit) {
        return new OfferTerms(price, payout, stockLimit, stockScope);
    }

    public OfferTerms withStockScope(StockScope stockScope) {
        return new OfferTerms(price, payout, stockLimit, stockScope);
    }
}
