package dev.easynpc.runtime;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.model.WalkingSpeed;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Uses an invisible, goal-free mob as the native Minecraft navigator for a
 * mannequin. Mannequins are LivingEntity implementations and do not expose
 * Paper's Pathfinder API themselves.
 */
public final class NativeNpcNavigationService {
    private static final double NAVIGATOR_SCALE = 0.0625;
    private static final double ARRIVAL_HORIZONTAL_SQUARED = 0.8 * 0.8;
    private static final double ARRIVAL_VERTICAL = 1.5;
    private static final int REPATH_TICKS = 40;
    private static final int STUCK_TICKS = 5 * 20;

    private final Plugin plugin;
    private final NamespacedKey navigatorKey;
    private final Map<UUID, UUID> navigatorIdsByInstance = new HashMap<>();
    private final Map<UUID, NavigationState> states = new HashMap<>();
    private final Set<UUID> configuredNavigators = new HashSet<>();

    public NativeNpcNavigationService(Plugin plugin) {
        this.plugin = plugin;
        this.navigatorKey = new NamespacedKey(plugin, "navigator-instance-id");
    }

    public NavigationUpdate navigate(NpcInstance instance, Location target, WalkingSpeed walkingSpeed) {
        Husk navigator = findOrSpawn(instance);
        if (navigator == null) {
            return new NavigationUpdate(NavigationStatus.STALLED, instance.getLocation());
        }
        Location current = navigator.getLocation();
        if (target.getWorld() == null || current.getWorld() != target.getWorld()) {
            stop(instance);
            return new NavigationUpdate(NavigationStatus.STALLED, current);
        }

        double dx = target.getX() - current.getX();
        double dz = target.getZ() - current.getZ();
        if (dx * dx + dz * dz <= ARRIVAL_HORIZONTAL_SQUARED
            && Math.abs(target.getY() - current.getY()) <= ARRIVAL_VERTICAL) {
            navigator.getPathfinder().stopPathfinding();
            states.remove(instance.getId());
            return new NavigationUpdate(NavigationStatus.ARRIVED, current);
        }

        NavigationState state = states.computeIfAbsent(instance.getId(), ignored -> new NavigationState());
        boolean changed = !sameTarget(state.target, target) || state.walkingSpeed != walkingSpeed;
        if (changed) {
            state.target = target.clone();
            state.walkingSpeed = walkingSpeed;
            state.lastLocation = current.clone();
            state.stationaryTicks = 0;
            requestPath(navigator, target, walkingSpeed);
            state.retryTicks = REPATH_TICKS;
        } else {
            updateProgress(state, current);
            if (state.retryTicks <= 0
                && (!navigator.getPathfinder().hasPath() || state.stationaryTicks >= REPATH_TICKS)) {
                requestPath(navigator, target, walkingSpeed);
                state.retryTicks = REPATH_TICKS;
            }
        }
        if (state.retryTicks > 0) {
            state.retryTicks--;
        }
        boolean stuck = state.stationaryTicks >= STUCK_TICKS;
        return new NavigationUpdate(
            !stuck && navigator.getPathfinder().hasPath() ? NavigationStatus.MOVING : NavigationStatus.STALLED,
            current
        );
    }

    public void stop(NpcInstance instance) {
        Husk navigator = findNavigator(instance);
        if (navigator != null) {
            navigator.getPathfinder().stopPathfinding();
        }
        states.remove(instance.getId());
    }

    public void destroy(NpcInstance instance) {
        Husk navigator = findNavigator(instance);
        if (navigator != null) {
            navigator.remove();
            configuredNavigators.remove(navigator.getUniqueId());
        }
        navigatorIdsByInstance.remove(instance.getId());
        states.remove(instance.getId());
    }

    public boolean isNavigator(Entity entity) {
        return entity.getPersistentDataContainer().has(navigatorKey, PersistentDataType.STRING);
    }

    private void requestPath(Husk navigator, Location target, WalkingSpeed walkingSpeed) {
        configureSpeed(navigator, walkingSpeed);
        AttributeInstance followRange = navigator.getAttribute(Attribute.FOLLOW_RANGE);
        if (followRange != null) {
            double requiredRange = Math.sqrt(navigator.getLocation().distanceSquared(target)) + 16.0;
            followRange.setBaseValue(Math.max(64.0, Math.min(512.0, requiredRange)));
        }
        Pathfinder pathfinder = navigator.getPathfinder();
        Pathfinder.PathResult path = pathfinder.findPath(target);
        if (path != null) {
            pathfinder.moveTo(path, 1.0);
        } else {
            pathfinder.stopPathfinding();
        }
    }

    private Husk findOrSpawn(NpcInstance instance) {
        Husk existing = findNavigator(instance);
        if (existing != null) {
            if (configuredNavigators.add(existing.getUniqueId())) {
                configure(existing);
            }
            return existing;
        }
        Location location = instance.getLocation();
        if (location.getWorld() == null) {
            return null;
        }
        try {
            Husk spawned = location.getWorld().spawn(location, Husk.class, navigator -> {
                navigator.getPersistentDataContainer().set(
                    navigatorKey,
                    PersistentDataType.STRING,
                    instance.getId().toString()
                );
                configure(navigator);
            });
            navigatorIdsByInstance.put(instance.getId(), spawned.getUniqueId());
            configuredNavigators.add(spawned.getUniqueId());
            return spawned;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not create navigator for NPC " + instance.getId(), exception);
            return null;
        }
    }

    private Husk findNavigator(NpcInstance instance) {
        Location location = instance.getLocation();
        if (location.getWorld() == null) {
            return null;
        }
        UUID navigatorId = navigatorIdsByInstance.get(instance.getId());
        if (navigatorId != null && location.getWorld().getEntity(navigatorId) instanceof Husk navigator
            && navigator.isValid()) {
            return navigator;
        }
        navigatorIdsByInstance.remove(instance.getId());
        configuredNavigators.remove(navigatorId);

        location.getChunk().load();
        Husk found = null;
        String expectedId = instance.getId().toString();
        for (Husk navigator : location.getWorld().getEntitiesByClass(Husk.class)) {
            String taggedId = navigator.getPersistentDataContainer().get(navigatorKey, PersistentDataType.STRING);
            if (!expectedId.equals(taggedId)) {
                continue;
            }
            if (found == null) {
                found = navigator;
            } else {
                navigator.remove();
            }
        }
        if (found != null) {
            navigatorIdsByInstance.put(instance.getId(), found.getUniqueId());
        }
        return found;
    }

    private void configure(Husk navigator) {
        navigator.setPersistent(true);
        navigator.setRemoveWhenFarAway(false);
        navigator.setDespawnInPeacefulOverride(TriState.FALSE);
        navigator.setInvisible(true);
        navigator.setSilent(true);
        navigator.setInvulnerable(true);
        navigator.setCollidable(false);
        navigator.setCanPickupItems(false);
        navigator.setShouldBurnInDay(false);
        navigator.setCanBreakDoors(false);
        navigator.setAdult();
        navigator.setAI(true);
        navigator.setAware(true);
        navigator.setTarget(null);
        navigator.setCustomNameVisible(false);
        navigator.getEquipment().clear();
        navigator.clearLootTable();
        Bukkit.getMobGoals().removeAllGoals(navigator);
        AttributeInstance scale = navigator.getAttribute(Attribute.SCALE);
        if (scale != null) {
            // The husk only supplies pathfinding. Keeping its normal hitbox on
            // top of the mannequin makes sword swings hit this invulnerable,
            // invisible entity instead of the visible NPC.
            scale.setBaseValue(NAVIGATOR_SCALE);
        }
        Pathfinder pathfinder = navigator.getPathfinder();
        pathfinder.setCanOpenDoors(true);
        pathfinder.setCanPassDoors(true);
        pathfinder.setCanFloat(true);
    }

    private void configureSpeed(Husk navigator, WalkingSpeed walkingSpeed) {
        AttributeInstance movementSpeed = navigator.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            // Generic movement speed is measured in roughly blocks per tick.
            movementSpeed.setBaseValue(walkingSpeed.blocksPerSecond() / 20.0);
        }
    }

    private void updateProgress(NavigationState state, Location current) {
        if (state.lastLocation == null || state.lastLocation.getWorld() != current.getWorld()
            || state.lastLocation.distanceSquared(current) > 0.0025) {
            state.lastLocation = current.clone();
            state.stationaryTicks = 0;
        } else {
            state.stationaryTicks++;
        }
    }

    private boolean sameTarget(Location first, Location second) {
        return first != null
            && first.getWorld() == second.getWorld()
            && first.distanceSquared(second) < 0.0001;
    }

    public enum NavigationStatus {
        MOVING,
        ARRIVED,
        STALLED
    }

    public record NavigationUpdate(NavigationStatus status, Location location) {
    }

    private static final class NavigationState {
        private Location target;
        private Location lastLocation;
        private WalkingSpeed walkingSpeed;
        private int stationaryTicks;
        private int retryTicks;
    }
}
