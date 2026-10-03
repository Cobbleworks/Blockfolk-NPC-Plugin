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
        yaml.set("version", 4);
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
            section.set("cast-mode", attack.castMode().name());
            section.set("cooldown-ticks", attack.cooldownTicks());
            section.set("damage", attack.damage());
            section.set("effects", attack.effects().stream().sorted().map(Enum::name).toList());
            section.set("effect-seconds", attack.effectSeconds());
            section.set("effect-level", attack.effectLevel());
            section.set("knockback", attack.knockback());
            section.set("visual", attack.visual().name());
            section.set("min-range", attack.minRange());
            section.set("inner-radius", attack.innerRadius());
            section.set("chain-targets", attack.chainTargets());
            section.set("pulses", attack.pulses());
            section.set("pulse-interval-ticks", attack.pulseIntervalTicks());
            section.set("condition", attack.condition().name());
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
            attacks.add(FighterAttack.builder(key, section.getString("name", key))
                    .origin(enumValue(Origin.class, section.getString("origin"), Origin.NPC))
                    .shape(enumValue(Shape.class, section.getString("shape"), Shape.SPHERE))
                    .range(section.getDouble("range", 8)).size(section.getDouble("size", 2))
                    .angle(section.getDouble("angle", 60)).delayTicks(section.getInt("delay-ticks", 20))
                    .cooldownTicks(section.getInt("cooldown-ticks", 200)).damage(section.getDouble("damage", 4))
                    .effects(effects).effectSeconds(section.getInt("effect-seconds", 3))
                    .effectLevel(section.getInt("effect-level", 1)).knockback(section.getDouble("knockback", 0.8))
                    .visual(enumValue(Visual.class, section.getString("visual"), Visual.SOUL))
                    .coneLength(section.getDouble("cone-length", section.getDouble("range", 8)))
                    .icon(section.getItemStack("icon"))
                    .castMode(enumValue(CastMode.class, section.getString("cast-mode"), null))
                    .minRange(section.getDouble("min-range", 0)).innerRadius(section.getDouble("inner-radius", 0))
                    .chainTargets(section.getInt("chain-targets", 3)).pulses(section.getInt("pulses", 1))
                    .pulseIntervalTicks(section.getInt("pulse-interval-ticks", 20))
                    .condition(enumValue(Condition.class, section.getString("condition"), Condition.ALWAYS)).build());
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
