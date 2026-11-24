package dev.blockfolk.repository;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopState;

/**
 * Stock periods, rotation picks, and sales of every shop, stored in
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

    /** The stored state, or a fresh one starting at {@code now}. */
    public ShopState get(String shopKey, long now) {
        return states.computeIfAbsent(Shop.normalizeKey(shopKey), ignored -> ShopState.fresh(now));
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
            section.set("period-start", state.periodStart());
            if (!state.picks().isEmpty())
                section.set("picks", state.picks());
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
            List<String> picks = section.getStringList("picks");
            states.put(Shop.normalizeKey(key), new ShopState(section.getLong("period-start"), picks, sold, soldTo));
        }
        return states;
    }
}
