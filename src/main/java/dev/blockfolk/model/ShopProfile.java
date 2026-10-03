package dev.blockfolk.model;

import java.util.List;
import java.util.Objects;

/**
 * Per-preset shop configuration. Trades have unlimited stock and are opened
 * through the {@link BehaviourActionType#OPEN_SHOP} action.
 */
public record ShopProfile(boolean enabled, String title, List<ShopOffer> offers) {

    public static final int MAX_TITLE_LENGTH = 48;

    public ShopProfile {
        title = normalizeTitle(title);
        offers = Objects.requireNonNullElse(offers, List.<ShopOffer>of()).stream()
                .filter(offer -> offer != null && !offer.isEmpty()).toList();
    }

    public static ShopProfile disabled() {
        return new ShopProfile(false, null, List.of());
    }

    public ShopProfile withEnabled(boolean enabled) {
        return new ShopProfile(enabled, title, offers);
    }

    public ShopProfile withTitle(String title) {
        return new ShopProfile(enabled, title, offers);
    }

    public ShopProfile withOffers(List<ShopOffer> offers) {
        return new ShopProfile(enabled, title, offers);
    }

    /** Trades that are complete enough to be shown to players. */
    public List<ShopOffer> validOffers() {
        return offers.stream().filter(ShopOffer::isValid).toList();
    }

    /** Whether opening the shop would show at least one trade. */
    public boolean isOpenable() {
        return enabled && offers.stream().anyMatch(ShopOffer::isValid);
    }

    private static String normalizeTitle(String title) {
        if (title == null)
            return null;
        String trimmed = title.trim();
        if (trimmed.isEmpty())
            return null;
        return trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
    }
}
