package dev.blockfolk.testsupport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.bukkit.inventory.ItemStack;

import dev.blockfolk.model.OfferAvailability;
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

    /** An item-for-item trade with the given availability and draw weight. */
    public static ShopOffer offer(OfferAvailability availability, int weight) {
        return new ShopOffer(null, item(), null, item(),
                OfferTerms.DEFAULT.withAvailability(availability).withWeight(weight));
    }

    /** {@code count} offered item-for-item trades with the default weight. */
    public static List<ShopOffer> offers(int count) {
        return IntStream.range(0, count).mapToObj(ignored -> offer(OfferAvailability.OFFERED, 10)).toList();
    }
}
