package dev.blockfolk.runtime;

import java.util.List;

import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;

/**
 * Pure rules for daily shops: which Minecraft day it is, which page is on sale,
 * and which trades a shop shows.
 */
public final class ShopDays {

    private ShopDays() {
    }

    /** The day number of a world's full time; a new day starts at sunrise. */
    public static long day(long fullTime) {
        return Math.floorDiv(fullTime, DayPeriod.TICKS_PER_DAY);
    }

    /** Ticks until the next sunrise, when the next day starts. */
    public static long ticksUntilNextDay(long fullTime) {
        return DayPeriod.TICKS_PER_DAY - Math.floorMod(fullTime, DayPeriod.TICKS_PER_DAY);
    }

    /**
     * The page that comes {@code steps} pages after {@code current} among
     * {@code pages} (the pages holding trades, in order), wrapping around. When
     * {@code current} holds no trades, one step reaches the next page that does.
     */
    public static int pageAfter(List<Integer> pages, int current, long steps) {
        if (pages.isEmpty())
            return current;
        int size = pages.size();
        int position = pages.indexOf(current);
        if (position < 0)
            position = (int) pages.stream().filter(page -> page < current).count() - 1;
        return pages.get((int) Math.floorMod(position + Math.floorMod(steps, (long) size), (long) size));
    }

    /**
     * The page on sale when {@code stored} was the last one shown: {@code stored}
     * itself while it holds trades, otherwise the next page that does.
     */
    public static int pageOnSale(List<Integer> pages, int stored) {
        return pages.contains(stored) ? stored : pageAfter(pages, stored, 1);
    }

    /**
     * The trades players see, in editor order: every complete trade, or only those
     * on {@code page} in a one-page-per-day shop.
     */
    public static List<ShopOffer> visible(Shop shop, int page) {
        return shop.onePagePerDay() ? shop.validOffersOn(page) : shop.validOffers();
    }
}
