package dev.blockfolk.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.dialog.DialogService;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.MovementProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.repository.NpcDefinitionRepository;
import net.kyori.adventure.text.Component;

public final class NpcBehaviourService implements Listener {

    private static final double DIALOG_RANGE_SQUARED = 12.0 * 12.0;
    private static final double APPROACH_RANGE_SQUARED = 8.0 * 8.0;
    private static final double LEAVE_RANGE_SQUARED = 10.0 * 10.0;
    private static final double HEAL_BURST_THRESHOLD = 4.0;
    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final DialogService dialogService;
    private final Set<UUID> lowHealthTriggered = new HashSet<>();
    private final Map<UUID, String> routeOverrides = new HashMap<>();
    private final Map<UUID, WalkingSpeed> speedOverrides = new HashMap<>();
    private final Set<UUID> routePaused = new HashSet<>();
    private final Set<ProximityKey> nearbyPlayers = new HashSet<>();
    private NpcCombatService combatService;
    private BukkitTask behaviourTask;
    private int proximityTick;

    public NpcBehaviourService(
            Plugin plugin,
            NpcDefinitionRepository definitions,
            NpcInstanceRegistry instances,
            DialogService dialogService
    ) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
        this.dialogService = dialogService;
    }

    public void setCombatService(NpcCombatService combatService) {
        this.combatService = combatService;
    }

    public void start() {
        stop();
        behaviourTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBehaviour, 1L, 1L);
    }

    public void stop() {
        if (behaviourTask != null) {
            behaviourTask.cancel();
        }
        behaviourTask = null;
        routePaused.clear();
        nearbyPlayers.clear();
    }

    public void trigger(BehaviourEvent event, NpcInstance instance, Entity actor) {
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            return;
        }
        long delayTicks = 0L;
        for (BehaviourAction action : definition.getBehaviourActions(event)) {
            if (delayTicks == 0L) {
                execute(event, action, instance, definition, actor);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> execute(event, action, instance, definition, actor), delayTicks);
            }
            if (action.type() == dev.blockfolk.model.BehaviourActionType.SEND_DIALOG
                    || action.type() == dev.blockfolk.model.BehaviourActionType.SHOW_HOLO_DIALOG) {
                delayTicks += dialogService.secondsPerLine() * 20L;
            }
        }
    }

    public void forget(NpcInstance instance) {
        lowHealthTriggered.remove(instance.getId());
        routeOverrides.remove(instance.getId());
        speedOverrides.remove(instance.getId());
        routePaused.remove(instance.getId());
        nearbyPlayers.removeIf(key -> key.instanceId().equals(instance.getId()));
    }

    public MovementProfile movementFor(NpcInstance instance, NpcDefinition definition) {
        String route = routeOverrides.get(instance.getId());
        WalkingSpeed speed = speedOverrides.getOrDefault(instance.getId(), definition.getMovementProfile().walkingSpeed());
        if (routePaused.contains(instance.getId())) {
            return MovementProfile.disabled().withWalkingSpeed(speed);
        }
        return route == null ? definition.getMovementProfile().withWalkingSpeed(speed)
                : new MovementProfile(true, route, speed);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        instances.findByEntityId(event.getEntity().getEntityId())
                .ifPresent(instance -> trigger(BehaviourEvent.LEFT_CLICK, instance, player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        NpcInstance instance = instances.findByEntityId(event.getEntity().getEntityId()).orElse(null);
        if (instance == null) {
            return;
        }
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (event.getAmount() < HEAL_BURST_THRESHOLD) {
            return;
        }
        instances.findByEntityId(event.getEntity().getEntityId())
                .ifPresent(instance -> trigger(BehaviourEvent.HEAL, instance, null));
    }

    private void checkLowHealth(NpcInstance instance, Entity actor) {
        LivingEntity entity = instances.findEntity(instance).orElse(null);
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (entity == null || definition == null || entity.isDead() || definition.getCombatProfile().invulnerable()) {
            return;
        }
        boolean low = entity.getHealth() <= definition.getCombatProfile().maxHealth() * 0.25;
        if (low && lowHealthTriggered.add(instance.getId())) {
            trigger(BehaviourEvent.LOW_HEALTH, instance, actor); 
        }else if (!low) {
            lowHealthTriggered.remove(instance.getId());
        }
    }

    private void execute(
            BehaviourEvent event,
            BehaviourAction action,
            NpcInstance instance,
            NpcDefinition definition,
            Entity actor
    ) {
        switch (action.type()) {
            case SEND_DIALOG ->
                sendDialog(event, instance, definition, action.value(), actor);
            case SHOW_HOLO_DIALOG ->
                dialogService.showHologram(instance, definition, action.value());
            case SET_ROUTE -> {
                if (action.value() != null) {
                    routeOverrides.put(instance.getId(), action.value());
                    routePaused.remove(instance.getId());
                    instances.stopNavigating(instance);
                }
            }
            case RUN_CONSOLE_COMMAND -> {
                if (action.value() != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(action.value(), instance, definition, actor));
                }
            }
            case START_COMBAT -> {
                if (combatService != null) {
                    combatService.startCombat(instance, actor);
                }
            }
            case START_NAVIGATION -> {
                routePaused.remove(instance.getId());
                // Clear the old native path. The route worker will select the
                // nearest logical waypoint and create a fresh path next tick.
                instances.stopNavigating(instance);
            }
            case STOP_NAVIGATION -> {
                routePaused.add(instance.getId());
                instances.stopNavigating(instance);
            }
            case SET_WALK_SPEED ->
                speedOverrides.put(instance.getId(), WalkingSpeed.fromStored(action.value()));
        }
    }

    private void tickBehaviour() {
        if (++proximityTick >= 10) {
            proximityTick = 0;
            tickProximity();
        }
        Set<UUID> active = new HashSet<>();
        for (NpcInstance instance : instances.findAll()) {
            active.add(instance.getId());
        }
        routePaused.retainAll(active);
    }

    private void tickProximity() {
        Set<ProximityKey> nowNearby = new HashSet<>();
        Map<UUID, NpcInstance> activeInstances = new HashMap<>();
        for (NpcInstance instance : instances.findAll()) {
            activeInstances.put(instance.getId(), instance);
            Location npcLocation = instance.getLocation();
            if (npcLocation.getWorld() == null) {
                continue;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                ProximityKey key = new ProximityKey(instance.getId(), player.getUniqueId());
                if (player.getWorld() != npcLocation.getWorld()) {
                    continue;
                }
                double range = nearbyPlayers.contains(key) ? LEAVE_RANGE_SQUARED : APPROACH_RANGE_SQUARED;
                if (player.getLocation().distanceSquared(npcLocation) > range) {
                    continue;
                }
                nowNearby.add(key);
                if (!nearbyPlayers.contains(key)) {
                    trigger(BehaviourEvent.PLAYER_APPROACH, instance, player);
                }
            }
        }
        for (ProximityKey key : nearbyPlayers) {
            if (nowNearby.contains(key)) {
                continue;
            }
            NpcInstance instance = activeInstances.get(key.instanceId());
            if (instance != null) {
                trigger(BehaviourEvent.PLAYER_LEAVES, instance, Bukkit.getPlayer(key.playerId()));
            }
        }
        nearbyPlayers.clear();
        nearbyPlayers.addAll(nowNearby);
    }

    private void sendDialog(
            BehaviourEvent event,
            NpcInstance instance,
            NpcDefinition definition,
            String line,
            Entity actor
    ) {
        if (line == null) {
            return;
        }
        Component message = Component.text(definition.getDisplayName() + ": " + line);
        if ((event == BehaviourEvent.PLAYER_APPROACH || event == BehaviourEvent.PLAYER_LEAVES)
                && actor instanceof Player player && player.isOnline()) {
            player.sendMessage(message);
            return;
        }
        Location location = instance.getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == location.getWorld() && player.getLocation().distanceSquared(location) <= DIALOG_RANGE_SQUARED) {
                player.sendMessage(message);
            }
        }
    }

    private String placeholders(String value, NpcInstance instance, NpcDefinition definition, Entity actor) {
        return value.replace("%npc%", definition.getDisplayName())
                .replace("%instance%", instance.getId().toString())
                .replace("%player%", actor instanceof Player player ? player.getName() : "");
    }

    private record ProximityKey(UUID instanceId, UUID playerId) {

    }
}
