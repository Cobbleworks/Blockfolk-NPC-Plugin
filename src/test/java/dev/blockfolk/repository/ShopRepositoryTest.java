package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.OfferAvailability;
import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopAssortment;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.StockScope;
import dev.blockfolk.testsupport.TestItems;

class ShopRepositoryTest {
    @Test
    void shopsRoundTripInOrder() throws Exception {
        List<Shop> shops = List.of(Shop.create("smithy", "Smithy"), Shop.create("bakery", "Town Bakery"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(ShopRepository.encode(shops).saveToString());

        assertEquals(shops, ShopRepository.decode(yaml));
    }

    @Test
    void assortmentRoundTrips() throws Exception {
        Shop shop = Shop.create("market", "Market").withAssortment(new ShopAssortment(10, 90));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(ShopRepository.encode(List.of(shop)).saveToString());

        assertEquals(new ShopAssortment(10, 90), ShopRepository.decode(yaml).getFirst().assortment());
    }

    @Test
    void tradeIdsAndTermsRoundTrip() {
        OfferTerms terms = new OfferTerms(120.5, 3, OfferAvailability.ALWAYS, 25, 4, StockScope.PER_PLAYER);
        ShopOffer offer = new ShopOffer("sword01", TestItems.item(), null, TestItems.item(), terms);
        ShopOffer plain = new ShopOffer("bread01", TestItems.item(), null, TestItems.item(), null);

        List<ShopOffer> decoded = ShopRepository.decodeOffers(ShopRepository.encodeOffers(List.of(offer, plain)));

        assertEquals("sword01", decoded.get(0).id());
        assertEquals(terms, decoded.get(0).terms());
        assertEquals("bread01", decoded.get(1).id());
        assertEquals(OfferTerms.DEFAULT, decoded.get(1).terms());
        assertEquals(List.of("id", "cost", "result"),
                List.copyOf(ShopRepository.encodeOffers(List.of(plain)).getFirst().keySet()),
                "default terms are not written");
    }

    @Test
    void tradesFromOlderVersionsAreDetectedSoTheirNewIdsGetSaved() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("shops:\n  bakery:\n    offers:\n    - price: 2\n");
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("shops:\n  bakery:\n    offers:\n    - id: abc\n");

        assertTrue(ShopRepository.hasTradesWithoutId(old));
        assertFalse(ShopRepository.hasTradesWithoutId(current));
    }

    @Test
    void oldShopsLoadWithoutRotationOrRestock() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("shops:\n  bakery:\n    name: Bakery\n    offers: []\n");

        assertEquals(ShopAssortment.DEFAULT, ShopRepository.decode(yaml).getFirst().assortment());
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
