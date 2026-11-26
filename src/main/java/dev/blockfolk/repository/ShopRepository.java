package dev.blockfolk.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.OfferTerms;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.ShopPageMode;
import dev.blockfolk.model.StockScope;

/**
 * Shared shop library stored in {@code shops.yml}, in creation order. What
 * changes while players trade lives in {@link ShopStateRepository}.
 */
public final class ShopRepository {
    private final File file;
    private final DebouncedYamlWriter writer;
    private final Logger logger;
    private final Map<String, Shop> shops = new LinkedHashMap<>();

    public ShopRepository(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "shops.yml");
        writer = new DebouncedYamlWriter(plugin);
        logger = plugin.getLogger();
    }

    public void loadAll() {
        shops.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Shop> decoded = decode(yaml);
        decoded.forEach(shop -> shops.put(shop.key(), shop));
        boolean rewrite = needsRewrite(yaml);
        // Trades switched to "Not Offered" before shops had daily pages move into
        // a shop of their own that no NPC opens, so nothing is lost or sold.
        Map<String, List<ShopOffer>> stored = decodeStoredTrades(yaml);
        for (Shop shop : decoded) {
            List<ShopOffer> trades = stored.getOrDefault(shop.key(), List.of());
            if (trades.isEmpty())
                continue;
            Shop storage = new Shop(uniqueKey(shop.key() + "_stored"), shop.name() + " (stored)", trades);
            shops.put(storage.key(), storage);
            rewrite = true;
            logger.info(() -> "Moved " + trades.size() + " not-offered trade(s) of shop '" + shop.key()
                    + "' into the new, unused shop '" + storage.key() + "'.");
        }
        // Store ids and slots given on load, so stock counts and pages keep
        // pointing at the same trades.
        if (rewrite)
            saveAll();
    }

    public List<Shop> findAll() {
        return List.copyOf(shops.values());
    }

    public Optional<Shop> find(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(shops.get(Shop.normalizeKey(key)));
    }

    public void save(Shop shop) {
        shops.put(shop.key(), shop);
        saveAll();
    }

    public void delete(String key) {
        if (shops.remove(Shop.normalizeKey(key)) != null)
            saveAll();
    }

    /** Returns {@code base} or the first free {@code base_N} variant. */
    public String uniqueKey(String base) {
        String normalized = Shop.normalizeKey(base);
        if (normalized.isEmpty())
            normalized = "shop";
        String trimmed = normalized.substring(0, Math.min(Shop.MAX_KEY_LENGTH - 6, normalized.length()));
        String key = normalized;
        int suffix = 2;
        while (shops.containsKey(key))
            key = trimmed + "_" + suffix++;
        return key;
    }

    /**
     * Moves per-preset shops from older versions into the library under the preset
     * key and points that preset's unset Open Shop actions (including those on
     * routes it owns) at it. Returns the number of migrated shops.
     */
    public int migrateLegacyShops(Map<String, Shop> legacyShops, Collection<NpcDefinition> definitions,
            Consumer<NpcDefinition> saveDefinition, Collection<NpcRoute> routes, Consumer<NpcRoute> saveRoute) {
        int migrated = 0;
        for (NpcDefinition definition : definitions) {
            Shop legacy = legacyShops.get(definition.getKey());
            if (legacy == null)
                continue;
            // A shop under this key means an earlier migration stored it but the
            // preset file was not rewritten yet; keep the stored shop.
            if (!shops.containsKey(legacy.key())) {
                save(legacy);
                migrated++;
            }
            UnaryOperator<BehaviourAction> assign = assignUnsetShop(legacy.key());
            definition.mapActions(assign);
            saveDefinition.accept(definition);
            for (NpcRoute route : routes) {
                if (route.isOwnedBy(definition.getKey()) && route.mapActions(assign))
                    saveRoute.accept(route);
            }
        }
        return migrated;
    }

    static UnaryOperator<BehaviourAction> assignUnsetShop(String shopKey) {
        return action -> action.type() == BehaviourActionType.OPEN_SHOP && action.value() == null
                ? new BehaviourAction(BehaviourActionType.OPEN_SHOP, shopKey)
                : action;
    }

    private void saveAll() {
        writer.queue(file, () -> encode(findAll()));
    }

    public void flush() {
        writer.flush();
    }

    static YamlConfiguration encode(List<Shop> shops) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("shops");
        for (Shop shop : shops) {
            ConfigurationSection section = root.createSection(shop.key());
            section.set("name", shop.name());
            if (shop.pageMode() != ShopPageMode.ALL_PAGES)
                section.set("page-mode", shop.pageMode().name());
            section.set("offers", encodeOffers(shop.offers()));
        }
        return yaml;
    }

    static List<Shop> decode(YamlConfiguration yaml) {
        List<Shop> shops = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("shops");
        if (root == null)
            return shops;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null || Shop.normalizeKey(key).isEmpty())
                continue;
            // Shops that rotated a random assortment become one-page-per-day shops.
            ShopPageMode pageMode = section.isSet("page-mode")
                    ? ShopPageMode.fromStored(section.getString("page-mode"))
                    : section.getInt("assortment.rotating-slots", 0) > 0
                            ? ShopPageMode.ONE_PER_DAY
                            : ShopPageMode.ALL_PAGES;
            List<?> offers = section.getList("offers", List.of());
            shops.add(new Shop(key, section.getString("name"),
                    decodeOffers(offers.stream().filter(entry -> !isStoredTrade(entry)).toList()), pageMode));
        }
        return shops;
    }

    /**
     * Trades marked "Not Offered" by the version before daily pages, by shop key.
     */
    static Map<String, List<ShopOffer>> decodeStoredTrades(YamlConfiguration yaml) {
        Map<String, List<ShopOffer>> stored = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("shops");
        if (root == null)
            return stored;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null || Shop.normalizeKey(key).isEmpty())
                continue;
            List<?> hidden = section.getList("offers", List.of()).stream().filter(ShopRepository::isStoredTrade)
                    .toList();
            if (!hidden.isEmpty())
                stored.put(Shop.normalizeKey(key), decodeOffers(hidden).stream()
                        .map(offer -> offer.withSlot(ShopOffer.UNPLACED)).toList());
        }
        return stored;
    }

    private static boolean isStoredTrade(Object entry) {
        return entry instanceof Map<?, ?> saved && "HIDDEN".equalsIgnoreCase(text(saved.get("availability")));
    }

    static List<Map<String, Object>> encodeOffers(List<ShopOffer> offers) {
        return offers.stream().map(offer -> {
            Map<String, Object> saved = new LinkedHashMap<>();
            saved.put("id", offer.id());
            saved.put("slot", offer.slot());
            putItem(saved, "cost", offer.cost());
            putItem(saved, "second-cost", offer.secondCost());
            putItem(saved, "result", offer.result());
            OfferTerms terms = offer.terms();
            if (terms.price() > 0)
                saved.put("price", terms.price());
            if (terms.payout() > 0)
                saved.put("payout", terms.payout());
            if (terms.limited()) {
                saved.put("stock", terms.stockLimit());
                saved.put("stock-scope", terms.stockScope().name());
            }
            return saved;
        }).toList();
    }

    static List<ShopOffer> decodeOffers(List<?> stored) {
        List<ShopOffer> offers = new ArrayList<>();
        for (Object entry : stored) {
            if (entry instanceof Map<?, ?> saved) {
                OfferTerms terms = new OfferTerms(number(saved.get("price"), 0), number(saved.get("payout"), 0),
                        (int) number(saved.get("stock"), 0), StockScope.fromStored(text(saved.get("stock-scope"))));
                // Trades without a slot keep their position in the list.
                offers.add(new ShopOffer(text(saved.get("id")),
                        (int) number(saved.get("slot"), ShopOffer.UNPLACED), itemOrNull(saved.get("cost")),
                        itemOrNull(saved.get("second-cost")), itemOrNull(saved.get("result")), terms));
            }
        }
        return offers;
    }

    /**
     * Whether the file is from an older version: trades without an id or slot, or
     * settings of the former rotating assortment.
     */
    static boolean needsRewrite(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("shops");
        if (root == null)
            return false;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null)
                continue;
            if (section.isSet("assortment"))
                return true;
            for (Object entry : section.getList("offers", List.of())) {
                if (entry instanceof Map<?, ?> saved && (!ShopOffer.isValidId(text(saved.get("id")))
                        || !saved.containsKey("slot") || saved.containsKey("availability")
                        || saved.containsKey("weight")))
                    return true;
            }
        }
        return false;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number)
            return number.doubleValue();
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static void putItem(Map<String, Object> target, String key, ItemStack item) {
        if (item != null)
            target.put(key, item);
    }

    private static ItemStack itemOrNull(Object value) {
        return value instanceof ItemStack itemStack ? itemStack : null;
    }
}
