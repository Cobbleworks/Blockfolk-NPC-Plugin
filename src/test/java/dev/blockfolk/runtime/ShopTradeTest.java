package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import dev.blockfolk.model.OfferAvailability;
import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.testsupport.TestItems;

class ShopTradeTest {
    private final ShopTrade.Goods goods = mock(ShopTrade.Goods.class);
    private final ShopTrade.Wallet wallet = mock(ShopTrade.Wallet.class);
    private final ShopTrade.RefundFailure refundFailure = mock(ShopTrade.RefundFailure.class);

    @BeforeEach
    void setUp() {
        when(wallet.available()).thenReturn(true);
        when(wallet.has(anyDouble())).thenReturn(true);
        when(wallet.withdraw(anyDouble())).thenReturn(true);
        when(wallet.deposit(anyDouble())).thenReturn(true);
        when(goods.prepare(org.mockito.ArgumentMatchers.any())).thenReturn(ShopTrade.Result.DONE);
    }

    @Test
    void purchaseChecksItemsBeforeTakingMoneyAndSwapsItemsAfter() {
        ShopOffer offer = offer(OfferTerms.DEFAULT.withPrice(120));

        assertEquals(ShopTrade.Result.DONE, ShopTrade.execute(offer, goods, wallet, refundFailure));

        InOrder order = inOrder(goods, wallet);
        order.verify(goods).prepare(offer);
        order.verify(wallet).withdraw(120);
        order.verify(goods).commit();
        verify(wallet, never()).deposit(anyDouble());
    }

    @Test
    void missingItemsOrRoomStopBeforeAnyMoneyMoves() {
        ShopOffer offer = offer(OfferTerms.DEFAULT.withPrice(5));
        when(goods.prepare(offer)).thenReturn(ShopTrade.Result.NO_ROOM);

        assertEquals(ShopTrade.Result.NO_ROOM, ShopTrade.execute(offer, goods, wallet, refundFailure));

        verify(wallet, never()).withdraw(anyDouble());
        verify(goods, never()).commit();
    }

    @Test
    void playersWhoCannotAffordATradeKeepTheirItems() {
        ShopOffer offer = offer(OfferTerms.DEFAULT.withPrice(500));
        when(wallet.has(500)).thenReturn(false);

        assertEquals(ShopTrade.Result.NOT_ENOUGH_MONEY, ShopTrade.execute(offer, goods, wallet, refundFailure));

        verify(goods, never()).commit();
        verify(wallet, never()).withdraw(anyDouble());
    }

    @Test
    void refusedWithdrawalLeavesTheInventoryUntouched() {
        ShopOffer offer = offer(OfferTerms.DEFAULT.withPrice(10));
        when(wallet.withdraw(10)).thenReturn(false);

        assertEquals(ShopTrade.Result.PAYMENT_FAILED, ShopTrade.execute(offer, goods, wallet, refundFailure));

        verify(goods, never()).commit();
    }

    @Test
    void refusedPayoutRestoresItemsAndRefundsThePrice() {
        ShopOffer offer = offer(OfferTerms.DEFAULT.withPrice(3).withPayout(50));
        when(wallet.deposit(50)).thenReturn(false);

        assertEquals(ShopTrade.Result.PAYMENT_FAILED, ShopTrade.execute(offer, goods, wallet, refundFailure));

        InOrder order = inOrder(goods, wallet);
        order.verify(goods).commit();
        order.verify(wallet).deposit(50);
        order.verify(goods).restore();
        order.verify(wallet).deposit(3);
        verifyNoInteractions(refundFailure);
    }

    @Test
    void aRefundThatFailsTooIsReported() {
        ShopOffer offer = offer(OfferTerms.DEFAULT.withPrice(3).withPayout(50));
        when(wallet.deposit(anyDouble())).thenReturn(false);

        ShopTrade.execute(offer, goods, wallet, refundFailure);

        verify(refundFailure).report(3);
    }

    @Test
    void sellingPaysOutAfterTakingTheItems() {
        ShopOffer offer = new ShopOffer(null, TestItems.item(), null, null, OfferTerms.DEFAULT.withPayout(5));

        assertEquals(ShopTrade.Result.DONE, ShopTrade.execute(offer, goods, wallet, refundFailure));

        InOrder order = inOrder(goods, wallet);
        order.verify(goods).commit();
        order.verify(wallet).deposit(5);
    }

    @Test
    void moneyTradesNeedAnEconomy() {
        when(wallet.available()).thenReturn(false);

        assertEquals(ShopTrade.Result.NO_ECONOMY,
                ShopTrade.execute(offer(OfferTerms.DEFAULT.withPrice(1)), goods, wallet, refundFailure));
        verifyNoInteractions(goods);
    }

    @Test
    void itemTradesWorkWithoutAnEconomy() {
        when(wallet.available()).thenReturn(false);

        assertEquals(ShopTrade.Result.DONE, ShopTrade.execute(offer(OfferTerms.DEFAULT), goods, wallet, refundFailure));
    }

    @Test
    void tradesThatAreNotOfferedCannotBeMade() {
        ShopOffer hidden = offer(OfferTerms.DEFAULT.withAvailability(OfferAvailability.HIDDEN));

        assertEquals(ShopTrade.Result.UNAVAILABLE, ShopTrade.execute(hidden, goods, wallet, refundFailure));
        verifyNoInteractions(goods);
    }

    private static ShopOffer offer(OfferTerms terms) {
        return new ShopOffer(null, TestItems.item(), null, TestItems.item(), terms);
    }
}
