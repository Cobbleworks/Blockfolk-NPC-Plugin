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

    private void tick() {
        Set<UUID> activeInstances = new HashSet<>();
        for (NpcInstance instance : instanceRegistry.findAll()) {
            activeInstances.add(instance.getId());
            move(instance);
        }
        progressByInstance.keySet().retainAll(activeInstances);
    }

    private void move(NpcInstance instance) {
        if (combatService.isEngaged(instance)) {
            // Combat temporarily owns navigation. Keep the route target so the
            // NPC resumes toward the same waypoint instead of rebuilding the
            // route from its post-combat position.
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
            progress = new Progress(route.getKey(), sourcePoints, ordered, 0, 0L);
            progressByInstance.put(instance.getId(), progress);
        }

        RoutePoint targetPoint = progress.orderedPoints().get(progress.targetIndex());
        if (progress.waitUntilNanos() > 0) {
            if (System.nanoTime() < progress.waitUntilNanos()) {
                return;
            }
            progress = progress.withTargetIndex((progress.targetIndex() + 1) % progress.orderedPoints().size());
            progressByInstance.put(instance.getId(), progress);
            targetPoint = progress.orderedPoints().get(progress.targetIndex());
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
        if (status == NativeNpcNavigationService.NavigationStatus.ARRIVED && targetPoint.isWaitingPoint()) {
            instanceRegistry.stopNavigating(instance);
            progressByInstance.put(instance.getId(), progress.withWaitUntilNanos(
                    System.nanoTime() + targetPoint.waitMillis() * 1_000_000L));
        } else if (status == NativeNpcNavigationService.NavigationStatus.ARRIVED
                || status == NativeNpcNavigationService.NavigationStatus.STALLED) {
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
            long waitUntilNanos
            ) {

        private Progress {
            sourcePoints = List.copyOf(sourcePoints);
            orderedPoints = List.copyOf(orderedPoints);
        }

        boolean matches(String candidateRouteKey, List<RoutePoint> candidatePoints) {
            return routeKey.equals(candidateRouteKey) && sourcePoints.equals(candidatePoints);
        }

        Progress withTargetIndex(int targetIndex) {
            return new Progress(routeKey, sourcePoints, orderedPoints, targetIndex, 0L);
        }

        Progress withWaitUntilNanos(long waitUntilNanos) {
            return new Progress(routeKey, sourcePoints, orderedPoints, targetIndex, waitUntilNanos);
        }
    }
}
