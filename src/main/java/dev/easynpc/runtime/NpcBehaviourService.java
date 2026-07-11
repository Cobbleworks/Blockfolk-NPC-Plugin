package dev.easynpc.runtime;

import dev.easynpc.model.BehaviourAction;
import dev.easynpc.model.BehaviourActionType;
import dev.easynpc.model.BehaviourEvent;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.model.WalkingSpeed;
import dev.easynpc.model.MovementProfile;
import dev.easynpc.repository.NpcDefinitionRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public final class NpcBehaviourService implements Listener {
    private static final double DIALOG_RANGE_SQUARED = 12.0 * 12.0;
    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final Set<UUID> lowHealthTriggered = new HashSet<>();
    private final Map<UUID, String> routeOverrides = new HashMap<>();
    private final Map<UUID, WalkingSpeed> speedOverrides = new HashMap<>();
    private final Map<UUID, Location> directNavigationTargets = new HashMap<>();
    private final Set<UUID> routePaused = new HashSet<>();
    private NpcCombatService combatService;
    private BukkitTask navigationTask;

    public NpcBehaviourService(Plugin plugin, NpcDefinitionRepository definitions, NpcInstanceRegistry instances) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
    }

    public void setCombatService(NpcCombatService combatService) { this.combatService = combatService; }

    public void start() {
        stop();
        navigationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDirectNavigation, 1L, 1L);
    }

    public void stop() {
        if (navigationTask != null) navigationTask.cancel();
        navigationTask = null;
        directNavigationTargets.clear();
        routePaused.clear();
    }

    public void trigger(BehaviourEvent event, NpcInstance instance, Entity actor) {
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) return;
        for (BehaviourAction action : definition.getBehaviourActions(event)) execute(action, instance, definition, actor);
    }

    public void forget(NpcInstance instance) {
        lowHealthTriggered.remove(instance.getId());
        routeOverrides.remove(instance.getId());
        speedOverrides.remove(instance.getId());
        directNavigationTargets.remove(instance.getId());
        routePaused.remove(instance.getId());
    }

    public MovementProfile movementFor(NpcInstance instance, NpcDefinition definition) {
        String route = routeOverrides.get(instance.getId());
        WalkingSpeed speed = speedOverrides.getOrDefault(instance.getId(), definition.getMovementProfile().walkingSpeed());
        if (routePaused.contains(instance.getId()) || directNavigationTargets.containsKey(instance.getId()))
            return MovementProfile.disabled().withWalkingSpeed(speed);
        return route == null ? definition.getMovementProfile().withWalkingSpeed(speed)
            : new MovementProfile(true, route, speed);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        instances.findByEntityId(event.getEntity().getEntityId())
            .ifPresent(instance -> trigger(BehaviourEvent.LEFT_CLICK, instance, player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        NpcInstance instance = instances.findByEntityId(event.getEntity().getEntityId()).orElse(null);
        if (instance == null) return;
        Entity actor = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        trigger(BehaviourEvent.DAMAGE_TAKEN, instance, actor);
        Bukkit.getScheduler().runTask(plugin, () -> checkLowHealth(instance, actor));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        instances.findByEntityId(event.getEntity().getEntityId())
            .ifPresent(instance -> {
                trigger(BehaviourEvent.DEATH, instance, null);
                Bukkit.getScheduler().runTask(plugin, () -> forget(instance));
            });
    }

    private void checkLowHealth(NpcInstance instance, Entity actor) {
        LivingEntity entity = instances.findEntity(instance).orElse(null);
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (entity == null || definition == null || entity.isDead() || definition.getCombatProfile().invulnerable()) return;
        boolean low = entity.getHealth() <= definition.getCombatProfile().maxHealth() * 0.25;
        if (low && lowHealthTriggered.add(instance.getId())) trigger(BehaviourEvent.LOW_HEALTH, instance, actor);
        else if (!low) lowHealthTriggered.remove(instance.getId());
    }

    private void execute(BehaviourAction action, NpcInstance instance, NpcDefinition definition, Entity actor) {
        switch (action.type()) {
            case SEND_DIALOG -> sendDialog(instance, definition, action.value());
            case SET_ROUTE -> {
                if (action.value() != null) {
                    routeOverrides.put(instance.getId(), action.value());
                    directNavigationTargets.remove(instance.getId());
                    routePaused.remove(instance.getId());
                    instances.stopNavigating(instance);
                }
            }
            case RUN_CONSOLE_COMMAND -> {
                if (action.value() != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(action.value(), instance, definition, actor));
            }
            case START_COMBAT -> {
                if (combatService != null) combatService.startCombat(instance, actor);
            }
            case START_NAVIGATION -> {
                Location target = decodeLocation(action.value());
                if (target != null) {
                    routePaused.add(instance.getId());
                    directNavigationTargets.put(instance.getId(), target);
                    instances.stopNavigating(instance);
                }
            }
            case STOP_NAVIGATION -> {
                directNavigationTargets.remove(instance.getId());
                routePaused.add(instance.getId());
                instances.stopNavigating(instance);
            }
            case SET_WALK_SPEED -> speedOverrides.put(instance.getId(), WalkingSpeed.fromStored(action.value()));
        }
    }

    private void tickDirectNavigation() {
        Set<UUID> active = new HashSet<>();
        for (NpcInstance instance : instances.findAll()) {
            active.add(instance.getId());
            Location target = directNavigationTargets.get(instance.getId());
            if (target == null || combatService != null && combatService.isEngaged(instance)) continue;
            NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
            if (definition == null) {
                directNavigationTargets.remove(instance.getId());
                continue;
            }
            NativeNpcNavigationService.NavigationStatus status = instances.navigate(
                instance, target, movementFor(instance, definition).walkingSpeed());
            if (status == NativeNpcNavigationService.NavigationStatus.ARRIVED) {
                directNavigationTargets.remove(instance.getId());
                instances.stopNavigating(instance);
            }
        }
        directNavigationTargets.keySet().retainAll(active);
        routePaused.retainAll(active);
    }

    private void sendDialog(NpcInstance instance, NpcDefinition definition, String line) {
        if (line == null) return;
        Location location = instance.getLocation();
        Component message = Component.text(definition.getDisplayName() + ": " + line);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == location.getWorld() && player.getLocation().distanceSquared(location) <= DIALOG_RANGE_SQUARED)
                player.sendMessage(message);
        }
    }

    private String placeholders(String value, NpcInstance instance, NpcDefinition definition, Entity actor) {
        return value.replace("%npc%", definition.getDisplayName())
            .replace("%instance%", instance.getId().toString())
            .replace("%player%", actor instanceof Player player ? player.getName() : "");
    }

    private Location decodeLocation(String value) {
        if (value == null) return null;
        String[] parts = value.split(",", -1);
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try { return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])); }
        catch (NumberFormatException ignored) { return null; }
    }
}
