package dev.blockfolk.repository;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopState;

/**
 * The current day, the page on sale, and the sales of every shop, stored in
 * {@code shop-state.yml} apart from the shop definitions so that trading never
 * rewrites {@code shops.yml}.
 */
public final class ShopStateRepository {
    private final File file;
    private final DebouncedYamlWriter writer;
    private final Map<String, ShopState> states = new LinkedHashMap<>();

    public ShopStateRepository(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "shop-state.yml");
        writer = new DebouncedYamlWriter(plugin);
    }

    public void loadAll() {
        states.clear();
        states.putAll(decode(YamlConfiguration.loadConfiguration(file)));
    }

    /** The stored state, or a fresh one starting on {@code today}. */
    public ShopState get(String shopKey, long today) {
        return states.computeIfAbsent(Shop.normalizeKey(shopKey), ignored -> ShopState.fresh(today));
    }

    public void delete(String shopKey) {
        if (states.remove(Shop.normalizeKey(shopKey)) != null)
            save();
    }

    public void save() {
        writer.queue(file, () -> encode(states));
    }

    public void flush() {
        writer.flush();
    }

    static YamlConfiguration encode(Map<String, ShopState> states) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("shops");
        states.forEach((key, state) -> {
            ConfigurationSection section = root.createSection(key);
            if (state.knowsDay())
                section.set("day", state.day());
            if (state.page() > 0)
                section.set("page", state.page());
            if (!state.sharedSales().isEmpty())
                section.createSection("sold", new LinkedHashMap<>(state.sharedSales()));
            Map<String, Map<UUID, Integer>> playerSales = state.playerSales();
            if (!playerSales.isEmpty()) {
                ConfigurationSection soldTo = section.createSection("sold-to");
                playerSales.forEach((offer, buyers) -> {
                    Map<String, Object> counts = new LinkedHashMap<>();
                    buyers.forEach((buyer, count) -> counts.put(buyer.toString(), count));
                    soldTo.createSection(offer, counts);
                });
            }
        });
        return yaml;
    }

    static Map<String, ShopState> decode(YamlConfiguration yaml) {
        Map<String, ShopState> states = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("shops");
        if (root == null)
            return states;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null || Shop.normalizeKey(key).isEmpty())
                continue;
            Map<String, Integer> sold = new HashMap<>();
            ConfigurationSection soldSection = section.getConfigurationSection("sold");
            if (soldSection != null)
                soldSection.getKeys(false).forEach(offer -> sold.put(offer, Math.max(0, soldSection.getInt(offer))));
            Map<String, Map<UUID, Integer>> soldTo = new HashMap<>();
            ConfigurationSection soldToSection = section.getConfigurationSection("sold-to");
            if (soldToSection != null) {
                for (String offer : soldToSection.getKeys(false)) {
                    ConfigurationSection buyers = soldToSection.getConfigurationSection(offer);
                    if (buyers == null)
                        continue;
                    Map<UUID, Integer> counts = new HashMap<>();
                    for (String buyer : buyers.getKeys(false)) {
                        try {
                            counts.put(UUID.fromString(buyer), Math.max(0, buyers.getInt(buyer)));
                        } catch (IllegalArgumentException ignored) {
                            // A hand-edited entry that is not a player id.
                        }
                    }
                    soldTo.put(offer, counts);
                }
            }
            // States from before daily restocks have no day; the shop adopts the
            // current day when it is next opened.
            long day = section.isSet("day") ? section.getLong("day") : ShopState.UNKNOWN_DAY;
            states.put(Shop.normalizeKey(key), new ShopState(day, section.getInt("page"), sold, soldTo));
        }
        return states;
    }
}
