package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A shared shop that any NPC can open through the
 * {@link BehaviourActionType#OPEN_SHOP} action, whose value is the shop key.
 * The name doubles as the shop screen title. Each trade carries its own terms
 * (money, availability, stock); {@code assortment} decides how many offered
 * trades are shown at once and how often the shop restocks.
 */
public record Shop(String key, String name, List<ShopOffer> offers, ShopAssortment assortment) {

    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_KEY_LENGTH = 64;

    public Shop {
        key = normalizeKey(key);
        if (key.isEmpty())
            throw new IllegalArgumentException("A shop needs a key containing letters or numbers.");
        name = normalizeName(name, key);
        offers = uniqueIds(Objects.requireNonNullElse(offers, List.<ShopOffer>of()).stream()
                .filter(offer -> offer != null && !offer.isEmpty()).toList());
        assortment = Objects.requireNonNullElse(assortment, ShopAssortment.DEFAULT);
    }

    public Shop(String key, String name, List<ShopOffer> offers) {
        this(key, name, offers, ShopAssortment.DEFAULT);
    }

    public static Shop create(String key, String name) {
        return new Shop(key, name, List.of());
    }

    public Shop withName(String name) {
        return new Shop(key, name, offers, assortment);
    }

    public Shop withOffers(List<ShopOffer> offers) {
        return new Shop(key, name, offers, assortment);
    }

    public Shop withAssortment(ShopAssortment assortment) {
        return new Shop(key, name, offers, assortment);
    }

    /** Replaces the trade with {@code offer}'s id; unchanged when it is gone. */
    public Shop withOffer(ShopOffer offer) {
        return withOffers(offers.stream().map(current -> current.id().equals(offer.id()) ? offer : current).toList());
    }

    public Shop copyAs(String key, String name) {
        return new Shop(key, name, offers, assortment);
    }

    public Optional<ShopOffer> findOffer(String id) {
        return offers.stream().filter(offer -> offer.id().equals(id)).findFirst();
    }

    /** Trades that are complete, whether offered or not. */
    public List<ShopOffer> validOffers() {
        return offers.stream().filter(ShopOffer::isValid).toList();
    }

    /** Complete trades that players can see when they are in the assortment. */
    public List<ShopOffer> offeredTrades() {
        return offers.stream().filter(ShopOffer::isOffered).toList();
    }

    /** Offered trades that take part in the draw when the shop rotates. */
    public List<ShopOffer> rotationPool() {
        return offers.stream()
                .filter(offer -> offer.isOffered() && offer.terms().availability() == OfferAvailability.OFFERED)
                .toList();
    }

    /**
     * Whether any offered trade involves money or a stock limit, which the vanilla
     * trading screen cannot show.
     */
    public boolean needsShopMenu() {
        return offeredTrades().stream().anyMatch(ShopOffer::needsShopMenu);
    }

    public static String normalizeKey(String value) {
        String key = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        return key.substring(0, Math.min(MAX_KEY_LENGTH, key.length()));
    }

    private static String normalizeName(String name, String key) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty())
            return key;
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    /**
     * Gives a trade a fresh id when an earlier trade already uses its id, for
     * example after an item was copied into a second row.
     */
    private static List<ShopOffer> uniqueIds(List<ShopOffer> offers) {
        Set<String> seen = new HashSet<>();
        List<ShopOffer> unique = new ArrayList<>(offers.size());
        for (ShopOffer offer : offers) {
            ShopOffer kept = offer;
            while (!seen.add(kept.id()))
                kept = new ShopOffer(null, offer.cost(), offer.secondCost(), offer.result(), offer.terms());
            unique.add(kept);
        }
        return List.copyOf(unique);
    }
}
