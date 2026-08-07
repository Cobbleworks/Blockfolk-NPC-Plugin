package dev.blockfolk.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.util.SkinTextureUtil;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;

/**
 * Renders NPCs with Paper's native player-shaped mannequin entity. Unlike a
 * packet-only fake player, the server owns and tracks this entity, so packet
 * layout changes cannot leave the client with a silently missing NPC.
 */
public final class PaperMannequinNpcRenderer implements NpcRenderer {

    private final Plugin plugin;
    private final NamespacedKey instanceKey;
    private final Map<UUID, UUID> entityIdsByInstance = new HashMap<>();
    private final Map<UUID, Integer> jumpTicksByInstance = new HashMap<>();
    private BukkitTask animationTask;

    private static final int JUMP_DURATION_TICKS = 12;
    private static final double JUMP_HEIGHT = 0.85;

    public PaperMannequinNpcRenderer(Plugin plugin) {
        this.plugin = plugin;
        this.instanceKey = new NamespacedKey(plugin, "instance-id");
    }

    @Override
    public void start() {
        if (animationTask != null) {
            animationTask.cancel();
        }
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickJumps, 1L, 1L);
    }

    @Override
    public void stop() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        jumpTicksByInstance.clear();
        entityIdsByInstance.clear();
    }

    @Override
    public boolean spawn(NpcInstance instance, NpcDefinition definition) {
        Mannequin existing = findEntity(instance, true);
        if (existing != null) {
            applyDefinition(existing, instance, definition, false);
            return true;
        }

        Location location = instance.getLocation();
        if (location.getWorld() == null) {
            plugin.getLogger().warning("Cannot render NPC " + definition.getKey() + ": its world is not loaded.");
            return false;
        }

        try {
            Mannequin mannequin = location.getWorld().spawn(location, Mannequin.class, spawned -> {
                spawned.setPersistent(true);
                spawned.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING,
                        instance.getId().toString());
                // Collision stays enabled so the mannequin remains a hittable entity.
                // The definition controls native player-bump movement separately.
                spawned.setImmovable(false);
                spawned.setAI(false);
                spawned.setGravity(false);
                spawned.setCollidable(true);
                spawned.setInvulnerable(true);
                spawned.setSilent(true);
                spawned.setRemoveWhenFarAway(false);
                applyDefinition(spawned, instance, definition, true);
            });
            entityIdsByInstance.put(instance.getId(), mannequin.getUniqueId());
            instance.setEntityId(mannequin.getEntityId());
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not render NPC " + definition.getKey(), exception);
            return false;
        }
    }

    @Override
    public void destroy(NpcInstance instance) {
        destroy(instance, false);
    }

    @Override
    public void destroyPermanently(NpcInstance instance) {
        destroy(instance, true);
    }

    private void destroy(NpcInstance instance, boolean loadChunk) {
        Mannequin mannequin = findEntity(instance, loadChunk);
        if (mannequin != null) {
            mannequin.remove();
        }
        entityIdsByInstance.remove(instance.getId());
        jumpTicksByInstance.remove(instance.getId());
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
        Location renderedLocation = location.clone().add(0.0, jumpOffset(instance.getId()), 0.0);
        if (mannequin == null || !mannequin.teleport(renderedLocation)) {
            return false;
        }
        mannequin.setRotation(location.getYaw(), location.getPitch());
        mannequin.setBodyYaw(location.getYaw());
        instance.setLocation(location);
        return true;
    }

    @Override
    public Optional<Location> currentLocation(NpcInstance instance) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin == null) {
            return Optional.empty();
        }
        return Optional.of(mannequin.getLocation().subtract(0.0, jumpOffset(instance.getId()), 0.0));
    }

    @Override
    public Optional<LivingEntity> findLivingEntity(NpcInstance instance) {
        return Optional.ofNullable(findEntity(instance));
    }

    @Override
    public void pose(NpcInstance instance, Pose pose) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin != null && Mannequin.validPoses().contains(pose)) {
            mannequin.setPose(pose, pose != Pose.STANDING);
        }
    }

    @Override
    public void stand(NpcInstance instance) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin != null) {
            mannequin.setPose(Pose.STANDING, false);
        }
    }

    @Override
    public void wave(NpcInstance instance) {
        for (long delay : new long[]{0L, 6L, 12L, 18L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Mannequin mannequin = findEntity(instance);
                if (mannequin != null && mannequin.isValid()) {
                    mannequin.swingMainHand();
                }
            }, delay);
        }
    }

    @Override
    public void jump(NpcInstance instance) {
        if (findEntity(instance) != null) {
            jumpTicksByInstance.putIfAbsent(instance.getId(), 0);
        }
    }

    @Override
    public void lookAt(NpcInstance instance, Location target) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin == null || target == null || target.getWorld() != mannequin.getWorld()) {
            return;
        }
        Location facing = mannequin.getLocation();
        facing.setDirection(target.toVector().subtract(mannequin.getEyeLocation().toVector()));
        float targetYaw = facing.getYaw();
        float targetPitch = Math.max(-60.0f, Math.min(60.0f, facing.getPitch()));
        float currentBodyYaw = mannequin.getBodyYaw();
        float bodyDelta = wrapDegrees(targetYaw - currentBodyYaw);
        float bodyStep = Math.max(-12.0f, Math.min(12.0f, bodyDelta * 0.35f));
        mannequin.setRotation(targetYaw, targetPitch);
        mannequin.setBodyYaw(currentBodyYaw + bodyStep);
    }

    private void tickJumps() {
        jumpTicksByInstance.replaceAll((instanceId, elapsed) -> elapsed + 1);
        jumpTicksByInstance.entrySet().removeIf(entry -> {
            // The renderer deliberately does not own the instance registry, so
            // use the tagged mannequin's current base location via the map below.
            UUID entityId = entityIdsByInstance.get(entry.getKey());
            if (entityId == null) {
                return true;
            }
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityId);
            if (!(entity instanceof Mannequin mannequin) || !mannequin.isValid()) {
                return true;
            }
            if (entry.getValue() >= JUMP_DURATION_TICKS) {
                Location landed = mannequin.getLocation().subtract(0.0, jumpOffset(entry.getValue() - 1), 0.0);
                mannequin.teleport(landed);
                return true;
            }
            double previousOffset = jumpOffset(entry.getValue() - 1);
            double nextOffset = jumpOffset(entry.getValue());
            mannequin.teleport(mannequin.getLocation().add(0.0, nextOffset - previousOffset, 0.0));
            return false;
        });
    }

    private double jumpOffset(UUID instanceId) {
        Integer elapsed = jumpTicksByInstance.get(instanceId);
        return elapsed == null ? 0.0 : jumpOffset(elapsed);
    }

    private double jumpOffset(int elapsed) {
        if (elapsed <= 0 || elapsed >= JUMP_DURATION_TICKS) {
            return 0.0;
        }
        return Math.sin(Math.PI * elapsed / JUMP_DURATION_TICKS) * JUMP_HEIGHT;
    }

    private Mannequin findEntity(NpcInstance instance) {
        return findEntity(instance, false);
    }

    private Mannequin findEntity(NpcInstance instance, boolean loadChunk) {
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

        if (!location.getChunk().isLoaded()) {
            if (!loadChunk)
                return null;
            // Spawn and permanent deletion are infrequent lifecycle operations;
            // routine lookups must never synchronously reload an idle NPC chunk.
            location.getChunk().load();
        }
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

    private void applyDefinition(Mannequin mannequin, NpcInstance instance, NpcDefinition definition,
            boolean healToFull) {
        mannequin.teleport(instance.getLocation());
        mannequin.setPersistent(true);
        mannequin.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instance.getId().toString());
        mannequin.customName(Component.text(definition.getDisplayName()));
        mannequin.setCustomNameVisible(definition.isShowName());
        mannequin.setDescription(null);
        mannequin.setInvisible(false);
        mannequin.setImmovable(!definition.isPushable());
        mannequin.setCollidable(true);
        mannequin.setRotation(instance.getLocation().getYaw(), instance.getLocation().getPitch());
        mannequin.setProfile(createProfile(instance, definition));
        AttributeInstance knockbackResistance = mannequin.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null) {
            knockbackResistance.setBaseValue(1.0);
        }
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

    private float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f)
            wrapped -= 360.0f;
        if (wrapped < -180.0f)
            wrapped += 360.0f;
        return wrapped;
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
