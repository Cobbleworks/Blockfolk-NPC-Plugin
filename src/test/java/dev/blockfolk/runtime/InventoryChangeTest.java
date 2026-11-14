package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.runtime.InventoryChange.Cause;
import dev.blockfolk.runtime.InventoryChange.Moved;

class InventoryChangeTest {

    @Test
    void mergesStacksOfTheSameKind() {
        assertEquals("70 cobblestone, 3 iron ingot", InventoryChange
                .join(List.of(new Moved("cobblestone", 64), new Moved("iron ingot", 3), new Moved("cobblestone", 6))));
    }

    @Test
    void describesHowItemsArrived() {
        List<Moved> items = List.of(new Moved("iron ingot", 3));
        assertEquals("You picked up 3 iron ingot from the ground.",
                InventoryChange.describeReceived(Cause.PICKED_UP, null, items));
        assertEquals("Alex put 3 iron ingot into your inventory.",
                InventoryChange.describeReceived(Cause.EDITED_BY_PLAYER, "Alex", items));
        assertEquals("You took 3 iron ingot from a chest.",
                InventoryChange.describeReceived(Cause.TAKEN, "a chest", items));
        assertEquals("You received 3 iron ingot.", InventoryChange.describeReceived(Cause.OTHER, null, items));
    }

    @Test
    void describesHowItemsLeft() {
        List<Moved> items = List.of(new Moved("bread", 2));
        assertEquals("You dropped 2 bread on the ground.", InventoryChange.describeLost(Cause.DROPPED, null, items));
        assertEquals("You stored 2 bread in a barrel.", InventoryChange.describeLost(Cause.STORED, "a barrel", items));
        assertEquals("Alex took 2 bread out of your inventory.",
                InventoryChange.describeLost(Cause.EDITED_BY_PLAYER, "Alex", items));
        assertEquals("You planted 2 bread.", InventoryChange.describeLost(Cause.FARMED, null, items));
    }

    @Test
    void nothingMovedHasNoDescription() {
        assertNull(InventoryChange.describeReceived(Cause.PICKED_UP, null, List.of()));
        assertNull(InventoryChange.describeLost(Cause.DROPPED, null, List.of(new Moved("bread", 0))));
    }
}
