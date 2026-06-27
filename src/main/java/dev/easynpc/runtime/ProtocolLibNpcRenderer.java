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
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.util.SkinTextureUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class ProtocolLibNpcRenderer implements NpcRenderer {
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(2_000_000);

    private final Plugin plugin;
    private final double renderDistanceSquared;
    private final ProtocolManager protocolManager;
    private final Map<UUID, NpcInstance> renderedInstances = new ConcurrentHashMap<>();

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
        renderedInstances.put(instance.getId(), instance);
        try {
            send(player, playerInfoPacket(definition, instance));
            send(player, spawnPacket(instance));
            send(player, equipmentPacket(instance, definition));
            send(player, headRotationPacket(instance));
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
            profile.getProperties().put("textures", WrappedSignedProperty.fromValues("textures", texture, null));
        }
        PlayerInfoData infoData = new PlayerInfoData(
            profile,
            0,
            EnumWrappers.NativeGameMode.SURVIVAL,
            WrappedChatComponent.fromText(definition.getDisplayName())
        );
        packet.getPlayerInfoActions().writeSafely(0, EnumSet.of(
            EnumWrappers.PlayerInfoAction.ADD_PLAYER,
            EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME,
            EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
            EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
            EnumWrappers.PlayerInfoAction.UPDATE_LISTED
        ));
        packet.getPlayerInfoDataLists().writeSafely(1, List.of(infoData));
        packet.getPlayerInfoDataLists().writeSafely(0, List.of(infoData));
        return packet;
    }

    private PacketContainer spawnPacket(NpcInstance instance) {
        Location location = instance.getLocation();
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        packet.getIntegers().writeSafely(0, instance.getEntityId());
        packet.getUUIDs().writeSafely(0, instance.getId());
        packet.getDoubles().writeSafely(0, location.getX());
        packet.getDoubles().writeSafely(1, location.getY());
        packet.getDoubles().writeSafely(2, location.getZ());
        packet.getBytes().writeSafely(0, angle(location.getYaw()));
        packet.getBytes().writeSafely(1, angle(location.getPitch()));
        return packet;
    }

    private PacketContainer equipmentPacket(NpcInstance instance, NpcDefinition definition) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packet.getIntegers().writeSafely(0, instance.getEntityId());
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
        addEquipment(equipment, EnumWrappers.ItemSlot.MAINHAND, definition.getMainHand());
        addEquipment(equipment, EnumWrappers.ItemSlot.OFFHAND, definition.getOffHand());
        ItemStack[] armor = definition.getArmorContents();
        addEquipment(equipment, EnumWrappers.ItemSlot.FEET, armor[0]);
        addEquipment(equipment, EnumWrappers.ItemSlot.LEGS, armor[1]);
        addEquipment(equipment, EnumWrappers.ItemSlot.CHEST, armor[2]);
        addEquipment(equipment, EnumWrappers.ItemSlot.HEAD, armor[3]);
        packet.getSlotStackPairLists().writeSafely(0, equipment);
        return packet;
    }

    private PacketContainer headRotationPacket(NpcInstance instance) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        packet.getIntegers().writeSafely(0, instance.getEntityId());
        packet.getBytes().writeSafely(0, angle(instance.getLocation().getYaw()));
        return packet;
    }

    private void send(Player player, PacketContainer packet) {
        protocolManager.sendServerPacket(player, packet);
    }

    private boolean canSee(Player player, Location location) {
        if (player.getWorld() != location.getWorld()) {
            return false;
        }
        return player.getLocation().distanceSquared(location) <= renderDistanceSquared;
    }

    private byte angle(float degrees) {
        return (byte) Math.floorMod((int) (degrees * 256.0F / 360.0F), 256);
    }

    private String trimName(String name) {
        String plain = name == null || name.isBlank() ? "EasyNPC" : name;
        return plain.length() <= 16 ? plain : plain.substring(0, 16);
    }

    private void addEquipment(List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment, EnumWrappers.ItemSlot slot, ItemStack item) {
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            equipment.add(new Pair<>(slot, item));
        }
    }
}
