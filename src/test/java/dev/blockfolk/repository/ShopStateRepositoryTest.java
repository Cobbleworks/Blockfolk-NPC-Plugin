package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.ShopState;
import dev.blockfolk.model.StockScope;

class ShopStateRepositoryTest {
    @Test
    void periodPicksAndSalesRoundTrip() throws Exception {
        UUID alex = UUID.randomUUID();
        ShopState state = new ShopState(1_764_000_000_000L, List.of("a1", "b2"), Map.of(), Map.of());
        state.recordSale("a1", StockScope.SHARED, alex, 3);
        state.recordSale("b2", StockScope.PER_PLAYER, alex, 1);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(ShopStateRepository.encode(Map.of("market", state)).saveToString());

        ShopState loaded = ShopStateRepository.decode(yaml).get("market");

        assertEquals(1_764_000_000_000L, loaded.periodStart());
        assertEquals(List.of("a1", "b2"), loaded.picks());
        assertEquals(3, loaded.sold("a1", StockScope.SHARED, UUID.randomUUID()));
        assertEquals(1, loaded.sold("b2", StockScope.PER_PLAYER, alex));
    }

    @Test
    void handEditedPlayerIdsAreSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("shops:\n  market:\n    period-start: 5\n    sold-to:\n      a1:\n        not-a-uuid: 2\n");

        ShopState loaded = ShopStateRepository.decode(yaml).get("market");

        assertTrue(loaded.playerSales().get("a1").isEmpty());
    }
}
