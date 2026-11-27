package dev.blockfolk.runtime;

import dev.blockfolk.model.ShopOffer;

/**
 * Executes one trade so that it either happens completely or not at all. Items
 * are checked and swapped on a copy of the inventory before any money moves; if
 * the payout fails after the swap, the inventory is restored and the price
 * refunded.
 */
public final class ShopTrade {

    public enum Result {
        DONE, SOLD_OUT, MISSING_ITEMS, NOT_ENOUGH_MONEY, NO_ROOM, NO_ECONOMY, PAYMENT_FAILED, UNAVAILABLE
    }

    /** The player's items for one trade. */
    public interface Goods {
        /**
         * Removes the costs from and adds the result to a copy of the inventory.
         * Returns {@link Result#DONE}, {@link Result#MISSING_ITEMS}, or
         * {@link Result#NO_ROOM}; the real inventory is not touched yet.
         */
        Result prepare(ShopOffer offer);

        /** Writes the prepared inventory. */
        void commit();

        /** Writes the inventory as it was before {@link #prepare}. */
        void restore();
    }

    /** The player's money for one trade. */
    public interface Wallet {
        boolean available();

        boolean has(double amount);

        boolean withdraw(double amount);

        boolean deposit(double amount);
    }

    /** Called when a refund after a failed payout was also refused. */
    public interface RefundFailure {
        void report(double amount);
    }

    private ShopTrade() {
    }

    public static Result execute(ShopOffer offer, Goods goods, Wallet wallet, RefundFailure refundFailure) {
        if (!offer.isValid())
            return Result.UNAVAILABLE;
        if (offer.terms().usesMoney() && !wallet.available())
            return Result.NO_ECONOMY;
        Result prepared = goods.prepare(offer);
        if (prepared != Result.DONE)
            return prepared;
        double price = offer.terms().price();
        if (price > 0) {
            if (!wallet.has(price))
                return Result.NOT_ENOUGH_MONEY;
            if (!wallet.withdraw(price))
                return Result.PAYMENT_FAILED;
        }
        goods.commit();
        double payout = offer.terms().payout();
        if (payout > 0 && !wallet.deposit(payout)) {
            goods.restore();
            if (price > 0 && !wallet.deposit(price))
                refundFailure.report(price);
            return Result.PAYMENT_FAILED;
        }
        return Result.DONE;
    }
}
