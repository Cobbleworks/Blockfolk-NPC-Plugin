package dev.blockfolk.api;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.runtime.NativeNpcNavigationService;
import dev.blockfolk.runtime.NpcRenderer;

/**
 * A transient Blockfolk mannequin. Its owner controls combat and listens to
 * Bukkit damage/death events.
 */
public final class TransientNpc {

    private final UUID id = UUID.randomUUID();
    private final TransientNpcService service;
    private final Plugin owner;
    private final NpcRenderer renderer;
    private final NativeNpcNavigationService navigation;
    private final NpcDefinition definition;
    private NpcInstance instance;
    private Location target;
    private double speed = WalkingSpeed.NORMAL.blocksPerSecond();
    private ResolvableProfile profile;
    private long skinRevision;
    private boolean destroyed;

    TransientNpc(TransientNpcService service, Plugin owner, String name, NpcRenderer renderer,
            NativeNpcNavigationService navigation) {
        this.service = service;
        this.owner = owner;
        this.renderer = renderer;
        this.navigation = navigation;
        definition = new NpcDefinition("transient_" + id);
        definition.setDisplayName(name == null ? "" : name);
        definition.setShowName(false);
        // Immovable mannequins do not fall or process velocity in Minecraft.
        definition.setPushable(true);
        definition.setCombatProfile(definition.getCombatProfile().withMaxHealth(20));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return definition.getDisplayName();
    }

    Plugin owner() {
        return owner;
    }

    public LivingEntity getEntity() {
        return instance == null ? null : renderer.findLivingEntity(instance).orElse(null);
    }

    public boolean isSpawned() {
        LivingEntity entity = getEntity();
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public boolean spawn(Location location) {
        checkActive();
        if (location == null || location.getWorld() == null)
            throw new IllegalArgumentException("A loaded world is required.");
        if (isSpawned())
            return teleport(location);
        if (instance != null)
            despawn();
        instance = new NpcInstance(id, definition.getKey(), location);
        if (!renderer.spawn(instance, definition)) {
            instance = null;
            return false;
        }
        LivingEntity entity = getEntity();
        if (entity != null) {
            entity.setPersistent(false);
            entity.setCollidable(false);
            entity.setGravity(true);
            entity.setInvulnerable(false);
            if (entity instanceof Mannequin mannequin && profile != null)
                mannequin.setProfile(profile);
        }
        return true;
    }

    public void despawn() {
        TransientNpcService.requireMainThread();
        target = null;
        if (instance == null)
            return;
        LivingEntity entity = getEntity();
        if (entity != null)
            instance.setLocation(entity.getLocation());
        navigation.destroyPermanently(instance);
        renderer.destroyPermanently(instance);
        instance = null;
    }

    public void destroy() {
        if (destroyed)
            return;
        despawn();
        destroyed = true;
        skinRevision++;
        service.remove(this);
    }

    public boolean teleport(Location location) {
        checkActive();
        if (instance == null)
            return false;
        stopNavigating();
        navigation.destroyPermanently(instance);
        instance.setLocation(location);
        return renderer.move(instance, location);
    }

    public void navigate(Location destination, double blocksPerSecond) {
        checkActive();
        if (destination == null || destination.getWorld() == null || !Double.isFinite(blocksPerSecond)
                || blocksPerSecond <= 0)
            throw new IllegalArgumentException("Valid destination and positive speed required.");
        target = destination.clone();
        speed = blocksPerSecond;
    }

    public void stopNavigating() {
        TransientNpcService.requireMainThread();
        target = null;
        if (instance != null)
            navigation.stop(instance);
    }

    public void lookAt(Location location) {
        checkActive();
        if (instance != null)
            renderer.lookAt(instance, location);
    }

    public void setSkin(String name, String texture, String signature) {
        checkActive();
        long revision = ++skinRevision;
        if (texture != null && !texture.isBlank()) {
            ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile().uuid(id)
                    .name("NPC" + id.toString().substring(0, 13));
            builder.addProperty(signature == null || signature.isBlank()
                    ? new ProfileProperty("textures", texture)
                    : new ProfileProperty("textures", texture, signature));
            applyProfile(builder.build());
        } else if (name != null && !name.isBlank()) {
            ResolvableProfile.resolvableProfile().name(name).build().resolve().whenComplete((resolved, error) -> {
                if (!service.plugin().isEnabled())
                    return;
                service.plugin().getServer().getScheduler().runTask(service.plugin(), () -> {
                    if (destroyed || revision != skinRevision || !service.contains(this))
                        return;
                    if (error != null)
                        owner.getLogger().warning("Could not resolve NPC skin '" + name + "': " + error.getMessage());
                    else
                        applyProfile(ResolvableProfile.resolvableProfile(resolved));
                });
            });
        }
    }

    private void applyProfile(ResolvableProfile profile) {
        this.profile = profile;
        if (getEntity() instanceof Mannequin mannequin)
            mannequin.setProfile(profile);
    }

    public void animate(String animation) {
        checkActive();
        LivingEntity entity = getEntity();
        if (entity == null)
            return;
        switch (animation) {
            case "ARM_SWING" -> entity.swingMainHand();
            case "ARM_SWING_OFFHAND" -> entity.swingOffHand();
            case "START_USE_MAINHAND_ITEM" -> entity.startUsingItem(EquipmentSlot.HAND);
            case "START_USE_OFFHAND_ITEM" -> entity.startUsingItem(EquipmentSlot.OFF_HAND);
            case "STOP_USE_ITEM" -> entity.clearActiveItem();
            default -> throw new IllegalArgumentException("Unknown NPC animation: " + animation);
        }
    }

    void tick() {
        if (destroyed || instance == null || target == null || !isSpawned())
            return;
        LivingEntity entity = getEntity();
        instance.setLocation(entity.getLocation());
        NativeNpcNavigationService.NavigationUpdate update = navigation.navigate(instance, target, speed);
        navigation.setPersistent(instance, false);
        if (update.status() == NativeNpcNavigationService.NavigationStatus.ARRIVED) {
            if (update.location() != null)
                renderer.move(instance, update.location());
            stopNavigating();
            return;
        }
        if (update.status() == NativeNpcNavigationService.NavigationStatus.MOVING && update.location() != null
                && renderer.move(instance, update.location())) {
            instance.setLocation(update.location());
            LivingEntity moved = getEntity();
            if (moved != null)
                moved.setPersistent(false);
        }
    }

    private void checkActive() {
        TransientNpcService.requireMainThread();
        if (destroyed || !owner.isEnabled())
            throw new IllegalStateException("This NPC's owner is no longer active.");
    }
}
