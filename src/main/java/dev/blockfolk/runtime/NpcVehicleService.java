package dev.blockfolk.runtime;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.NpcDefinitionRepository;

/**
 * Lets NPCs ride minecarts and boats.
 *
 * <p>
 * NPCs with "Board Vehicles" enabled climb into an empty minecart or boat they
 * walk or stand next to, including while following a route. The AI can also ask
 * an NPC to walk to a listed vehicle and get in, or to get out. Rides end
 * through the world itself: an activator rail ejects the rider, a broken
 * vehicle drops it, and a restart puts every NPC back on foot.
 *
 * <p>
 * Riding state is read from the entity every tick rather than stored, so any
 * way a ride ends (including other plugins) is noticed and the NPC's position,
 * navigator, and route resume from where it got out.
 */
public final class NpcVehicleService implements Listener {

    /**
     * How far beyond the NPC's hitbox an auto-boarding NPC reaches for a vehicle.
     */
    static final double AUTO_BOARD_REACH = 0.6;
    /** Horizontal distance at which an AI-requested boarding completes. */
    static final double REQUESTED_BOARD_RANGE = 2.0;
    /** Auto-boarding pauses this long after any ride ends. */
    static final long EXIT_COOLDOWN_TICKS = 3L * 20L;
    /** The vehicle just left stays off-limits until the NPC is this far from it. */
    static final double REBOARD_CLEARANCE = 4.0;
    /** A walk to a requested vehicle gives up after this long. */
    static final long REQUEST_TIMEOUT_TICKS = 30L * 20L;
    private static final long SYNC_INTERVAL_TICKS = 2L;
    private static final long SCAN_INTERVAL_TICKS = 10L;

    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final Map<UUID, UUID> ridingVehicles = new HashMap<>();
    private final Map<UUID, ExitRecord> lastExits = new HashMap<>();
    private final Map<UUID, BoardRequest> requests = new HashMap<>();
    private Predicate<NpcInstance> busy = instance -> false;
    private Predicate<NpcInstance> walking = instance -> false;
    private Consumer<NpcInstance> exitListener = instance -> {
    };
    private Consumer<NpcInstance> requestedBoardListener = instance -> {
    };
    private BiConsumer<NpcInstance, String> outcomeListener = (instance, outcome) -> {
    };
    private UUID boardingInstance;
    private BukkitTask task;
    private long currentTick;

    public NpcVehicleService(Plugin plugin, NpcDefinitionRepository definitions, NpcInstanceRegistry instances) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
    }

    /**
     * NPCs for which this returns true (for example, in combat) never auto-board.
     */
    public void setBusyCheck(Predicate<NpcInstance> busy) {
        this.busy = busy == null ? instance -> false : busy;
    }

    /**
     * Whether the NPC is still walking toward a destination; used for requested
     * boarding.
     */
    public void setWalkingCheck(Predicate<NpcInstance> walking) {
        this.walking = walking == null ? instance -> false : walking;
    }

    /** Called after a ride ended and the NPC stands at its dismount spot. */
    public void setExitListener(Consumer<NpcInstance> exitListener) {
        this.exitListener = exitListener == null ? instance -> {
        } : exitListener;
    }

    /** Called when an NPC got into the vehicle it was asked to walk to. */
    public void setRequestedBoardListener(Consumer<NpcInstance> requestedBoardListener) {
        this.requestedBoardListener = requestedBoardListener == null ? instance -> {
        } : requestedBoardListener;
    }

    /** Receives a short explanation when a requested boarding fails. */
    public void setOutcomeListener(BiConsumer<NpcInstance, String> outcomeListener) {
        this.outcomeListener = outcomeListener == null ? (instance, outcome) -> {
        } : outcomeListener;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        ridingVehicles.clear();
        lastExits.clear();
        requests.clear();
    }

    public Optional<Entity> vehicle(NpcInstance instance) {
        return instances.vehicle(instance);
    }

    public boolean isRiding(NpcInstance instance) {
        return instances.isRiding(instance);
    }

    /**
     * Gets into the vehicle now when it is within reach; otherwise remembers it so
     * the NPC boards on arrival. The caller starts the walk when this returns
     * {@link RequestResult#WALKING}.
     */
    public RequestResult requestBoard(NpcInstance instance, Entity vehicle) {
        requests.remove(instance.getId());
        if (instances.isRiding(instance))
            return RequestResult.ALREADY_RIDING;
        if (vehicle == null || !NpcVehicles.isTransport(vehicle) || !vehicle.isValid())
            return RequestResult.GONE;
        LivingEntity npc = instances.findEntity(instance).orElse(null);
        if (npc == null || npc.getWorld() != vehicle.getWorld())
            return RequestResult.GONE;
        if (withinRequestedRange(npc.getLocation(), vehicle.getLocation()))
            return board(instance, vehicle) ? RequestResult.BOARDED : RequestResult.REFUSED;
        requests.put(instance.getId(), new BoardRequest(vehicle.getUniqueId(), currentTick + REQUEST_TIMEOUT_TICKS));
        return RequestResult.WALKING;
    }

    /** Gets out of the current minecart or boat. Returns false when not riding. */
    public boolean exit(NpcInstance instance) {
        requests.remove(instance.getId());
        if (!instances.leaveVehicle(instance))
            return false;
        syncRiding(instance);
        return true;
    }

    /** Forgets runtime state for a removed NPC. */
    public void forget(NpcInstance instance) {
        ridingVehicles.remove(instance.getId());
        lastExits.remove(instance.getId());
        requests.remove(instance.getId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!NpcVehicles.isTransport(event.getVehicle()))
            return;
        // A passing cart must never carry off the invisible pathfinding mob.
        if (instances.isNavigationEntity(event.getEntered())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntered() instanceof Mannequin))
            return;
        NpcInstance instance = instances.findByEntityId(event.getEntered().getEntityId()).orElse(null);
        if (instance == null || instance.getId().equals(boardingInstance))
            return;
        // Vanilla minecarts and boats scoop up mobs they bump into. Only NPCs
        // that would board on their own accept that ride.
        if (!mayAutoBoard(instance, event.getVehicle(), event.getEntered().getLocation()))
            event.setCancelled(true);
    }

    private void tick() {
        currentTick++;
        if (currentTick % SYNC_INTERVAL_TICKS != 0L)
            return;
        boolean scan = currentTick % SCAN_INTERVAL_TICKS == 0L;
        Set<UUID> active = new HashSet<>();
        for (NpcInstance instance : instances.findActive()) {
            active.add(instance.getId());
            syncRiding(instance);
            tickRequest(instance);
            if (scan)
                autoBoard(instance);
        }
        ridingVehicles.keySet().retainAll(active);
        requests.keySet().retainAll(active);
        if (scan)
            lastExits.keySet().retainAll(active);
    }

    private void syncRiding(NpcInstance instance) {
        Entity vehicle = instances.vehicle(instance).orElse(null);
        UUID tracked = ridingVehicles.get(instance.getId());
        if (vehicle != null) {
            if (!vehicle.getUniqueId().equals(tracked)) {
                ridingVehicles.put(instance.getId(), vehicle.getUniqueId());
                boarded(instance, vehicle);
            }
            // Keep the stored position on the vehicle, so the AI, nearby
            // checks, and the next save see where the NPC actually is.
            instances.currentLocation(instance);
        } else if (tracked != null) {
            ridingVehicles.remove(instance.getId());
            exited(instance, tracked);
        }
    }

    private void boarded(NpcInstance instance, Entity vehicle) {
        // The navigator stays behind at the boarding spot; a fresh one is
        // created wherever the ride ends.
        instances.resetNavigation(instance);
        BoardRequest request = requests.remove(instance.getId());
        if (request != null && request.vehicleId().equals(vehicle.getUniqueId()))
            requestedBoardListener.accept(instance);
    }

    private void exited(NpcInstance instance, UUID vehicleId) {
        instances.currentLocation(instance);
        instances.resetNavigation(instance);
        lastExits.put(instance.getId(), new ExitRecord(vehicleId, currentTick));
        exitListener.accept(instance);
    }

    private void tickRequest(NpcInstance instance) {
        BoardRequest request = requests.get(instance.getId());
        if (request == null)
            return;
        Entity vehicle = Bukkit.getEntity(request.vehicleId());
        LivingEntity npc = instances.findEntity(instance).orElse(null);
        if (vehicle == null || !vehicle.isValid() || npc == null || npc.getWorld() != vehicle.getWorld()) {
            failRequest(instance, "the vehicle is no longer there");
            return;
        }
        if (withinRequestedRange(npc.getLocation(), vehicle.getLocation())) {
            // On success, boarded() consumes the request and ends the walk.
            if (!board(instance, vehicle))
                failRequest(instance, "the " + NpcVehicles.describe(vehicle) + " has no free seat");
            return;
        }
        if (currentTick > request.expiresAtTick() || !walking.test(instance))
            failRequest(instance, "the NPC could not reach the " + NpcVehicles.describe(vehicle));
    }

    private void failRequest(NpcInstance instance, String reason) {
        requests.remove(instance.getId());
        outcomeListener.accept(instance, "enter_vehicle failed: " + reason + ".");
    }

    private void autoBoard(NpcInstance instance) {
        if (ridingVehicles.containsKey(instance.getId()) || requests.containsKey(instance.getId()))
            return;
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null || !definition.isAutoBoardVehicles())
            return;
        LivingEntity npc = instances.findEntity(instance).orElse(null);
        // A seated NPC rides its sitting seat and stays put.
        if (npc == null || npc.isInsideVehicle())
            return;
        Location feet = npc.getLocation();
        npc.getWorld()
                .getNearbyEntities(npc.getBoundingBox().expand(AUTO_BOARD_REACH, 0.5, AUTO_BOARD_REACH),
                        NpcVehicles::isBoardable)
                .stream().filter(vehicle -> mayAutoBoard(instance, vehicle, feet))
                .min(Comparator.comparingDouble(vehicle -> vehicle.getLocation().distanceSquared(feet)))
                .ifPresent(vehicle -> board(instance, vehicle));
    }

    private boolean mayAutoBoard(NpcInstance instance, Entity vehicle, Location feet) {
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null || !definition.isAutoBoardVehicles() || busy.test(instance))
            return false;
        ExitRecord exit = lastExits.get(instance.getId());
        if (exit == null)
            return true;
        long ticksSinceExit = currentTick - exit.atTick();
        Entity left = Bukkit.getEntity(exit.vehicleId());
        boolean nearLeftVehicle = left != null && left.isValid() && left.getWorld() == feet.getWorld()
                && left.getLocation().distanceSquared(feet) <= REBOARD_CLEARANCE * REBOARD_CLEARANCE;
        if (ticksSinceExit >= EXIT_COOLDOWN_TICKS && !nearLeftVehicle) {
            // Walked away from the old vehicle: every vehicle is fair game again.
            lastExits.remove(instance.getId());
            return true;
        }
        return reboardAllowed(ticksSinceExit, nearLeftVehicle, vehicle.getUniqueId().equals(exit.vehicleId()));
    }

    /**
     * After a ride, boarding waits out a short cooldown, and the vehicle just left
     * stays off-limits while the NPC is still next to it. Otherwise an activator
     * rail would eject the NPC straight back into a stopped cart.
     */
    static boolean reboardAllowed(long ticksSinceExit, boolean nearLeftVehicle, boolean sameVehicle) {
        return ticksSinceExit >= EXIT_COOLDOWN_TICKS && !(nearLeftVehicle && sameVehicle);
    }

    private boolean board(NpcInstance instance, Entity vehicle) {
        boardingInstance = instance.getId();
        boolean boarded;
        try {
            boarded = instances.board(instance, vehicle);
        } finally {
            boardingInstance = null;
        }
        if (boarded)
            syncRiding(instance);
        return boarded;
    }

    static boolean withinRequestedRange(Location npc, Location vehicle) {
        double dx = npc.getX() - vehicle.getX();
        double dz = npc.getZ() - vehicle.getZ();
        return dx * dx + dz * dz <= REQUESTED_BOARD_RANGE * REQUESTED_BOARD_RANGE
                && Math.abs(npc.getY() - vehicle.getY()) <= 2.0;
    }

    public enum RequestResult {
        BOARDED, WALKING, ALREADY_RIDING, GONE, REFUSED
    }

    private record ExitRecord(UUID vehicleId, long atTick) {
    }

    private record BoardRequest(UUID vehicleId, long expiresAtTick) {
    }
}
