package dev.blockfolk.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.MovementProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.RoutePoint;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.RouteRepository;

public final class RouteMovementService {

    private final JavaPlugin plugin;
    private final NpcDefinitionRepository definitionRepository;
    private final RouteRepository routeRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final NpcCombatService combatService;
    private final NpcBehaviourService behaviourService;
    private final Map<UUID, Progress> progressByInstance = new HashMap<>();
    private BukkitTask task;

    public RouteMovementService(
            JavaPlugin plugin,
            NpcDefinitionRepository definitionRepository,
            RouteRepository routeRepository,
            NpcInstanceRegistry instanceRegistry,
            NpcCombatService combatService,
            NpcBehaviourService behaviourService
    ) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.routeRepository = routeRepository;
        this.instanceRegistry = instanceRegistry;
        this.combatService = combatService;
        this.behaviourService = behaviourService;
    }

    public void start() {
        stop();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        progressByInstance.clear();
    }

    public void resetProgress(NpcInstance instance) {
        progressByInstance.remove(instance.getId());
        instanceRegistry.stopNavigating(instance);
    }

    private void tick() {
        Set<UUID> activeInstances = new HashSet<>();
        for (NpcInstance instance : instanceRegistry.findAll()) {
            activeInstances.add(instance.getId());
            move(instance);
        }
        progressByInstance.keySet().retainAll(activeInstances);
    }

    private void move(NpcInstance instance) {
        if (combatService.isEngaged(instance) || behaviourService.isFollowing(instance)
                || behaviourService.isMovingTo(instance)
                || behaviourService.isRunningWaypointActions(instance)) {
            // Another runtime activity temporarily owns navigation. Keep the
            // route target so the NPC resumes toward the same next waypoint.
            return;
        }
        if (behaviourService.isNavigationPaused(instance)) {
            // STOP_NAVIGATION is temporary state, not a disabled route. In
            // particular, a waypoint's stop/wait/start sequence has already
            // advanced targetIndex, and removing progress here would select
            // the waypoint just reached again when navigation resumes.
            return;
        }
        NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            stop(instance);
            return;
        }
        MovementProfile movement = behaviourService.movementFor(instance, definition);
        if (!movement.enabled()) {
            stop(instance);
            return;
        }
        NpcRoute route = routeRepository.find(movement.routeKey()).orElse(null);
        if (route == null || route.getPoints().isEmpty()) {
            stop(instance);
            return;
        }
        Location current = instance.getLocation();
        if (current.getWorld() == null
                || !route.getPoints().getFirst().worldName().equals(current.getWorld().getName())) {
            stop(instance);
            return;
        }

        Progress progress = progressByInstance.get(instance.getId());
        List<RoutePoint> sourcePoints = route.getPoints();
        if (progress == null || !progress.matches(route.getKey(), sourcePoints)) {
            List<RoutePoint> ordered = route.logicallyOrdered(instance.getLocation());
            progress = new Progress(route.getKey(), sourcePoints, ordered, 0, false);
            progressByInstance.put(instance.getId(), progress);
        }

        RoutePoint targetPoint = progress.orderedPoints().get(progress.targetIndex());
        if (progress.targetHandled()) {
            // A one-point route has no different next index. Keep its action
            // from firing every tick while the NPC remains on the point, but
            // allow the route to bring the NPC back after it is displaced.
            if (targetPoint.distanceSquared(current) <= 1.0) {
                return;
            }
            progress = progress.withTargetPending();
            progressByInstance.put(instance.getId(), progress);
        }
        Location target = targetPoint.toWalkingLocation();
        if (target == null || !current.getWorld().equals(target.getWorld())) {
            stop(instance);
            return;
        }

        NativeNpcNavigationService.NavigationStatus status = instanceRegistry.navigate(
                instance,
                target,
                movement.walkingSpeed()
        );
        if (status == NativeNpcNavigationService.NavigationStatus.ARRIVED) {
            instanceRegistry.stopNavigating(instance);
            int nextIndex = (progress.targetIndex() + 1) % progress.orderedPoints().size();
            progressByInstance.put(instance.getId(), progress.withTargetIndex(nextIndex));
            behaviourService.triggerWaypointActions(targetPoint.actions(), instance);
            behaviourService.trigger(dev.blockfolk.model.BehaviourEvent.ROUTE_POINT_REACHED,
                    instance, null, "The NPC reached a route waypoint.");
        } else if (status == NativeNpcNavigationService.NavigationStatus.STALLED) {
            int nextIndex = (progress.targetIndex() + 1) % progress.orderedPoints().size();
            progressByInstance.put(instance.getId(), progress.withTargetIndex(nextIndex));
        }
    }

    private void stop(NpcInstance instance) {
        if (progressByInstance.remove(instance.getId()) != null) {
            instanceRegistry.stopNavigating(instance);
        }
    }

    private record Progress(
            String routeKey,
            List<RoutePoint> sourcePoints,
            List<RoutePoint> orderedPoints,
            int targetIndex,
            boolean targetHandled
            ) {

        private Progress {
            sourcePoints = List.copyOf(sourcePoints);
            orderedPoints = List.copyOf(orderedPoints);
        }

        boolean matches(String candidateRouteKey, List<RoutePoint> candidatePoints) {
            return routeKey.equals(candidateRouteKey) && sourcePoints.equals(candidatePoints);
        }

        Progress withTargetIndex(int targetIndex) {
            return new Progress(routeKey, sourcePoints, orderedPoints, targetIndex, targetIndex == this.targetIndex);
        }

        Progress withTargetPending() {
            return new Progress(routeKey, sourcePoints, orderedPoints, targetIndex, false);
        }
    }
}
