package dev.blockfolk.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import dev.blockfolk.model.ShopOffer;

/**
 * The items of one trade, taken from and given to an inventory's storage slots.
 * For a player those are the hotbar and backpack; armor and off-hand are never
 * touched. Costs must match exactly, including names, enchantments, and other
 * item data, like in the vanilla trading screen. The swap is worked out on a
 * scratch copy, so the real inventory only changes on {@link #commit()}.
 */
public final class InventoryTradeGoods implements ShopTrade.Goods {
    private final Inventory inventory;
    private ItemStack[] original;
    private ItemStack[] prepared;

    public InventoryTradeGoods(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public ShopTrade.Result prepare(ShopOffer offer) {
        original = copy(inventory.getStorageContents());
        // Custom inventories come in rows of nine; extra slots stay empty and are
        // cut off again below.
        Inventory scratch = Bukkit.createInventory(null, Math.max(9, (original.length + 8) / 9 * 9));
        scratch.setContents(copy(original));
        List<ItemStack> costs = new ArrayList<>(2);
        if (offer.cost() != null)
            costs.add(offer.cost());
        if (offer.secondCost() != null)
            costs.add(offer.secondCost());
        if (!costs.isEmpty() && !scratch.removeItem(costs.toArray(ItemStack[]::new)).isEmpty())
            return ShopTrade.Result.MISSING_ITEMS;
        if (offer.result() != null && (!scratch.addItem(offer.result()).isEmpty() || spilledPastStorage(scratch)))
            return ShopTrade.Result.NO_ROOM;
        prepared = Arrays.copyOf(scratch.getContents(), original.length);
        return ShopTrade.Result.DONE;
    }

    @Override
    public void commit() {
        inventory.setStorageContents(prepared);
    }

    @Override
    public void restore() {
        inventory.setStorageContents(original);
    }

    /** Whether the result landed in padding slots that the real storage lacks. */
    private boolean spilledPastStorage(Inventory scratch) {
        ItemStack[] contents = scratch.getContents();
        for (int slot = original.length; slot < contents.length; slot++) {
            if (contents[slot] != null && !contents[slot].isEmpty())
                return true;
        }
        return false;
    }

    /** How many items exactly like {@code item} the inventory's storage holds. */
    public static int count(Inventory inventory, ItemStack item) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(item))
                total += stack.getAmount();
        }
        return total;
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++)
            copy[index] = contents[index] == null ? null : contents[index].clone();
        return copy;
    }
}
