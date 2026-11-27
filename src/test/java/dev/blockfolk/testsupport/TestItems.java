package dev.blockfolk.testsupport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.bukkit.inventory.ItemStack;

import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.ShopOffer;

/** Item stacks and trades for tests that run without a server. */
public final class TestItems {

    private TestItems() {
    }

    /** A distinct, non-empty item stack whose copy is itself. */
    public static ItemStack item() {
        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);
        return item;
    }

    /** An item-for-item trade in editor slot {@code slot}. */
    public static ShopOffer offer(int slot) {
        return new ShopOffer(null, slot, item(), null, item(), OfferTerms.DEFAULT);
    }

    /** {@code count} item-for-item trades in the first {@code count} slots. */
    public static List<ShopOffer> offers(int count) {
        return IntStream.range(0, count).mapToObj(TestItems::offer).toList();
    }
}
