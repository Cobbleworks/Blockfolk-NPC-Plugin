package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.Shop;

class ShopRepositoryTest {
    @Test
    void shopsRoundTripInOrder() throws Exception {
        List<Shop> shops = List.of(Shop.create("smithy", "Smithy"), Shop.create("bakery", "Town Bakery"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(ShopRepository.encode(shops).saveToString());

        assertEquals(shops, ShopRepository.decode(yaml));
    }

    @Test
    void missingNameFallsBackToTheKey() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("shops:\n  bakery:\n    offers: []\n");

        assertEquals(List.of(Shop.create("bakery", "bakery")), ShopRepository.decode(yaml));
    }

    @Test
    void migrationOnlyFillsOpenShopActionsWithoutAShop() {
        var assign = ShopRepository.assignUnsetShop("baker");
        BehaviourAction unset = new BehaviourAction(BehaviourActionType.OPEN_SHOP, null);
        BehaviourAction chosen = new BehaviourAction(BehaviourActionType.OPEN_SHOP, "smithy");
        BehaviourAction other = new BehaviourAction(BehaviourActionType.WAVE, null);

        assertEquals(new BehaviourAction(BehaviourActionType.OPEN_SHOP, "baker"), assign.apply(unset));
        assertSame(chosen, assign.apply(chosen));
        assertSame(other, assign.apply(other));
    }
}
