package dev.easynpc.runtime;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.util.SkinTextureUtil;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Renders NPCs with Paper's native player-shaped mannequin entity. Unlike a
 * packet-only fake player, the server owns and tracks this entity, so packet
 * layout changes cannot leave the client with a silently missing NPC.
 */
public final class PaperMannequinNpcRenderer implements NpcRenderer {
    private final Plugin plugin;
    private final NamespacedKey instanceKey;
    private final Map<UUID, UUID> entityIdsByInstance = new HashMap<>();

    public PaperMannequinNpcRenderer(Plugin plugin) {
        this.plugin = plugin;
        this.instanceKey = new NamespacedKey(plugin, "instance-id");
    }

    @Override
    public void start() {
        // Native entities need no packet listener or renderer lifecycle.
    }

    @Override
    public void stop() {
        entityIdsByInstance.clear();
    }

    @Override
    public void spawn(NpcInstance instance, NpcDefinition definition) {
        Mannequin existing = findEntity(instance);
        if (existing != null) {
            applyDefinition(existing, instance, definition, false);
            return;
        }

        Location location = instance.getLocation();
        if (location.getWorld() == null) {
            plugin.getLogger().warning("Cannot render NPC " + definition.getKey() + ": its world is not loaded.");
            return;
        }

        try {
            Mannequin mannequin = location.getWorld().spawn(location, Mannequin.class, spawned -> {
                spawned.setPersistent(true);
                spawned.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instance.getId().toString());
                spawned.setImmovable(true);
                spawned.setAI(false);
                spawned.setGravity(false);
                spawned.setCollidable(false);
                spawned.setInvulnerable(true);
                spawned.setSilent(true);
                spawned.setRemoveWhenFarAway(false);
                applyDefinition(spawned, instance, definition, true);
            });
            entityIdsByInstance.put(instance.getId(), mannequin.getUniqueId());
            instance.setEntityId(mannequin.getEntityId());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not render NPC " + definition.getKey(), exception);
        }
    }

    @Override
    public void destroy(NpcInstance instance) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin != null) {
            mannequin.remove();
        }
        entityIdsByInstance.remove(instance.getId());
        instance.setEntityId(0);
    }

    @Override
    public void refresh(NpcInstance instance, NpcDefinition definition) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin == null) {
            spawn(instance, definition);
            return;
        }
        applyDefinition(mannequin, instance, definition, false);
    }

    @Override
    public boolean move(NpcInstance instance, Location location) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin == null || !mannequin.teleport(location)) {
            return false;
        }
        mannequin.setRotation(location.getYaw(), location.getPitch());
        mannequin.setBodyYaw(location.getYaw());
        instance.setLocation(location);
        return true;
    }

    @Override
    public Optional<LivingEntity> findLivingEntity(NpcInstance instance) {
        return Optional.ofNullable(findEntity(instance));
    }

    private Mannequin findEntity(NpcInstance instance) {
        UUID entityUuid = entityIdsByInstance.get(instance.getId());
        Location location = instance.getLocation();
        if (location.getWorld() == null) {
            return null;
        }
        if (entityUuid != null && location.getWorld().getEntity(entityUuid) instanceof Mannequin mannequin
            && mannequin.isValid()) {
            return mannequin;
        }
        entityIdsByInstance.remove(instance.getId());

        // Loading the saved chunk also loads any tagged mannequin left behind
        // by an unclean shutdown, allowing it to be adopted instead of cloned.
        location.getChunk().load();
        Mannequin found = null;
        for (Mannequin mannequin : location.getWorld().getEntitiesByClass(Mannequin.class)) {
            String taggedId = mannequin.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
            if (!instance.getId().toString().equals(taggedId)) {
                continue;
            }
            if (found == null) {
                found = mannequin;
            } else {
                mannequin.remove();
            }
        }
        if (found != null) {
            entityIdsByInstance.put(instance.getId(), found.getUniqueId());
            instance.setEntityId(found.getEntityId());
        }
        return found;
    }

    private void applyDefinition(
        Mannequin mannequin,
        NpcInstance instance,
        NpcDefinition definition,
        boolean healToFull
    ) {
        mannequin.teleport(instance.getLocation());
        mannequin.setPersistent(true);
        mannequin.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instance.getId().toString());
        mannequin.customName(Component.text(definition.getDisplayName()));
        mannequin.setCustomNameVisible(true);
        mannequin.setDescription(null);
        mannequin.setInvisible(false);
        mannequin.setRotation(instance.getLocation().getYaw(), instance.getLocation().getPitch());
        mannequin.setProfile(createProfile(instance, definition));
        applyEquipment(mannequin.getEquipment(), definition);
        applyCombatProfile(mannequin, definition, healToFull);
    }

    private void applyCombatProfile(Mannequin mannequin, NpcDefinition definition, boolean healToFull) {
        int configuredHealth = definition.getCombatProfile().maxHealth();
        boolean invulnerable = configuredHealth == 0;
        mannequin.setInvulnerable(invulnerable);

        AttributeInstance maxHealth = mannequin.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double previousHealth = mannequin.getHealth();
        if (!invulnerable) {
            maxHealth.setBaseValue(configuredHealth);
            mannequin.setHealth(healToFull ? configuredHealth : Math.min(previousHealth, configuredHealth));
        } else if (previousHealth <= 0.0) {
            mannequin.setHealth(maxHealth.getValue());
        }
    }

    private ResolvableProfile createProfile(NpcInstance instance, NpcDefinition definition) {
        PlayerProfile profile = Bukkit.createProfileExact(instance.getId(), profileName(instance));
        String texture = definition.getSkinTextureValue();
        if (texture == null) {
            texture = SkinTextureUtil.toTextureProperty(definition.getSkinUrl());
        }
        if (texture != null) {
            String signature = definition.getSkinTextureSignature();
            profile.setProperty(signature == null
                ? new ProfileProperty("textures", texture)
                : new ProfileProperty("textures", texture, signature));
        }
        return ResolvableProfile.resolvableProfile(profile);
    }

    private String profileName(NpcInstance instance) {
        return "NPC" + instance.getId().toString().replace("-", "").substring(0, 13);
    }

    private void applyEquipment(EntityEquipment equipment, NpcDefinition definition) {
        equipment.clear();
        equipment.setItemInMainHand(orAir(definition.getMainHand()), true);
        equipment.setItemInOffHand(orAir(definition.getOffHand()), true);
        ItemStack[] armor = definition.getArmorContents();
        equipment.setBoots(orAir(armor[0]), true);
        equipment.setLeggings(orAir(armor[1]), true);
        equipment.setChestplate(orAir(armor[2]), true);
        equipment.setHelmet(orAir(armor[3]), true);
    }

    private ItemStack orAir(ItemStack item) {
        return item == null ? ItemStack.empty() : item;
    }
}
