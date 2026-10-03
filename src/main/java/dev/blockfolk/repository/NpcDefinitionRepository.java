package dev.blockfolk.repository;

import dev.blockfolk.ai.AiActionType;
import dev.blockfolk.ai.AiControlSettings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.AiMemory;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.SpecialAttackOptions;
import dev.blockfolk.model.MovementProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcColor;
import dev.blockfolk.model.NpcPose;
import dev.blockfolk.model.StoredLocation;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.util.LocationCodec;

public final class NpcDefinitionRepository {

    private final JavaPlugin plugin;
    private final File definitionsFolder;
    private final File orderFile;
    private final DebouncedYamlWriter writer;
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
    private final List<String> definitionOrder = new ArrayList<>();

    public NpcDefinitionRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.definitionsFolder = new File(plugin.getDataFolder(), "definitions");
        this.orderFile = new File(plugin.getDataFolder(), "definition-order.yml");
        this.writer = new DebouncedYamlWriter(plugin);
    }

    public void loadAll() {
        definitions.clear();
        definitionOrder.clear();
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
        loadOrder();
    }

    public Optional<NpcDefinition> find(String keyOrName) {
        return Optional.ofNullable(definitions.get(NpcDefinition.toKey(keyOrName)));
    }

    public Collection<NpcDefinition> findAll() {
        return definitionOrder.stream().map(definitions::get).filter(java.util.Objects::nonNull).toList();
    }

    public NpcDefinition save(NpcDefinition definition) {
        boolean added = definitions.put(definition.getKey(), definition) == null;
        if (added) {
            definitionOrder.add(definition.getKey());
            saveOrder();
        }
        File file = new File(definitionsFolder, definition.getKey() + ".yml");
        writer.queue(file, () -> serialize(definition));
        return definition;
    }

    private YamlConfiguration serialize(NpcDefinition definition) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("key", definition.getKey());
        configuration.set("display-name", definition.getDisplayName());
        configuration.set("skin-url", definition.getSkinUrl());
        configuration.set("skin-texture-value", definition.getSkinTextureValue());
        configuration.set("skin-texture-signature", definition.getSkinTextureSignature());
        if (definition.getStoredSpawnpoint() != null) {
            LocationCodec.write(configuration.createSection("spawnpoint"), definition.getStoredSpawnpoint());
        }
        configuration.set("inventory.contents", Arrays.asList(definition.getInventoryContents()));
        configuration.set("inventory.initial-temporary-contents",
                Arrays.asList(definition.getInitialTemporaryInventoryContents()));
        configuration.set("inventory.armor", Arrays.asList(definition.getArmorContents()));
        configuration.set("inventory.main-hand", definition.getMainHand());
        configuration.set("inventory.off-hand", definition.getOffHand());
        writeCombatProfile(configuration, definition.getCombatProfile());
        configuration.set("movement.speed",
                definition.getMovementProfile().walkingSpeed().name().toLowerCase(Locale.ROOT));
        configuration.set("movement.enabled", definition.getMovementProfile().enabled());
        configuration.set("movement.route", definition.getMovementProfile().routeKey());
        configuration.set("properties.show-name", definition.isShowName());
        configuration.set("properties.look-at-player", definition.isLookAtPlayer());
        configuration.set("properties.item-pickup", definition.isItemPickup());
        configuration.set("properties.pushable", definition.isPushable());
        configuration.set("properties.pose", definition.getPose().name());
        configuration.set("properties.color", definition.getColor().name().toLowerCase(Locale.ROOT));
        AiControlSettings ai = definition.getAiControlSettings();
        configuration.set("ai-control.enabled", ai.enabled());
        configuration.set("ai-control.identity", ai.identity().isBlank() ? null : ai.identity());
        configuration.set("ai-control.behaviour", ai.behaviour().isBlank() ? null : ai.behaviour());
        configuration.set("ai-control.likes-dislikes", ai.likesDislikes().isBlank() ? null : ai.likesDislikes());
        configuration.set("ai-control.goal", ai.goal().isBlank() ? null : ai.goal());
        configuration.set("ai-control.information", ai.information().isBlank() ? null : ai.information());
        configuration.set("ai-control.respond-to-chat", ai.respondToChat());
        configuration.set("ai-control.memory.enabled", ai.memoryEnabled());
        configuration.set("ai-control.conversation.shared", ai.sharedConversation());
        configuration.set("ai-control.memory.facts", definition.getAiMemoryEntries().stream().map(memory -> {
            Map<String, Object> saved = new LinkedHashMap<>();
            saved.put("fact", memory.fact());
            saved.put("category", memory.category().name().toLowerCase(Locale.ROOT));
            saved.put("recorded-at", memory.recordedAt());
            if (memory.origin() != null) {
                saved.put("world", memory.origin().worldName());
                saved.put("x", memory.origin().x());
                saved.put("y", memory.origin().y());
                saved.put("z", memory.origin().z());
            }
            return saved;
        }).toList());
        configuration.set("ai-control.allowed-actions",
                ai.allowedActions().stream()
                        .filter(action -> action != AiActionType.REMEMBER_FACT && action != AiActionType.DROP_ITEM)
                        .map(action -> action.name().toLowerCase(Locale.ROOT)).sorted().toList());
        for (BehaviourEvent event : BehaviourEvent.values()) {
            List<Map<String, Object>> actions = BehaviourActionCodec.encodeList(definition.getBehaviourActions(event));
            configuration.set("behaviours." + event.name().toLowerCase(Locale.ROOT),
                    actions.isEmpty() ? null : actions);
        }
        for (String eventName : definition.getCustomEventNames()) {
            List<Map<String, Object>> actions = BehaviourActionCodec
                    .encodeList(definition.getCustomEventActions(eventName));
            configuration.set("custom-event-behaviours." + encodeEventName(eventName), actions);
        }
        return configuration;
    }

    public void reorder(List<String> orderedKeys) {
        List<String> normalized = orderedKeys.stream().map(NpcDefinition::toKey).toList();
        if (normalized.size() != definitions.size() || new HashSet<>(normalized).size() != normalized.size()
                || !definitions.keySet().containsAll(normalized)) {
            throw new IllegalArgumentException("The NPC order must contain every definition exactly once.");
        }
        definitionOrder.clear();
        definitionOrder.addAll(normalized);
        saveOrder();
    }

    public boolean delete(NpcDefinition definition) {
        if (definitions.remove(definition.getKey()) == null) {
            return false;
        }
        definitionOrder.remove(definition.getKey());
        File file = new File(definitionsFolder, definition.getKey() + ".yml");
        writer.delete(file);
        saveOrder();
        return true;
    }

    private void loadOrder() {
        definitionOrder.clear();
        List<String> stored = YamlConfiguration.loadConfiguration(orderFile).getStringList("order");
        Set<String> seen = new HashSet<>();
        for (String key : stored) {
            String normalized = NpcDefinition.toKey(key);
            if (definitions.containsKey(normalized) && seen.add(normalized)) {
                definitionOrder.add(normalized);
            }
        }
        definitions.keySet().stream().filter(seen::add).sorted(Comparator.naturalOrder()).forEach(definitionOrder::add);
    }

    private void saveOrder() {
        writer.queue(orderFile, this::serializeOrder);
    }

    private YamlConfiguration serializeOrder() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("order", definitionOrder);
        return configuration;
    }

    public void flush() {
        writer.flush();
    }

    static void writeCombatProfile(YamlConfiguration configuration, CombatProfile profile) {
        configuration.set("combat.max-health", profile.maxHealth());
        configuration.set("combat.respawn-seconds", profile.respawnSeconds());
        configuration.set("combat.aggression-level", profile.attackReaction().name().toLowerCase(Locale.ROOT));
        configuration.set("combat.targets.mobs", profile.targetMobs());
        configuration.set("combat.targets.animals", profile.targetAnimals());
        configuration.set("combat.targets.players", profile.targetPlayers());
        configuration.set("combat.targets.npcs", profile.targetNpcs());
        configuration.set("combat.alliance", profile.alliance());
        configuration.set("combat.show-boss-bar", profile.showBossBar());
        configuration.set("combat.dropped-experience", profile.droppedExperience());
        configuration.set("combat.special-attacks.enabled", profile.specialAttacks().storedAttacks());
        configuration.set("combat.special-attacks.interval-seconds", profile.specialAttacks().intervalSeconds());
        configuration.set("combat.special-attacks.fighter-attacks",
                profile.specialAttacks().fighterAttacks().stream().sorted().toList());
    }

    static CombatProfile readCombatProfile(YamlConfiguration configuration) {
        return new CombatProfile(configuration.getInt("combat.max-health", 0),
                configuration.getInt("combat.respawn-seconds", 0),
                AttackReaction.fromStored(configuration.getString("combat.aggression-level")),
                configuration.getBoolean("combat.targets.mobs", false),
                configuration.getBoolean("combat.targets.animals", false),
                configuration.getBoolean("combat.targets.players", false),
                configuration.getBoolean("combat.targets.npcs", false), configuration.getString("combat.alliance"),
                configuration.getBoolean("combat.show-boss-bar", false),
                configuration.getInt("combat.dropped-experience", 0),
                SpecialAttackOptions
                        .fromStored(configuration.getStringList("combat.special-attacks.enabled"),
                                configuration.getInt("combat.special-attacks.interval-seconds",
                                        SpecialAttackOptions.DEFAULT_INTERVAL_SECONDS))
                        .withFighterAttacks(configuration.getStringList("combat.special-attacks.fighter-attacks")));
    }

    private NpcDefinition load(File file) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String key = configuration.getString("key", file.getName().replaceFirst("\\.yml$", ""));
        NpcDefinition definition = new NpcDefinition(NpcDefinition.toKey(key));
        definition.setDisplayName(configuration.getString("display-name", definition.getKey()));
        definition.setResolvedSkin(configuration.getString("skin-url"), configuration.getString("skin-texture-value"),
                configuration.getString("skin-texture-signature"));
        definition.setStoredSpawnpoint(LocationCodec.readStored(configuration.getConfigurationSection("spawnpoint")));
        definition.setInventoryContents(readItemArray(configuration, "inventory.contents", 36));
        definition.setInitialTemporaryInventoryContents(
                readItemArray(configuration, "inventory.initial-temporary-contents", 27));
        definition.setArmorContents(readItemArray(configuration, "inventory.armor", 4));
        definition.setMainHand(configuration.getItemStack("inventory.main-hand"));
        definition.setOffHand(configuration.getItemStack("inventory.off-hand"));
        definition.setCombatProfile(readCombatProfile(configuration));
        WalkingSpeed storedSpeed = WalkingSpeed.fromStored(configuration.getString("movement.speed"));
        String storedRoute = configuration.getString("movement.route");
        try {
            definition.setMovementProfile(configuration.getBoolean("movement.enabled", false) && storedRoute != null
                    ? MovementProfile.routing(storedRoute, storedSpeed)
                    : MovementProfile.disabled().withWalkingSpeed(storedSpeed));
        } catch (IllegalArgumentException ignored) {
            definition.setMovementProfile(MovementProfile.disabled().withWalkingSpeed(storedSpeed));
            plugin.getLogger().warning("Ignoring malformed movement route in " + file.getName());
        }
        definition.setShowName(configuration.getBoolean("properties.show-name", true));
        definition.setLookAtPlayer(configuration.getBoolean("properties.look-at-player", true));
        definition.setItemPickup(configuration.getBoolean("properties.item-pickup", false));
        definition.setPushable(configuration.getBoolean("properties.pushable", true));
        definition.setPose(NpcPose.fromStored(configuration.getString("properties.pose")));
        definition.setColor(NpcColor.fromStored(configuration.getString("properties.color")));
        java.util.EnumSet<AiActionType> allowedAiActions = java.util.EnumSet.noneOf(AiActionType.class);
        for (String stored : configuration.getStringList("ai-control.allowed-actions")) {
            try {
                allowedAiActions.add(AiActionType.fromModel(stored));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring unknown AI action '" + stored + "' in " + file.getName());
            }
        }
        if (allowedAiActions.isEmpty())
            allowedAiActions.addAll(AiActionType.safeDefaults());
        String identity = configuration.getString("ai-control.identity", "");
        String behaviour = configuration.getString("ai-control.behaviour", "");
        String likesDislikes = configuration.getString("ai-control.likes-dislikes", "");
        String goal = configuration.getString("ai-control.goal", "");
        String information = configuration.getString("ai-control.information", "");
        definition.setAiControlSettings(new AiControlSettings(identity, behaviour, likesDislikes, goal, information,
                allowedAiActions, configuration.getBoolean("ai-control.enabled", false),
                configuration.getBoolean("ai-control.respond-to-chat", true),
                configuration.getBoolean("ai-control.memory.enabled", false),
                configuration.getBoolean("ai-control.conversation.shared", false)));
        List<AiMemory> memories = new ArrayList<>();
        for (Object item : configuration.getList("ai-control.memory.facts", List.of())) {
            if (item instanceof String legacy) {
                memories.add(new AiMemory(legacy, AiMemory.Category.PERSONAL));
            } else if (item instanceof Map<?, ?> saved) {
                Object fact = saved.get("fact");
                if (fact instanceof String text) {
                    AiMemory.Category category;
                    try {
                        category = AiMemory.Category
                                .valueOf(String.valueOf(saved.get("category")).toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException error) {
                        category = AiMemory.Category.PERSONAL;
                    }
                    StoredLocation origin = null;
                    if (saved.get("world") instanceof String world && saved.get("x") instanceof Number x
                            && saved.get("y") instanceof Number y && saved.get("z") instanceof Number z) {
                        origin = new StoredLocation(world, x.doubleValue(), y.doubleValue(), z.doubleValue(), 0, 0);
                    }
                    long recordedAt = saved.get("recorded-at") instanceof Number timestamp
                            ? timestamp.longValue()
                            : System.currentTimeMillis();
                    memories.add(new AiMemory(text, category, origin, recordedAt));
                }
            }
        }
        definition.setAiMemoryEntries(memories);
        for (BehaviourEvent event : BehaviourEvent.values()) {
            String path = "behaviours." + event.name().toLowerCase(Locale.ROOT);
            definition.setBehaviourActions(event, decodeActions(configuration.getMapList(path), file, "behaviour"));
        }
        List<BehaviourAction> attackedActions = decodeActions(configuration.getMapList("behaviours.npc_attacked"), file,
                "behaviour");
        if (!attackedActions.isEmpty()) {
            List<BehaviourAction> damageActions = definition.getBehaviourActions(BehaviourEvent.DAMAGE_TAKEN);
            List<BehaviourAction> merged = mergeDamageActions(attackedActions, damageActions);
            if (merged.size() < attackedActions.size() + damageActions.size()) {
                File backup = new File(file.getPath() + ".pre-damage-merge.bak");
                if (!backup.exists()) {
                    try {
                        Files.copy(file.toPath(), backup.toPath());
                    } catch (IOException exception) {
                        plugin.getLogger().log(Level.WARNING,
                                "Could not back up " + file.getName() + " before combining damage actions.", exception);
                    }
                }
                plugin.getLogger().warning("Only the first seven combined damage actions from " + file.getName()
                        + " fit in On Damage Taken; review the preset and the .pre-damage-merge.bak backup.");
            }
            definition.setBehaviourActions(BehaviourEvent.DAMAGE_TAKEN, merged);
        }
        ConfigurationSection custom = configuration.getConfigurationSection("custom-event-behaviours");
        if (custom != null) {
            for (String encodedName : custom.getKeys(false)) {
                String eventName;
                try {
                    eventName = decodeEventName(encodedName);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning(() -> "Ignoring malformed custom event name in " + file.getName());
                    continue;
                }
                definition.setCustomEventActions(eventName,
                        decodeActions(custom.getMapList(encodedName), file, "custom-event"));
            }
        }
        return definition;
    }

    private List<BehaviourAction> decodeActions(List<Map<?, ?>> storedActions, File file, String kind) {
        List<BehaviourAction> actions = new ArrayList<>();
        for (Map<?, ?> entry : storedActions) {
            Object type = entry.get("type");
            if (type == null)
                continue;
            try {
                actions.add(BehaviourActionCodec.decode(entry));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger()
                        .warning(() -> "Ignoring unknown " + kind + " action '" + type + "' in " + file.getName());
            }
        }
        return actions;
    }

    static List<BehaviourAction> mergeDamageActions(List<BehaviourAction> attacked, List<BehaviourAction> damage) {
        List<BehaviourAction> merged = new ArrayList<>(attacked);
        merged.addAll(damage);
        return merged.subList(0, Math.min(7, merged.size()));
    }

    private static String encodeEventName(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decodeEventName(String value) {
        return new String(java.util.Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
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
