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
import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.ShopPageMode;
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
    void pageModeRoundTrips() throws Exception {
        Shop shop = Shop.create("market", "Market").withPageMode(ShopPageMode.ONE_PER_DAY);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(ShopRepository.encode(List.of(shop)).saveToString());

        assertEquals(ShopPageMode.ONE_PER_DAY, ShopRepository.decode(yaml).getFirst().pageMode());
    }

    @Test
    void tradeIdsSlotsAndTermsRoundTrip() {
        OfferTerms terms = new OfferTerms(120.5, 3, 4, StockScope.PER_PLAYER);
        ShopOffer offer = new ShopOffer("sword01", 23, TestItems.item(), null, TestItems.item(), terms);
        ShopOffer plain = new ShopOffer("bread01", 0, TestItems.item(), null, TestItems.item(), null);

        List<ShopOffer> decoded = ShopRepository.decodeOffers(ShopRepository.encodeOffers(List.of(offer, plain)));

        assertEquals("sword01", decoded.get(0).id());
        assertEquals(23, decoded.get(0).slot());
        assertEquals(terms, decoded.get(0).terms());
        assertEquals("bread01", decoded.get(1).id());
        assertEquals(OfferTerms.DEFAULT, decoded.get(1).terms());
        assertEquals(List.of("id", "slot", "cost", "result"),
                List.copyOf(ShopRepository.encodeOffers(List.of(plain)).getFirst().keySet()),
                "default terms are not written");
    }

    @Test
    void filesFromOlderVersionsAreDetectedSoTheyGetRewritten() throws Exception {
        assertTrue(ShopRepository.needsRewrite(yaml("shops:\n  bakery:\n    offers:\n    - price: 2\n")), "no id");
        assertTrue(ShopRepository.needsRewrite(yaml("shops:\n  bakery:\n    offers:\n    - id: abc\n")), "no slot");
        assertTrue(
                ShopRepository.needsRewrite(
                        yaml("shops:\n  bakery:\n    offers:\n    - id: abc\n      slot: 0\n      weight: 20\n")),
                "draw weight");
        assertTrue(
                ShopRepository.needsRewrite(
                        yaml("shops:\n  bakery:\n    assortment:\n      refresh-minutes: 60\n    offers: []\n")),
                "assortment");
        assertFalse(
                ShopRepository.needsRewrite(yaml("shops:\n  bakery:\n    offers:\n    - id: abc\n      slot: 0\n")));
    }

    @Test
    void oldShopsShowAllPagesAndTheirTradesKeepTheirOrder() throws Exception {
        YamlConfiguration yaml = yaml("shops:\n  bakery:\n    name: Bakery\n    offers: []\n");

        assertEquals(ShopPageMode.ALL_PAGES, ShopRepository.decode(yaml).getFirst().pageMode());
        // Without a slot, the shop places trades in list order (see ShopTest).
        assertEquals(ShopOffer.UNPLACED,
                ShopRepository.decodeOffers(List.of(java.util.Map.of("id", "a"))).getFirst().slot());
    }

    @Test
    void rotatingShopsBecomeOnePagePerDayAndNotOfferedTradesAreSetAside() throws Exception {
        YamlConfiguration yaml = yaml("shops:\n  market:\n    assortment:\n      rotating-slots: 3\n"
                + "    offers:\n    - id: sold\n      price: 2\n      payout: 1\n"
                + "    - id: kept\n      price: 2\n      payout: 1\n      availability: HIDDEN\n");

        List<ShopOffer> stored = ShopRepository.decodeStoredTrades(yaml).get("market");

        assertEquals(ShopPageMode.ONE_PER_DAY, ShopRepository.decode(yaml).getFirst().pageMode());
        assertEquals(List.of("kept"), stored.stream().map(ShopOffer::id).toList());
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
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
