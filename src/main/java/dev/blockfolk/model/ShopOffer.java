package dev.blockfolk.model;

import org.bukkit.inventory.ItemStack;

/**
 * One villager-style trade: the player pays {@code cost} (and optionally
 * {@code secondCost}) and receives {@code result}. Empty item stacks are
 * normalised to {@code null}.
 */
public record ShopOffer(ItemStack cost, ItemStack secondCost, ItemStack result) {

    public ShopOffer {
        cost = copy(cost);
        secondCost = copy(secondCost);
        result = copy(result);
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

    /** A trade can be offered once it has a first cost and a result. */
    public boolean isValid() {
        return cost != null && result != null;
    }

    public boolean isEmpty() {
        return cost == null && secondCost == null && result == null;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }
}
