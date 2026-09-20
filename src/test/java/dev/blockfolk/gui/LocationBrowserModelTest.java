package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.ActionLocation;
import dev.blockfolk.model.NamedLocation;

class LocationBrowserModelTest {

    @Test
    void groupsLocationsBySlashSeparatedNames() {
        NamedLocation spawn = location("Spawn");
        NamedLocation market = location("Town Center/Market Square");
        NamedLocation forge = location("Town Center/Shops/Forge");

        List<LocationBrowserModel.Entry> root = LocationBrowserModel.entries(List.of(spawn, market, forge), "");
        assertEquals(List.of("Town Center", "Spawn"), root.stream().map(LocationBrowserModel.Entry::label).toList());
        assertTrue(root.get(0).folder());
        assertFalse(root.get(1).folder());
        assertEquals(2, root.get(0).childCount());

        List<LocationBrowserModel.Entry> town = LocationBrowserModel.entries(List.of(spawn, market, forge),
                "town-center");
        assertEquals(List.of("Shops", "Market Square"), town.stream().map(LocationBrowserModel.Entry::label).toList());
        assertEquals("town-center/shops", town.get(0).path());
    }

    @Test
    void keepsALocationAndGroupWithTheSameNameAccessible() {
        List<LocationBrowserModel.Entry> root = LocationBrowserModel
                .entries(List.of(location("Town"), location("Town/Market")), "");

        assertEquals(2, root.size());
        assertTrue(root.get(0).folder());
        assertFalse(root.get(1).folder());
    }

    private static NamedLocation location(String name) {
        return NamedLocation.create(name, new ActionLocation("world", 1, 2, 3));
    }
}
