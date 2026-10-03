package dev.blockfolk.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A shared shop that any NPC can open through the
 * {@link BehaviourActionType#OPEN_SHOP} action, whose value is the shop key.
 * The name doubles as the trading screen title. Trades have unlimited stock.
 */
public record Shop(String key, String name, List<ShopOffer> offers) {

    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_KEY_LENGTH = 64;

    public Shop {
        key = normalizeKey(key);
        if (key.isEmpty())
            throw new IllegalArgumentException("A shop needs a key containing letters or numbers.");
        name = normalizeName(name, key);
        offers = Objects.requireNonNullElse(offers, List.<ShopOffer>of()).stream()
                .filter(offer -> offer != null && !offer.isEmpty()).toList();
    }

    public static Shop create(String key, String name) {
        return new Shop(key, name, List.of());
    }

    public Shop withName(String name) {
        return new Shop(key, name, offers);
    }

    public Shop withOffers(List<ShopOffer> offers) {
        return new Shop(key, name, offers);
    }

    public Shop copyAs(String key, String name) {
        return new Shop(key, name, offers);
    }

    /** Trades that are complete enough to be shown to players. */
    public List<ShopOffer> validOffers() {
        return offers.stream().filter(ShopOffer::isValid).toList();
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
}
