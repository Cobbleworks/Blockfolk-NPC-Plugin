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

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;

/** Shared shop library stored in {@code shops.yml}, in creation order. */
public final class ShopRepository {
    private final File file;
    private final DebouncedYamlWriter writer;
    private final Map<String, Shop> shops = new LinkedHashMap<>();

    public ShopRepository(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "shops.yml");
        writer = new DebouncedYamlWriter(plugin);
    }

    public void loadAll() {
        shops.clear();
        decode(YamlConfiguration.loadConfiguration(file)).forEach(shop -> shops.put(shop.key(), shop));
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
            shops.add(new Shop(key, section.getString("name"), decodeOffers(section.getList("offers", List.of()))));
        }
        return shops;
    }

    static List<Map<String, Object>> encodeOffers(List<ShopOffer> offers) {
        return offers.stream().map(offer -> {
            Map<String, Object> saved = new LinkedHashMap<>();
            putItem(saved, "cost", offer.cost());
            putItem(saved, "second-cost", offer.secondCost());
            putItem(saved, "result", offer.result());
            return saved;
        }).toList();
    }

    static List<ShopOffer> decodeOffers(List<?> stored) {
        List<ShopOffer> offers = new ArrayList<>();
        for (Object entry : stored) {
            if (entry instanceof Map<?, ?> saved) {
                offers.add(new ShopOffer(itemOrNull(saved.get("cost")), itemOrNull(saved.get("second-cost")),
                        itemOrNull(saved.get("result"))));
            }
        }
        return offers;
    }

    private static void putItem(Map<String, Object> target, String key, ItemStack item) {
        if (item != null)
            target.put(key, item);
    }

    private static ItemStack itemOrNull(Object value) {
        return value instanceof ItemStack itemStack ? itemStack : null;
    }
}
