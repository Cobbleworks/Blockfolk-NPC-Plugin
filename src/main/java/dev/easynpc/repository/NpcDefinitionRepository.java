package dev.easynpc.repository;

import dev.easynpc.model.CombatProfile;
import dev.easynpc.model.MovementProfile;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.WalkingSpeed;
import dev.easynpc.util.LocationCodec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class NpcDefinitionRepository {
    private final JavaPlugin plugin;
    private final File definitionsFolder;
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();

    public NpcDefinitionRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.definitionsFolder = new File(plugin.getDataFolder(), "definitions");
    }

    public void loadAll() {
        definitions.clear();
        if (!definitionsFolder.exists() && !definitionsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create definitions folder.");
            return;
        }
        File[] files = definitionsFolder.listFiles((directory, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                NpcDefinition definition = load(file);
                definitions.put(definition.getKey(), definition);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to load NPC definition " + file.getName(), exception);
            }
        }
    }

    public Optional<NpcDefinition> find(String keyOrName) {
        return Optional.ofNullable(definitions.get(NpcDefinition.toKey(keyOrName)));
    }

    public Collection<NpcDefinition> findAll() {
        return definitions.values().stream()
            .sorted(Comparator.comparing(NpcDefinition::getKey))
            .toList();
    }

    public NpcDefinition save(NpcDefinition definition) {
        definitions.put(definition.getKey(), definition);
        File file = new File(definitionsFolder, definition.getKey() + ".yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("key", definition.getKey());
        configuration.set("display-name", definition.getDisplayName());
        configuration.set("skin-url", definition.getSkinUrl());
        if (definition.getSpawnpoint() != null) {
            LocationCodec.write(configuration.createSection("spawnpoint"), definition.getSpawnpoint());
        }
        configuration.set("inventory.contents", Arrays.asList(definition.getInventoryContents()));
        configuration.set("inventory.armor", Arrays.asList(definition.getArmorContents()));
        configuration.set("inventory.main-hand", definition.getMainHand());
        configuration.set("inventory.off-hand", definition.getOffHand());
        configuration.set("dialog.lines", definition.getDialogLines());
        configuration.set("dialog.seconds-per-line", definition.getSecondsPerDialogLine());
        configuration.set("combat.enabled", definition.getCombatProfile().enabled());
        configuration.set("movement.enabled", definition.getMovementProfile().enabled());
        configuration.set("movement.route", definition.getMovementProfile().routeKey());
        configuration.set("movement.speed", definition.getMovementProfile().walkingSpeed().name().toLowerCase(Locale.ROOT));
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save NPC definition " + definition.getKey(), exception);
        }
        return definition;
    }

    public boolean delete(NpcDefinition definition) {
        if (definitions.remove(definition.getKey()) == null) {
            return false;
        }
        File file = new File(definitionsFolder, definition.getKey() + ".yml");
        if (file.exists() && !file.delete()) {
            definitions.put(definition.getKey(), definition);
            throw new IllegalStateException("Could not delete NPC definition " + definition.getKey());
        }
        return true;
    }

    private NpcDefinition load(File file) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String key = configuration.getString("key", file.getName().replaceFirst("\\.yml$", ""));
        NpcDefinition definition = new NpcDefinition(NpcDefinition.toKey(key));
        definition.setDisplayName(configuration.getString("display-name", definition.getKey()));
        definition.setSkinUrl(configuration.getString("skin-url"));
        definition.setSpawnpoint(LocationCodec.read(configuration.getConfigurationSection("spawnpoint")));
        definition.setInventoryContents(readItemArray(configuration, "inventory.contents", 36));
        definition.setArmorContents(readItemArray(configuration, "inventory.armor", 4));
        definition.setMainHand(configuration.getItemStack("inventory.main-hand"));
        definition.setOffHand(configuration.getItemStack("inventory.off-hand"));
        definition.setDialogLines(configuration.getStringList("dialog.lines"));
        definition.setSecondsPerDialogLine(configuration.getInt("dialog.seconds-per-line", 3));
        definition.setCombatProfile(new CombatProfile(configuration.getBoolean("combat.enabled", false)));
        definition.setMovementProfile(new MovementProfile(
            configuration.getBoolean("movement.enabled", false),
            configuration.getString("movement.route"),
            WalkingSpeed.fromStored(configuration.getString("movement.speed"))
        ));
        return definition;
    }

    private ItemStack[] readItemArray(YamlConfiguration configuration, String path, int size) {
        ItemStack[] items = new ItemStack[size];
        List<?> list = configuration.getList(path, new ArrayList<>());
        for (int index = 0; index < Math.min(list.size(), size); index++) {
            Object value = list.get(index);
            if (value instanceof ItemStack itemStack) {
                items[index] = itemStack;
            }
        }
        return items;
    }
}
