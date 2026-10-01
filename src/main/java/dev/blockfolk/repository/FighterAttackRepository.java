package dev.blockfolk.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import dev.blockfolk.fighters.FighterAttack;
import dev.blockfolk.fighters.FighterAttack.*;
import dev.blockfolk.fighters.FighterTemplates;

public final class FighterAttackRepository {
    private final File file;
    private final DebouncedYamlWriter writer;
    private final Map<String, FighterAttack> attacks = new LinkedHashMap<>();

    public FighterAttackRepository(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "abilities.yml");
        writer = new DebouncedYamlWriter(plugin);
    }
    public void loadAll() {
        attacks.clear();
        File source = sourceFile(file);
        if (!source.exists()) {
            FighterTemplates.defaults().forEach(attack -> attacks.put(attack.key(), attack));
            saveAll();
            return;
        }
        decode(YamlConfiguration.loadConfiguration(source)).forEach(attack -> attacks.put(attack.key(), attack));
        if (!source.equals(file))
            saveAll();
    }

    static File sourceFile(File file) {
        return file.exists() ? file : new File(file.getParentFile(), "fighters.yml");
    }
    public List<FighterAttack> findAll() {
        return List.copyOf(attacks.values());
    }
    public Optional<FighterAttack> find(String key) {
        return Optional.ofNullable(attacks.get(key));
    }
    public void save(FighterAttack attack) {
        attacks.put(attack.key(), attack);
        saveAll();
    }
    public void delete(String key) {
        if (attacks.remove(key) != null)
            saveAll();
    }
    private void saveAll() {
        writer.queue(file, () -> encode(findAll()));
    }
    public void flush() {
        writer.flush();
    }

    static YamlConfiguration encode(List<FighterAttack> attacks) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 2);
        ConfigurationSection root = yaml.createSection("attacks");
        for (FighterAttack attack : attacks) {
            ConfigurationSection section = root.createSection(attack.key());
            section.set("name", attack.name());
            section.set("origin", attack.origin().name());
            section.set("shape", attack.shape().name());
            section.set("range", attack.range());
            section.set("cone-length", attack.coneLength());
            section.set("icon", attack.icon());
            section.set("size", attack.size());
            section.set("angle", attack.angle());
            section.set("delay-ticks", attack.delayTicks());
            section.set("cooldown-ticks", attack.cooldownTicks());
            section.set("damage", attack.damage());
            section.set("effects", attack.effects().stream().sorted().map(Enum::name).toList());
            section.set("effect-seconds", attack.effectSeconds());
            section.set("effect-level", attack.effectLevel());
            section.set("knockback", attack.knockback());
            section.set("visual", attack.visual().name());
        }
        return yaml;
    }
    static List<FighterAttack> decode(YamlConfiguration yaml) {
        List<FighterAttack> attacks = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("attacks");
        if (root == null)
            return attacks;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null || !key.equals(FighterAttack.normalizeKey(key)))
                continue;
            Set<Effect> effects = section.getStringList("effects").stream()
                    .map(value -> enumValue(Effect.class, value, null)).filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            attacks.add(new FighterAttack(key, section.getString("name", key),
                    enumValue(Origin.class, section.getString("origin"), Origin.NPC),
                    enumValue(Shape.class, section.getString("shape"), Shape.SPHERE), section.getDouble("range", 8),
                    section.getDouble("size", 2), section.getDouble("angle", 60), section.getInt("delay-ticks", 20),
                    section.getInt("cooldown-ticks", 200), section.getDouble("damage", 4), effects,
                    section.getInt("effect-seconds", 3), section.getInt("effect-level", 1),
                    section.getDouble("knockback", 0.8),
                    enumValue(Visual.class, section.getString("visual"), Visual.SOUL),
                    section.getDouble("cone-length", section.getDouble("range", 8)), section.getItemStack("icon")));
        }
        return attacks;
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
