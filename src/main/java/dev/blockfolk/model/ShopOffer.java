package dev.blockfolk.model;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

/**
 * One trade: the player pays {@code cost}, an optional {@code secondCost} and
 * the terms' price, and receives {@code result} and the terms' payout. Items
 * are stored as complete stacks, so renamed, enchanted, or otherwise custom
 * items trade exactly as placed. Empty item stacks are normalised to
 * {@code null}; a second cost without a first cost moves into the first slot.
 * The {@code id} stays with the trade while it is edited, so stock counts and
 * rotation picks survive changes to its items.
 */
public record ShopOffer(String id, ItemStack cost, ItemStack secondCost, ItemStack result, OfferTerms terms) {

    private static final int ID_LENGTH = 8;

    public ShopOffer {
        id = isValidId(id) ? id : newId();
        cost = copy(cost);
        secondCost = copy(secondCost);
        result = copy(result);
        if (cost == null) {
            cost = secondCost;
            secondCost = null;
        }
        terms = Objects.requireNonNullElse(terms, OfferTerms.DEFAULT);
    }

    /** A new trade with a fresh id and default terms. */
    public ShopOffer(ItemStack cost, ItemStack secondCost, ItemStack result) {
        this(null, cost, secondCost, result, OfferTerms.DEFAULT);
    }

    @Override
    public ItemStack cost() {
        return copy(cost);
    }

    @Override
    public ItemStack secondCost() {
        return copy(secondCost);
    }

    @Override
    public ItemStack result() {
        return copy(result);
    }

    public ShopOffer withItems(ItemStack cost, ItemStack secondCost, ItemStack result) {
        return new ShopOffer(id, cost, secondCost, result, terms);
    }

    public ShopOffer withTerms(OfferTerms terms) {
        return new ShopOffer(id, cost, secondCost, result, terms);
    }

    /**
     * A trade can be offered once the player gives something (an item or money) and
     * gets something (an item or money).
     */
    public boolean isValid() {
        return (cost != null || terms.price() > 0) && (result != null || terms.payout() > 0);
    }

    /** No items at all; such trades are dropped when the shop is saved. */
    public boolean isEmpty() {
        return cost == null && secondCost == null && result == null;
    }

    /** Valid and not switched off. */
    public boolean isOffered() {
        return isValid() && terms.availability() != OfferAvailability.HIDDEN;
    }

    /** Needs the Blockfolk shop menu: money or a stock limit is involved. */
    public boolean needsShopMenu() {
        return terms.usesMoney() || terms.limited();
    }

    public static boolean isValidId(String id) {
        return id != null && !id.isBlank() && id.length() <= 32 && id.matches("[A-Za-z0-9_-]+");
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, ID_LENGTH);
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }
}
