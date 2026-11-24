package dev.blockfolk.model;

/**
 * How a shop changes over time.
 *
 * @param rotatingSlots
 *            how many {@link OfferAvailability#OFFERED} trades are drawn into
 *            the assortment; 0 offers all of them, so the shop does not rotate
 * @param refreshMinutes
 *            minutes between restocks; each restock also draws a new rotation.
 *            0 never restocks on its own
 */
public record ShopAssortment(int rotatingSlots, int refreshMinutes) {

    public static final int MAX_ROTATING_SLOTS = 50;
    /** Thirty days. */
    public static final int MAX_REFRESH_MINUTES = 30 * 24 * 60;
    public static final ShopAssortment DEFAULT = new ShopAssortment(0, 0);

    public ShopAssortment {
        rotatingSlots = Math.clamp(rotatingSlots, 0, MAX_ROTATING_SLOTS);
        refreshMinutes = Math.clamp(refreshMinutes, 0, MAX_REFRESH_MINUTES);
    }

    public boolean rotates() {
        return rotatingSlots > 0;
    }

    public boolean refreshes() {
        return refreshMinutes > 0;
    }

    public long refreshMillis() {
        return refreshMinutes * 60_000L;
    }

    public ShopAssortment withRotatingSlots(int rotatingSlots) {
        return new ShopAssortment(rotatingSlots, refreshMinutes);
    }

    public ShopAssortment withRefreshMinutes(int refreshMinutes) {
        return new ShopAssortment(rotatingSlots, refreshMinutes);
    }
}
