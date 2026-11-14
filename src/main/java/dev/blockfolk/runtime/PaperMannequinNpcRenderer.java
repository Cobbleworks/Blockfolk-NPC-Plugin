package dev.blockfolk.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import com.destroystokyo.paper.profile.ProfileProperty;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.NpcPose;
import io.papermc.paper.entity.TeleportFlag;
import dev.blockfolk.util.EntityHealth;
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
    private final NamespacedKey seatKey;
    private final NamespacedKey healthBarKey;
    private final HealthBar.Settings healthBars;
    private final Map<UUID, UUID> entityIdsByInstance = new HashMap<>();
    private final Map<UUID, Integer> jumpTicksByInstance = new HashMap<>();
    /** Health bar display entity per instance, for removing bars left behind. */
    private final Map<UUID, UUID> healthBarsByInstance = new HashMap<>();
    private BukkitTask animationTask;

    private static final int JUMP_DURATION_TICKS = 12;
    private static final double JUMP_HEIGHT = 0.85;
    private static final float HEALTH_BAR_SCALE = 0.8f;
    private static final int HEALTH_BAR_TELEPORT_TICKS = 3;
    /** Bottom of the lowest name tag line above the head. */
    private static final double HEALTH_BAR_BASE_OFFSET = 0.3;
    /** One name tag line: the name, or the description below it. */
    private static final double NAME_TAG_LINE_HEIGHT = 0.26;

    public PaperMannequinNpcRenderer(Plugin plugin, HealthBar.Settings healthBars) {
        this.plugin = plugin;
        this.instanceKey = new NamespacedKey(plugin, "instance-id");
        this.seatKey = new NamespacedKey(plugin, "seat-instance-id");
        this.healthBarKey = new NamespacedKey(plugin, "health-bar-instance-id");
        this.healthBars = healthBars;
    }

    @Override
    public void start() {
        if (animationTask != null) {
            animationTask.cancel();
        }
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        tickJumps();
        tickHealthBars();
    }

    @Override
    public void stop() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        jumpTicksByInstance.clear();
        for (UUID displayId : healthBarsByInstance.values()) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null)
                display.remove();
        }
        healthBarsByInstance.clear();
        entityIdsByInstance.clear();
    }

    @Override
    public boolean spawn(NpcInstance instance, NpcDefinition definition) {
        Mannequin existing = findEntity(instance, true);
        if (existing != null) {
            // A mannequin saved inside a minecart or boat (for example after a crash)
            // starts out on foot, like every other restart.
            Entity vehicle = existing.getVehicle();
            if (NpcVehicles.isTransport(vehicle))
                existing.leaveVehicle();
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
                spawned.setCollidable(true);
                spawned.setInvulnerable(true);
                spawned.setSilent(true);
                spawned.setRemoveWhenFarAway(false);
            });
            entityIdsByInstance.put(instance.getId(), mannequin.getUniqueId());
            instance.setEntityId(mannequin.getEntityId());
            applyDefinition(mannequin, instance, definition, true);
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
        removeSeats(instance, mannequin);
        removeHealthBar(instance.getId());
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
        if (mannequin == null) {
            return false;
        }
        ArmorStand seat = seat(mannequin);
        boolean moved = seat == null
                ? mannequin.teleport(renderedLocation)
                : seat.teleport(renderedLocation, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        if (!moved)
            return false;
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
        ArmorStand seat = seat(mannequin);
        Location location = seat == null ? mannequin.getLocation() : seat.getLocation();
        location.setYaw(mannequin.getLocation().getYaw());
        location.setPitch(mannequin.getLocation().getPitch());
        return Optional.of(location.subtract(0.0, jumpOffset(instance.getId()), 0.0));
    }

    @Override
    public Optional<LivingEntity> findLivingEntity(NpcInstance instance) {
        return Optional.ofNullable(findEntity(instance));
    }

    @Override
    public Optional<Entity> vehicle(NpcInstance instance) {
        Mannequin mannequin = findEntity(instance);
        return mannequin == null ? Optional.empty() : Optional.ofNullable(transport(mannequin));
    }

    @Override
    public boolean board(NpcInstance instance, Entity vehicle) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin == null || !NpcVehicles.isTransport(vehicle) || !vehicle.isValid()
                || vehicle.getWorld() != mannequin.getWorld())
            return false;
        // The sitting pose rides an armor stand; it must be vacated first.
        removeSeats(instance, mannequin);
        if (mannequin.isInsideVehicle())
            return false;
        if (jumpTicksByInstance.remove(instance.getId()) != null)
            mannequin.setGravity(true);
        mannequin.setPose(Pose.STANDING, false);
        return vehicle.addPassenger(mannequin);
    }

    @Override
    public boolean leaveVehicle(NpcInstance instance) {
        Mannequin mannequin = findEntity(instance);
        return mannequin != null && transport(mannequin) != null && mannequin.leaveVehicle();
    }

    private static Entity transport(Mannequin mannequin) {
        Entity vehicle = mannequin.getVehicle();
        return NpcVehicles.isTransport(vehicle) ? vehicle : null;
    }

    @Override
    public void pose(NpcInstance instance, Pose pose) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin != null && Mannequin.validPoses().contains(pose)) {
            removeSeats(instance, mannequin);
            mannequin.setPose(pose, pose != Pose.STANDING);
        }
    }

    @Override
    public void sit(NpcInstance instance) {
        Mannequin mannequin = findEntity(instance);
        if (mannequin != null)
            applyPose(mannequin, instance, NpcPose.SITTING);
    }

    @Override
    public void stand(NpcInstance instance) {
        pose(instance, Pose.STANDING);
    }

    private ArmorStand seat(Mannequin mannequin) {
        return mannequin.getVehicle() instanceof ArmorStand stand
                && stand.getPersistentDataContainer().has(seatKey, PersistentDataType.STRING) ? stand : null;
    }

    private void removeSeats(NpcInstance instance, Mannequin mannequin) {
        ArmorStand mounted = mannequin == null ? null : seat(mannequin);
        Location base = mounted == null ? null : mounted.getLocation();
        if (mounted != null) {
            mannequin.leaveVehicle();
            mounted.remove();
            mannequin.teleport(base);
        }
        // Also removes a saved seat whose passenger died or was removed externally.
        if (instance.getLocation().getWorld() != null) {
            String id = instance.getId().toString();
            for (ArmorStand stand : instance.getLocation().getWorld().getEntitiesByClass(ArmorStand.class)) {
                if (id.equals(stand.getPersistentDataContainer().get(seatKey, PersistentDataType.STRING)))
                    stand.remove();
            }
        }
    }

    private void applyPose(Mannequin mannequin, NpcInstance instance, NpcPose pose) {
        if (pose != NpcPose.SITTING) {
            removeSeats(instance, mannequin);
            mannequin.setPose(pose.nativePose(), pose != NpcPose.STANDING);
            return;
        }
        mannequin.setPose(Pose.STANDING, true);
        // A minecart or boat rider already sits; a second seat cannot be added.
        if (seat(mannequin) != null || transport(mannequin) != null)
            return;
        removeSeats(instance, mannequin);
        ArmorStand stand = mannequin.getWorld().spawn(instance.getLocation(), ArmorStand.class, spawned -> {
            spawned.setVisible(false);
            spawned.setMarker(true);
            spawned.setSmall(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setPersistent(mannequin.isPersistent());
            spawned.getPersistentDataContainer().set(seatKey, PersistentDataType.STRING, instance.getId().toString());
        });
        if (!stand.addPassenger(mannequin)) {
            stand.remove();
            throw new IllegalStateException("Could not seat NPC " + instance.getId());
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
        Mannequin mannequin = findEntity(instance);
        if (mannequin != null && !mannequin.isInsideVehicle() && mannequin.isOnGround()
                && !jumpTicksByInstance.containsKey(instance.getId())) {
            jumpTicksByInstance.putIfAbsent(instance.getId(), 0);
            // The scripted jump supplies its own vertical motion for 12 ticks.
            mannequin.setGravity(false);
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

    private void tickHealthBars() {
        Set<UUID> shown = new HashSet<>();
        if (healthBars.enabled()) {
            for (Map.Entry<UUID, UUID> entry : entityIdsByInstance.entrySet()) {
                if (Bukkit.getEntity(entry.getValue()) instanceof Mannequin mannequin && mannequin.isValid()
                        && updateHealthBar(entry.getKey(), mannequin))
                    shown.add(entry.getKey());
            }
        }
        // Full health, dead, unloaded, or removed NPCs lose their bar.
        healthBarsByInstance.entrySet().removeIf(entry -> {
            if (shown.contains(entry.getKey()))
                return false;
            Entity display = Bukkit.getEntity(entry.getValue());
            if (display != null)
                display.remove();
            return true;
        });
    }

    /** Shows, moves, and updates the bar. Returns false when none should show. */
    private boolean updateHealthBar(UUID instanceId, Mannequin mannequin) {
        int filled = mannequin.isInvulnerable() || mannequin.isDead()
                ? HealthBar.HIDDEN
                : HealthBar.filledSegments(mannequin.getHealth(), EntityHealth.maximum(mannequin));
        if (filled == HealthBar.HIDDEN)
            return false;
        Location target = healthBarLocation(mannequin);
        UUID displayId = healthBarsByInstance.get(instanceId);
        Entity existing = displayId == null ? null : Bukkit.getEntity(displayId);
        TextDisplay bar = existing instanceof TextDisplay display && display.isValid()
                && display.getWorld() == mannequin.getWorld() ? display : null;
        if (bar == null) {
            if (existing != null)
                existing.remove();
            bar = spawnHealthBar(instanceId, target);
            healthBarsByInstance.put(instanceId, bar.getUniqueId());
        } else if (bar.getLocation().distanceSquared(target) > 1.0E-4) {
            bar.teleport(target);
        }
        Component text = HealthBar.text(filled);
        if (!text.equals(bar.text()))
            bar.text(text);
        return true;
    }

    /**
     * Just above the NPC's name tag lines. The bar must not ride the NPC: the
     * client hides the name tag of any entity that carries a passenger, which would
     * hide the name, dialog lines, and the AI thinking indicator.
     */
    private static Location healthBarLocation(Mannequin mannequin) {
        int lines = (mannequin.customName() != null ? 1 : 0) + (mannequin.getDescription() != null ? 1 : 0);
        Location location = mannequin.getLocation();
        location.setYaw(0.0f);
        location.setPitch(0.0f);
        return location.add(0.0, mannequin.getHeight() + HEALTH_BAR_BASE_OFFSET + lines * NAME_TAG_LINE_HEIGHT, 0.0);
    }

    private TextDisplay spawnHealthBar(UUID instanceId, Location location) {
        return location.getWorld().spawn(location, TextDisplay.class, spawned -> {
            // Bars are rebuilt from the mannequin's health, so they never need saving.
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(healthBarKey, PersistentDataType.STRING, instanceId.toString());
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setShadowed(true);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setViewRange((float) (Math.max(1.0, healthBars.viewDistance()) / 64.0));
            // Smooths the per-tick follow moves like the client smooths the NPC itself.
            spawned.setTeleportDuration(HEALTH_BAR_TELEPORT_TICKS);
            spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(HEALTH_BAR_SCALE, HEALTH_BAR_SCALE, HEALTH_BAR_SCALE), new AxisAngle4f()));
            spawned.setSilent(true);
            spawned.setInvulnerable(true);
        });
    }

    private void removeHealthBar(UUID instanceId) {
        UUID displayId = healthBarsByInstance.remove(instanceId);
        Entity display = displayId == null ? null : Bukkit.getEntity(displayId);
        if (display != null)
            display.remove();
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
                mannequin.setGravity(true);
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
        removeSeats(instance, mannequin);
        // Teleporting a rider would pull it out of its minecart or boat; a
        // preset edit must not end the ride.
        if (transport(mannequin) == null)
            mannequin.teleport(instance.getLocation());
        mannequin.setPersistent(true);
        mannequin.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instance.getId().toString());
        mannequin.customName(Component.text(definition.getDisplayName()));
        mannequin.setCustomNameVisible(definition.isShowName());
        mannequin.setDescription(null);
        mannequin.setInvisible(false);
        mannequin.setImmovable(!definition.isPushable());
        // Also repairs mannequins spawned by older versions with NoGravity set.
        mannequin.setGravity(!jumpTicksByInstance.containsKey(instance.getId()));
        mannequin.setCollidable(true);
        mannequin.setRotation(instance.getLocation().getYaw(), instance.getLocation().getPitch());
        mannequin.setProfile(createProfile(instance, definition));
        AttributeInstance knockbackResistance = mannequin.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null) {
            knockbackResistance.setBaseValue(1.0);
        }
        applyEquipment(mannequin.getEquipment(), definition);
        applyCombatProfile(mannequin, definition, healToFull);
        applyPose(mannequin, instance, definition.getPose());
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
        ResolvableProfile.Builder profile = ResolvableProfile.resolvableProfile().uuid(instance.getId())
                .name(profileName(instance));
        String texture = definition.getSkinTextureValue();
        if (texture == null) {
            texture = SkinTextureUtil.toTextureProperty(definition.getSkinUrl());
        }
        if (texture != null) {
            String signature = definition.getSkinTextureSignature();
            profile.addProperty(signature == null
                    ? new ProfileProperty("textures", texture)
                    : new ProfileProperty("textures", texture, signature));
        }
        return profile.build();
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
