package dev.easynpc.runtime;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.util.SkinTextureUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class ProtocolLibNpcRenderer implements NpcRenderer {
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(2_000_000);
    private static final Field PROFILE_PROPERTY_MAP = findProfilePropertyMap();

    private final Plugin plugin;
    private final double renderDistanceSquared;
    private final ProtocolManager protocolManager;
    private final Map<UUID, NpcInstance> renderedInstances = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> viewersByInstance = new ConcurrentHashMap<>();

    public ProtocolLibNpcRenderer(Plugin plugin, double renderDistance) {
        this.plugin = plugin;
        this.renderDistanceSquared = renderDistance * renderDistance;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @Override
    public void start() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                int entityId = event.getPacket().getIntegers().readSafely(0);
                renderedInstances.values().stream()
                    .filter(instance -> instance.getEntityId() == entityId)
                    .findFirst()
                    .ifPresent(instance -> event.getPlayer().sendMessage("Talking to NPC copy " + instance.getId()));
            }
        });
    }

    @Override
    public void stop() {
        protocolManager.removePacketListeners(plugin);
    }

    @Override
    public void spawnFor(Player player, NpcInstance instance, NpcDefinition definition) {
        if (!canSee(player, instance.getLocation())) {
            destroyFor(player, instance);
            return;
        }
        if (instance.getEntityId() == 0) {
            instance.setEntityId(ENTITY_IDS.incrementAndGet());
        }
        if (isRenderedFor(player, instance)) {
            return;
        }
        renderedInstances.put(instance.getId(), instance);
        try {
            send(player, playerInfoPacket(definition, instance));
            send(player, spawnPacket(instance));
            send(player, metadataPacket(instance));
            PacketContainer equipment = equipmentPacket(instance, definition);
            if (equipment != null) {
                send(player, equipment);
            }
            PacketContainer headRotation = headRotationPacket(instance);
            if (headRotation != null) {
                send(player, headRotation);
            }
            viewersByInstance.computeIfAbsent(instance.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not render NPC " + definition.getKey() + " for " + player.getName(), exception);
        }
    }

    @Override
    public void destroyFor(Player player, NpcInstance instance) {
        try {
            PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().writeSafely(0, List.of(instance.getEntityId()));
            send(player, destroy);

            PacketContainer removeInfo = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            removeInfo.getUUIDLists().writeSafely(0, List.of(instance.getId()));
            send(player, removeInfo);
            Set<UUID> viewers = viewersByInstance.get(instance.getId());
            if (viewers != null) {
                viewers.remove(player.getUniqueId());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Could not destroy NPC " + instance.getId() + " for " + player.getName(), exception);
        }
    }

    @Override
    public void destroyForAll(NpcInstance instance) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            destroyFor(player, instance);
        }
        renderedInstances.remove(instance.getId());
        viewersByInstance.remove(instance.getId());
    }

    @Override
    public void refresh(NpcInstance instance, NpcDefinition definition) {
        destroyForAll(instance);
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnFor(player, instance, definition);
        }
    }

    private PacketContainer playerInfoPacket(NpcDefinition definition, NpcInstance instance) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        WrappedGameProfile profile = new WrappedGameProfile(instance.getId(), trimName(definition.getDisplayName()));
        String texture = SkinTextureUtil.toTextureProperty(definition.getSkinUrl());
        if (texture != null) {
            applySkinProperty(profile, texture);
        }
        PlayerInfoData infoData = new PlayerInfoData(
            instance.getId(),
            0,
            false,
            EnumWrappers.NativeGameMode.SURVIVAL,
            profile,
            WrappedChatComponent.fromText(definition.getDisplayName())
        );
        EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.of(
            EnumWrappers.PlayerInfoAction.ADD_PLAYER,
            EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME,
            EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
            EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
            EnumWrappers.PlayerInfoAction.UPDATE_LISTED
        );
        PacketContainer constructed = constructPlayerInfoPacket(actions, List.of(infoData));
        if (constructed != null) {
            return constructed;
        }
        packet.getPlayerInfoActions().writeSafely(0, actions);
        packet.getPlayerInfoDataLists().writeSafely(0, new ArrayList<>(List.of(infoData)));
        return packet;
    }

    private PacketContainer constructPlayerInfoPacket(Set<EnumWrappers.PlayerInfoAction> actions, List<PlayerInfoData> entries) {
        try {
            Set<Object> nmsActions = createNmsPlayerInfoActions(actions);
            List<Object> nmsEntries = entries.stream()
                .map(PlayerInfoData.getConverter()::getGeneric)
                .toList();

            for (Constructor<?> constructor : PacketType.Play.Server.PLAYER_INFO.getPacketClass().getConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length != 2
                    || !EnumSet.class.isAssignableFrom(parameterTypes[0])
                    || !Collection.class.isAssignableFrom(parameterTypes[1])) {
                    continue;
                }
                Object handle = constructor.newInstance(nmsActions, nmsEntries);
                return new PacketContainer(PacketType.Play.Server.PLAYER_INFO, handle);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Could not construct immutable player info packet directly.", exception);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Set<Object> createNmsPlayerInfoActions(Set<EnumWrappers.PlayerInfoAction> actions) {
        Set<Object> nmsActions = (Set<Object>) (Object) EnumWrappers.createEmptyEnumSet(EnumWrappers.getPlayerInfoActionClass());
        for (EnumWrappers.PlayerInfoAction action : actions) {
            nmsActions.add(EnumWrappers.getPlayerInfoActionConverter().getGeneric(action));
        }
        return nmsActions;
    }

    private PacketContainer spawnPacket(NpcInstance instance) {
        Location location = instance.getLocation();
        PacketType spawnType = playerSpawnPacketType();
        PacketContainer packet = protocolManager.createPacket(spawnType);
        packet.getIntegers().writeSafely(0, instance.getEntityId());
        packet.getUUIDs().writeSafely(0, instance.getId());
        if (PacketType.Play.Server.SPAWN_ENTITY.equals(spawnType)) {
            packet.getEntityTypeModifier().writeSafely(0, EntityType.PLAYER);
        }
        packet.getDoubles().writeSafely(0, location.getX());
        packet.getDoubles().writeSafely(1, location.getY());
        packet.getDoubles().writeSafely(2, location.getZ());
        packet.getBytes().writeSafely(0, angle(location.getPitch()));
        packet.getBytes().writeSafely(1, angle(location.getYaw()));
        packet.getBytes().writeSafely(2, angle(location.getYaw()));
        return packet;
    }

    private PacketContainer metadataPacket(NpcInstance instance) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().writeSafely(0, instance.getEntityId());
        List<WrappedDataValue> values = new ArrayList<>();
        values.add(new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x7F));
        packet.getDataValueCollectionModifier().writeSafely(0, values);
        return packet;
    }

    private PacketContainer equipmentPacket(NpcInstance instance, NpcDefinition definition) {
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
        addEquipment(equipment, EnumWrappers.ItemSlot.MAINHAND, definition.getMainHand());
        addEquipment(equipment, EnumWrappers.ItemSlot.OFFHAND, definition.getOffHand());
        ItemStack[] armor = definition.getArmorContents();
        addEquipment(equipment, EnumWrappers.ItemSlot.FEET, armor[0]);
        addEquipment(equipment, EnumWrappers.ItemSlot.LEGS, armor[1]);
        addEquipment(equipment, EnumWrappers.ItemSlot.CHEST, armor[2]);
        addEquipment(equipment, EnumWrappers.ItemSlot.HEAD, armor[3]);
        if (equipment.isEmpty()) {
            return null;
        }

        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packet.getIntegers().writeSafely(0, instance.getEntityId());
        packet.getSlotStackPairLists().writeSafely(0, equipment);
        return packet;
    }

    private PacketContainer headRotationPacket(NpcInstance instance) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            packet.getIntegers().writeSafely(0, instance.getEntityId());
            packet.getBytes().writeSafely(0, angle(instance.getLocation().getYaw()));
            return packet;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Skipping optional NPC head rotation packet.", exception);
            return null;
        }
    }

    private void send(Player player, PacketContainer packet) {
        protocolManager.sendServerPacket(player, packet);
    }

    private boolean canSee(Player player, Location location) {
        if (!player.getWorld().equals(location.getWorld())) {
            return false;
        }
        return player.getLocation().distanceSquared(location) <= renderDistanceSquared;
    }

    private boolean isRenderedFor(Player player, NpcInstance instance) {
        Set<UUID> viewers = viewersByInstance.get(instance.getId());
        return viewers != null && viewers.contains(player.getUniqueId());
    }

    private byte angle(float degrees) {
        return (byte) Math.floorMod((int) (degrees * 256.0F / 360.0F), 256);
    }

    private String trimName(String name) {
        String plain = name == null || name.isBlank() ? "EasyNPC" : name;
        return plain.length() <= 16 ? plain : plain.substring(0, 16);
    }

    private PacketType playerSpawnPacketType() {
        List<PacketType> packetTypes = new ArrayList<>();
        packetTypes.add(PacketType.Play.Server.SPAWN_ENTITY);
        packetTypes.add(findPacketType("SPAWN_PLAYER"));
        packetTypes.add(findPacketType("NAMED_ENTITY_SPAWN"));
        for (PacketType packetType : packetTypes) {
            if (packetType == null || !canCreatePacket(packetType)) {
                continue;
            }
            return packetType;
        }
        return PacketType.Play.Server.SPAWN_ENTITY;
    }

    private PacketType findPacketType(String fieldName) {
        try {
            Field field = PacketType.Play.Server.class.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof PacketType packetType) {
                return packetType;
            }
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
        }
        return null;
    }

    private boolean canCreatePacket(PacketType packetType) {
        try {
            protocolManager.createPacket(packetType);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Packet type " + packetType + " is not available on this server.", exception);
            return false;
        }
    }

    private void addEquipment(List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment, EnumWrappers.ItemSlot slot, ItemStack item) {
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            equipment.add(new Pair<>(slot, item));
        }
    }

    private void applySkinProperty(WrappedGameProfile profile, String texture) {
        WrappedSignedProperty property = WrappedSignedProperty.fromValues("textures", texture, null);
        if (PROFILE_PROPERTY_MAP != null) {
            try {
                Multimap<String, WrappedSignedProperty> properties = ArrayListMultimap.create();
                properties.put("textures", property);
                PROFILE_PROPERTY_MAP.set(profile, properties);
                return;
            } catch (IllegalAccessException exception) {
                plugin.getLogger().log(Level.FINE, "Could not set ProtocolLib profile property map directly.", exception);
            }
        }

        try {
            profile.getProperties().put("textures", property);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not apply NPC skin texture; rendering with default skin.", exception);
        }
    }

    private static Field findProfilePropertyMap() {
        try {
            Field field = WrappedGameProfile.class.getDeclaredField("propertyMap");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }
}
