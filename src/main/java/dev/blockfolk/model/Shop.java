package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A shared shop that any NPC can open through the
 * {@link BehaviourActionType#OPEN_SHOP} action, whose value is the shop key.
 * The name doubles as the shop screen title. Trades sit on up to
 * {@value #MAX_PAGES} editor pages of {@value #TRADES_PER_PAGE};
 * {@code pageMode} decides whether all pages are on sale or one per Minecraft
 * day. Each trade carries its own terms (money, stock).
 */
public record Shop(String key, String name, List<ShopOffer> offers, ShopPageMode pageMode) {

    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_KEY_LENGTH = 64;
    public static final int TRADES_PER_PAGE = 10;
    public static final int MAX_PAGES = 5;
    public static final int MAX_TRADES = TRADES_PER_PAGE * MAX_PAGES;

    public Shop {
        key = normalizeKey(key);
        if (key.isEmpty())
            throw new IllegalArgumentException("A shop needs a key containing letters or numbers.");
        name = normalizeName(name, key);
        offers = place(uniqueIds(Objects.requireNonNullElse(offers, List.<ShopOffer>of()).stream()
                .filter(offer -> offer != null && !offer.isEmpty()).toList()));
        pageMode = Objects.requireNonNullElse(pageMode, ShopPageMode.ALL_PAGES);
    }

    public Shop(String key, String name, List<ShopOffer> offers) {
        this(key, name, offers, ShopPageMode.ALL_PAGES);
    }

    public static Shop create(String key, String name) {
        return new Shop(key, name, List.of());
    }

    public Shop withName(String name) {
        return new Shop(key, name, offers, pageMode);
    }

    public Shop withOffers(List<ShopOffer> offers) {
        return new Shop(key, name, offers, pageMode);
    }

    public Shop withPageMode(ShopPageMode pageMode) {
        return new Shop(key, name, offers, pageMode);
    }

    /** Replaces the trade with {@code offer}'s id; unchanged when it is gone. */
    public Shop withOffer(ShopOffer offer) {
        return withOffers(offers.stream().map(current -> current.id().equals(offer.id()) ? offer : current).toList());
    }

    /**
     * Replaces every trade on {@code page} with {@code rows}, whose slots must lie
     * on that page. Trades on other pages stay where they are.
     */
    public Shop withPage(int page, List<ShopOffer> rows) {
        List<ShopOffer> updated = new ArrayList<>(offers.stream().filter(offer -> offer.page() != page).toList());
        updated.addAll(rows);
        return withOffers(updated);
    }

    public Shop copyAs(String key, String name) {
        return new Shop(key, name, offers, pageMode);
    }

    public Optional<ShopOffer> findOffer(String id) {
        return offers.stream().filter(offer -> offer.id().equals(id)).findFirst();
    }

    /** The trade in editor slot {@code slot}, if any. */
    public Optional<ShopOffer> offerAt(int slot) {
        return offers.stream().filter(offer -> offer.slot() == slot).findFirst();
    }

    /** Complete trades, which players can see when their page is on sale. */
    public List<ShopOffer> validOffers() {
        return offers.stream().filter(ShopOffer::isValid).toList();
    }

    /** Complete trades on {@code page}, in editor order. */
    public List<ShopOffer> validOffersOn(int page) {
        return offers.stream().filter(offer -> offer.page() == page && offer.isValid()).toList();
    }

    /** Pages holding at least one complete trade, in order. */
    public List<Integer> pagesWithTrades() {
        return validOffers().stream().map(ShopOffer::page).distinct().sorted().toList();
    }

    public boolean onePagePerDay() {
        return pageMode == ShopPageMode.ONE_PER_DAY;
    }

    /**
     * Whether players notice the new day: limited trades refill, or a daily shop
     * turns to another page.
     */
    public boolean changesAtSunrise() {
        return validOffers().stream().anyMatch(offer -> offer.terms().limited())
                || onePagePerDay() && pagesWithTrades().size() > 1;
    }

    /**
     * Whether any trade involves money or a stock limit, which the vanilla trading
     * screen cannot show.
     */
    public boolean needsShopMenu() {
        return validOffers().stream().anyMatch(ShopOffer::needsShopMenu);
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
                kept = new ShopOffer(null, offer.slot(), offer.cost(), offer.secondCost(), offer.result(),
                        offer.terms());
            unique.add(kept);
        }
        return unique;
    }

    /**
     * Keeps every trade in its own slot and sorts them by slot. Trades without a
     * slot, or whose slot is already taken, move into the first free one, in list
     * order; trades beyond {@link #MAX_TRADES} are dropped.
     */
    private static List<ShopOffer> place(List<ShopOffer> offers) {
        boolean[] taken = new boolean[MAX_TRADES];
        List<ShopOffer> placed = new ArrayList<>(offers.size());
        List<ShopOffer> homeless = new ArrayList<>();
        for (ShopOffer offer : offers) {
            if (offer.slot() != ShopOffer.UNPLACED && !taken[offer.slot()]) {
                taken[offer.slot()] = true;
                placed.add(offer);
            } else {
                homeless.add(offer);
            }
        }
        int free = 0;
        for (ShopOffer offer : homeless) {
            while (free < MAX_TRADES && taken[free])
                free++;
            if (free == MAX_TRADES)
                break;
            taken[free] = true;
            placed.add(offer.withSlot(free));
        }
        placed.sort(Comparator.comparingInt(ShopOffer::slot));
        return List.copyOf(placed);
    }
}
