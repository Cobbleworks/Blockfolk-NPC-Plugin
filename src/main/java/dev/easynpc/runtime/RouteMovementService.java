package dev.easynpc.runtime;

import dev.easynpc.model.MovementProfile;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.model.NpcRoute;
import dev.easynpc.model.RoutePoint;
import dev.easynpc.repository.NpcDefinitionRepository;
import dev.easynpc.repository.RouteRepository;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RouteMovementService {
    private final JavaPlugin plugin;
    private final NpcDefinitionRepository definitionRepository;
    private final RouteRepository routeRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final Map<UUID, Progress> progressByInstance = new HashMap<>();
    private BukkitTask task;

    public RouteMovementService(
        JavaPlugin plugin,
        NpcDefinitionRepository definitionRepository,
        RouteRepository routeRepository,
        NpcInstanceRegistry instanceRegistry
    ) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.routeRepository = routeRepository;
        this.instanceRegistry = instanceRegistry;
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
        NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            progressByInstance.remove(instance.getId());
            return;
        }
        MovementProfile movement = definition.getMovementProfile();
        if (!movement.enabled()) {
            progressByInstance.remove(instance.getId());
            return;
        }
        NpcRoute route = routeRepository.find(movement.routeKey()).orElse(null);
        if (route == null || route.getPoints().isEmpty()) {
            progressByInstance.remove(instance.getId());
            return;
        }

        Progress progress = progressByInstance.get(instance.getId());
        List<RoutePoint> sourcePoints = route.getPoints();
        if (progress == null || !progress.matches(route.getKey(), sourcePoints)) {
            List<RoutePoint> ordered = route.logicallyOrdered(instance.getLocation());
            progress = new Progress(route.getKey(), sourcePoints, ordered, 0);
            progressByInstance.put(instance.getId(), progress);
        }

        RoutePoint targetPoint = progress.orderedPoints().get(progress.targetIndex());
        Location target = targetPoint.toWalkingLocation();
        Location current = instance.getLocation();
        if (target == null || current.getWorld() == null || !current.getWorld().equals(target.getWorld())) {
            return;
        }

        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double blocksPerTick = Math.max(0.01,
            plugin.getConfig().getDouble("route-movement-blocks-per-second", 2.0) / 20.0);
        boolean reached = distance <= blocksPerTick;
        Location next = reached ? target.clone() : current.clone().add(
            dx / distance * blocksPerTick,
            dy / distance * blocksPerTick,
            dz / distance * blocksPerTick
        );
        if (distance > 0.0001) {
            next.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            next.setPitch(0.0f);
        }
        if (instanceRegistry.move(instance, next) && reached) {
            int nextIndex = (progress.targetIndex() + 1) % progress.orderedPoints().size();
            progressByInstance.put(instance.getId(), progress.withTargetIndex(nextIndex));
        }
    }

    private record Progress(
        String routeKey,
        List<RoutePoint> sourcePoints,
        List<RoutePoint> orderedPoints,
        int targetIndex
    ) {
        private Progress {
            sourcePoints = List.copyOf(sourcePoints);
            orderedPoints = List.copyOf(orderedPoints);
        }

        boolean matches(String candidateRouteKey, List<RoutePoint> candidatePoints) {
            return routeKey.equals(candidateRouteKey) && sourcePoints.equals(candidatePoints);
        }

        Progress withTargetIndex(int targetIndex) {
            return new Progress(routeKey, sourcePoints, orderedPoints, targetIndex);
        }
    }
}
