package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.ShopState;
import dev.blockfolk.model.StockScope;

class ShopStateRepositoryTest {
    @Test
    void dayPageAndSalesRoundTrip() throws Exception {
        UUID alex = UUID.randomUUID();
        ShopState state = new ShopState(412, 3, Map.of(), Map.of());
        state.recordSale("a1", StockScope.SHARED, alex, 3);
        state.recordSale("b2", StockScope.PER_PLAYER, alex, 1);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(ShopStateRepository.encode(Map.of("market", state)).saveToString());

        ShopState loaded = ShopStateRepository.decode(yaml).get("market");

        assertEquals(412, loaded.day());
        assertEquals(3, loaded.page());
        assertEquals(3, loaded.sold("a1", StockScope.SHARED, UUID.randomUUID()));
        assertEquals(1, loaded.sold("b2", StockScope.PER_PLAYER, alex));
    }

    @Test
    void handEditedPlayerIdsAreSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("shops:\n  market:\n    day: 5\n    sold-to:\n      a1:\n        not-a-uuid: 2\n");

        ShopState loaded = ShopStateRepository.decode(yaml).get("market");

        assertTrue(loaded.playerSales().get("a1").isEmpty());
    }

    @Test
    void statesFromBeforeDailyRestocksHaveNoDay() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("shops:\n  market:\n    period-start: 1764000000000\n    picks: [a1]\n");

        assertFalse(ShopStateRepository.decode(yaml).get("market").knowsDay());
    }
}
