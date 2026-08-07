package dev.blockfolk.runtime;

import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.blockfolk.ai.AiControlService;
import dev.blockfolk.ai.AiDecision;
import dev.blockfolk.ai.AiDecisionResult;
import dev.blockfolk.ai.AiTargetSnapshot;
import dev.blockfolk.dialog.DialogService;
import dev.blockfolk.util.UiText;
import dev.blockfolk.util.EntityHealth;
import dev.blockfolk.model.ActionLocation;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.FightOptions;
import dev.blockfolk.model.MovementProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.repository.NpcDefinitionRepository;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class NpcBehaviourService implements Listener {

    private static final double DIALOG_RANGE_SQUARED = 16.0 * 16.0;
    private static final double CHAT_RANGE_SQUARED = 8.0 * 8.0;
    private static final double APPROACH_RANGE_SQUARED = 8.0 * 8.0;
    private static final double LEAVE_RANGE_SQUARED = 10.0 * 10.0;
    private static final double HEAL_BURST_THRESHOLD = 4.0;
    private static final double FOLLOW_ACQUIRE_RANGE_SQUARED = 16.0 * 16.0;
    private static final double FOLLOW_STOP_RANGE_SQUARED = 3.0 * 3.0;
    private static final double FOLLOW_RESUME_RANGE_SQUARED = 5.0 * 5.0;
    private static final double FOLLOW_CATCH_UP_RANGE_SQUARED = 6.0 * 6.0;
    private static final int FOLLOW_REPATH_TICKS = 10;
    private static final int PLAYER_LOOK_INTERVAL_TICKS = 5;
    private static final int ITEM_PICKUP_INTERVAL_TICKS = 5;
    private static final long OWN_DROP_PICKUP_LOCK_TICKS = 3L * 20L;
    private static final double ITEM_PICKUP_HORIZONTAL_RANGE = 1.5;
    private static final double ITEM_PICKUP_VERTICAL_RANGE = 1.0;
    private static final long CONTAINER_CLOSE_DELAY_TICKS = 20L;
    private static final int AI_INTERACT_RANGE = 12;
    private static final int MAX_QUEUED_AI_INTERACTIONS = 8;
    private static final double SWITCH_USE_RANGE_SQUARED = 2.5 * 2.5;
    private static final long IDLE_REPEAT_TICKS = 1L * 20L;
    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final DialogService dialogService;
    private final NpcQuestionService questionService;
    private final Set<UUID> lowHealthTriggered = new HashSet<>();
    private final Map<UUID, String> routeOverrides = new HashMap<>();
    private final Map<UUID, WalkingSpeed> speedOverrides = new HashMap<>();
    private final Set<UUID> routePaused = new HashSet<>();
    private final Set<UUID> externallyPaused = new HashSet<>();
    private final Map<UUID, Location> moveTargets = new HashMap<>();
    private final Map<UUID, FollowState> following = new HashMap<>();
    private final Map<UUID, Object> waypointActionSequences = new HashMap<>();
    private final Map<UUID, AiInteraction> aiInteractions = new HashMap<>();
    private final Map<UUID, ArrayDeque<AiInteractionRequest>> aiInteractionQueues = new HashMap<>();
    private final Map<UUID, Long> itemPickupLockedUntilTick = new HashMap<>();
    private final Map<UUID, Object> idleCycles = new HashMap<>();
    private final Set<ProximityKey> nearbyPlayers = new HashSet<>();
    private final Map<ProximityKey, Long> proximityCooldownUntilTick = new HashMap<>();
    private final Map<UUID, Long> lastWorldTimes = new HashMap<>();
    private final Map<UUID, Set<UUID>> observedEntities = new HashMap<>();
    private final Map<UUID, Long> entityNearbyCooldownUntilTick = new HashMap<>();
    private final Map<UUID, Location> playerLocationSnapshots = new ConcurrentHashMap<>();
    private final long proximityCooldownTicks;
    private NpcCombatService combatService;
    private AiControlService aiControlService;
    private BukkitTask behaviourTask;
    private long currentTick;
    private int proximityTick;
    private int playerLookTick;
    private int itemPickupTick;
    private int entityNearbyTick;
    private long customEmissionTick = -1L;
    private int customEmissionsThisTick;

    public NpcBehaviourService(Plugin plugin, NpcDefinitionRepository definitions, NpcInstanceRegistry instances,
            DialogService dialogService, NpcQuestionService questionService, int proximityCooldownSeconds) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
        this.dialogService = dialogService;
        this.questionService = questionService;
        this.proximityCooldownTicks = Math.max(0L, proximityCooldownSeconds) * 20L;
    }

    public void setCombatService(NpcCombatService combatService) {
        this.combatService = combatService;
    }

    public void setAiControlService(AiControlService aiControlService) {
        this.aiControlService = aiControlService;
        if (aiControlService != null) {
            aiControlService.setProcessingHandlers(dialogService::showProcessing, dialogService::hideProcessing);
        }
    }

    public void start() {
        stop();
        Bukkit.getOnlinePlayers()
                .forEach(player -> playerLocationSnapshots.put(player.getUniqueId(), player.getLocation().clone()));
        behaviourTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBehaviour, 1L, 1L);
    }

    public void stop() {
        if (behaviourTask != null) {
            behaviourTask.cancel();
        }
        behaviourTask = null;
        routePaused.clear();
        externallyPaused.clear();
        moveTargets.clear();
        following.clear();
        waypointActionSequences.clear();
        aiInteractions.clear();
        itemPickupLockedUntilTick.clear();
        idleCycles.clear();
        nearbyPlayers.clear();
        proximityCooldownUntilTick.clear();
        lastWorldTimes.clear();
        observedEntities.clear();
        entityNearbyCooldownUntilTick.clear();
        playerLocationSnapshots.clear();
    }

    public void trigger(BehaviourEvent event, NpcInstance instance, Entity actor) {
        trigger(event, instance, actor, null);
    }

    public void trigger(BehaviourEvent event, NpcInstance instance, Entity actor, String eventDetail) {
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            return;
        }
        Runnable completion = event == BehaviourEvent.SPAWN ? () -> startIdle(instance) : () -> {
        };
        executeSequence(event, definition.getBehaviourActions(event), 0, instance, definition, actor, eventDetail,
                completion);
    }

    /**
     * Updates an instance inventory and emits events for items entering or leaving
     * it.
     */
    public void updateTemporaryInventory(NpcInstance instance, ItemStack[] contents, Entity actor) {
        ItemStack[] before = instance.getTemporaryInventoryContents();
        ItemStack[] after = contents == null ? new ItemStack[0] : contents;
        boolean dropped = hasUnmatchedItems(before, after);
        boolean received = hasUnmatchedItems(after, before);
        instance.setTemporaryInventoryContents(after);
        if (dropped)
            trigger(BehaviourEvent.DROP_ITEM, instance, actor);
        if (received)
            trigger(BehaviourEvent.RECEIVE_ITEM, instance, actor);
    }

    private boolean hasUnmatchedItems(ItemStack[] source, ItemStack[] destination) {
        java.util.List<ItemStack> remaining = new java.util.ArrayList<>();
        for (ItemStack item : destination) {
            if (item != null && !item.getType().isAir())
                remaining.add(item.clone());
        }
        for (ItemStack item : source) {
            if (item == null || item.getType().isAir())
                continue;
            int amount = item.getAmount();
            for (ItemStack candidate : remaining) {
                if (amount == 0)
                    break;
                if (!item.isSimilar(candidate) || candidate.getAmount() == 0)
                    continue;
                int matched = Math.min(amount, candidate.getAmount());
                amount -= matched;
                candidate.setAmount(candidate.getAmount() - matched);
            }
            if (amount > 0)
                return true;
        }
        return false;
    }

    public void triggerWaypointActions(List<BehaviourAction> actions, NpcInstance instance) {
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null || actions == null || actions.isEmpty()) {
            return;
        }
        Object token = new Object();
        waypointActionSequences.put(instance.getId(), token);
        executeSequence(null, List.copyOf(actions), 0, instance, definition, null, "The NPC reached a route waypoint.",
                () -> waypointActionSequences.remove(instance.getId(), token));
    }

    public void emitCustomEvent(String eventName, Entity actor) {
        if (eventName == null || eventName.isBlank())
            return;
        if (customEmissionTick != currentTick) {
            customEmissionTick = currentTick;
            customEmissionsThisTick = 0;
        }
        if (++customEmissionsThisTick > 64) {
            plugin.getLogger().warning("Stopped a custom NPC event chain after 64 emissions in one tick.");
            return;
        }
        for (NpcInstance target : List.copyOf(instances.findActive())) {
            NpcDefinition definition = definitions.find(target.getDefinitionKey()).orElse(null);
            if (definition == null)
                continue;
            List<BehaviourAction> actions = definition.getCustomEventActions(eventName);
            if (!actions.isEmpty())
                executeSequence(null, actions, 0, target, definition, actor,
                        "Custom event '" + eventName + "' was emitted.", () -> {
                        });
        }
    }

    public void forget(NpcInstance instance) {
        questionService.forget(instance);
        lowHealthTriggered.remove(instance.getId());
        routeOverrides.remove(instance.getId());
        speedOverrides.remove(instance.getId());
        routePaused.remove(instance.getId());
        externallyPaused.remove(instance.getId());
        moveTargets.remove(instance.getId());
        following.remove(instance.getId());
        waypointActionSequences.remove(instance.getId());
        aiInteractions.remove(instance.getId());
        itemPickupLockedUntilTick.remove(instance.getId());
        idleCycles.remove(instance.getId());
        nearbyPlayers.removeIf(key -> key.instanceId().equals(instance.getId()));
        proximityCooldownUntilTick.keySet().removeIf(key -> key.instanceId().equals(instance.getId()));
        observedEntities.remove(instance.getId());
        entityNearbyCooldownUntilTick.remove(instance.getId());
        if (aiControlService != null)
            aiControlService.forget(instance);
    }

    public MovementProfile movementFor(NpcInstance instance, NpcDefinition definition) {
        String route = routeOverrides.get(instance.getId());
        WalkingSpeed speed = speedOverrides.getOrDefault(instance.getId(),
                definition.getMovementProfile().walkingSpeed());
        if (routePaused.contains(instance.getId())) {
            return MovementProfile.disabled().withWalkingSpeed(speed);
        }
        return route == null
                ? definition.getMovementProfile().withWalkingSpeed(speed)
                : MovementProfile.routing(route, speed);
    }

    public boolean hasRoute(NpcInstance instance, NpcDefinition definition) {
        return routeOverrides.containsKey(instance.getId()) || definition.getMovementProfile().enabled();
    }

    public void removeRoute(String routeKey) {
        String normalized = dev.blockfolk.model.NpcRoute.normalizeKey(routeKey);
        routeOverrides.entrySet().removeIf(entry -> {
            try {
                if (!dev.blockfolk.model.NpcRoute.normalizeKey(entry.getValue()).equals(normalized))
                    return false;
            } catch (IllegalArgumentException exception) {
                return false;
            }
            instances.findById(entry.getKey()).ifPresent(instances::stopNavigating);
            return true;
        });
    }

    public boolean isFollowing(NpcInstance instance) {
        return following.containsKey(instance.getId());
    }

    public boolean isMovingTo(NpcInstance instance) {
        return moveTargets.containsKey(instance.getId()) || aiInteractions.containsKey(instance.getId());
    }

    /**
     * Returns whether route movement has been explicitly paused by a behaviour
     * action. A pause must not discard the route worker's next waypoint.
     */
    public boolean isNavigationPaused(NpcInstance instance) {
        return routePaused.contains(instance.getId()) || externallyPaused.contains(instance.getId());
    }

    /** Temporarily pauses movement owned by an external dialog or quest system. */
    public boolean setExternalNavigationPaused(NpcInstance instance, boolean paused) {
        boolean previouslyPaused = externallyPaused.contains(instance.getId());
        if (paused) {
            externallyPaused.add(instance.getId());
            instances.stopNavigating(instance);
        } else {
            externallyPaused.remove(instance.getId());
        }
        return previouslyPaused;
    }

    /**
     * Waypoint actions own route movement until their sequence (including any
     * waits, dialog delays, or questions) has completed.
     */
    public boolean isRunningWaypointActions(NpcInstance instance) {
        return waypointActionSequences.containsKey(instance.getId());
    }

    private void executeSequence(BehaviourEvent event, java.util.List<BehaviourAction> actions, int index,
            NpcInstance instance, NpcDefinition definition, Entity actor, String eventDetail, Runnable completion) {
        if (instances.findById(instance.getId()).isEmpty()) {
            completion.run();
            return;
        }
        if (index >= actions.size()) {
            completion.run();
            return;
        }
        BehaviourAction action = actions.get(index);
        if (action.type() == BehaviourActionType.ASK_QUESTION) {
            askQuestion(event, actions, index, action, instance, definition, actor, eventDetail, completion);
            return;
        }
        execute(event, action, instance, definition, actor, eventDetail);
        long delayTicks = delayAfter(action);
        if (delayTicks <= 0L) {
            executeSequence(event, actions, index + 1, instance, definition, actor, eventDetail, completion);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> executeSequence(event, actions, index + 1, instance,
                    definition, actor, eventDetail, completion), delayTicks);
        }
    }

    private void askQuestion(BehaviourEvent event, List<BehaviourAction> parent, int index, BehaviourAction action,
            NpcInstance instance, NpcDefinition definition, Entity actor, String eventDetail, Runnable completion) {
        Player player = questionPlayer(instance, actor);
        if (player == null) {
            executeSequence(event, action.question().cancelActions(), 0, instance, definition, actor, eventDetail,
                    () -> executeSequence(event, parent, index + 1, instance, definition, actor, eventDetail,
                            completion));
            return;
        }
        questionService.enqueue(player, instance, definition.getDisplayName(), definition.getColor().textColor(),
                action.question(),
                (branch, done) -> executeSequence(event, branch, 0, instance, definition, player, eventDetail,
                        () -> executeSequence(event, parent, index + 1, instance, definition, player, eventDetail,
                                done)));
        // A duplicate intentionally stops only this repeated trigger. Its parent
        // continuation is not run, preventing idle loops from producing side effects.
    }

    private Player questionPlayer(NpcInstance instance, Entity actor) {
        Location location = instance.getLocation();
        if (location.getWorld() == null)
            return null;
        if (actor instanceof Player direct && direct.isOnline() && direct.getWorld() == location.getWorld()
                && direct.getLocation().distanceSquared(location) <= FOLLOW_ACQUIRE_RANGE_SQUARED) {
            return direct;
        }
        return nearestPlayer(instance).orElse(null);
    }

    private void startIdle(NpcInstance instance) {
        Object token = new Object();
        idleCycles.put(instance.getId(), token);
        runIdleCycle(instance, token);
    }

    private void runIdleCycle(NpcInstance instance, Object token) {
        if (idleCycles.get(instance.getId()) != token) {
            return;
        }
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            idleCycles.remove(instance.getId(), token);
            return;
        }
        executeSequence(BehaviourEvent.IDLE, definition.getBehaviourActions(BehaviourEvent.IDLE), 0, instance,
                definition, null, "The NPC has been idle.", () -> Bukkit.getScheduler().runTaskLater(plugin,
                        () -> runIdleCycle(instance, token), IDLE_REPEAT_TICKS));
    }

    private long delayAfter(BehaviourAction action) {
        if (action.type() == BehaviourActionType.WAIT) {
            return secondsToTicks(action.value());
        }
        if (action.type() == BehaviourActionType.SEND_DIALOG || action.type() == BehaviourActionType.SHOW_HOLO_DIALOG) {
            return DialogService.lineDurationSeconds(action.value()) * 20L;
        }
        return 0L;
    }

    static long secondsToTicks(String value) {
        try {
            double seconds = Double.parseDouble(value);
            if (!Double.isFinite(seconds) || seconds <= 0.0) {
                return 0L;
            }
            return Math.max(1L, Math.round(Math.min(seconds * 20.0, Long.MAX_VALUE / 2.0)));
        } catch (NullPointerException | NumberFormatException exception) {
            return 0L;
        }
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
        instances.stand(instance);
        Entity actor = event instanceof EntityDamageByEntityEvent byEntity ? damageActor(byEntity.getDamager()) : null;
        LivingEntity npc = instances.findEntity(instance).orElse(null);
        String detail = "The NPC took " + String.format(java.util.Locale.ROOT, "%.1f", event.getFinalDamage())
                + " damage" + (actor == null ? "." : " from " + actor.getName() + ".")
                + (npc == null
                        ? ""
                        : " Current health: " + String.format(java.util.Locale.ROOT, "%.1f / %.1f",
                                Math.max(0, npc.getHealth() - event.getFinalDamage()), EntityHealth.maximum(npc))
                                + ".");
        // An attack is more specific and higher-priority than generic damage,
        // so it gets the first opportunity to invoke a throttled AI action.
        if (actor != null)
            trigger(BehaviourEvent.NPC_ATTACKED, instance, actor, actor.getName() + " attacked the NPC. " + detail);
        trigger(BehaviourEvent.DAMAGE_TAKEN, instance, actor, detail);
        Bukkit.getScheduler().runTask(plugin, () -> checkLowHealth(instance, actor));
    }

    private Entity damageActor(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity entity)
                return entity;
        }
        return damager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        NpcInstance deceasedNpc = instances.findByEntityId(event.getEntity().getEntityId()).orElse(null);
        if (deceasedNpc != null) {
            trigger(BehaviourEvent.DEATH, deceasedNpc, null);
            Bukkit.getScheduler().runTask(plugin, () -> forget(deceasedNpc));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (event.getAmount() < HEAL_BURST_THRESHOLD) {
            return;
        }
        instances.findByEntityId(event.getEntity().getEntityId())
                .ifPresent(instance -> trigger(BehaviourEvent.HEAL, instance, null));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Location chatLocation = playerLocationSnapshots.get(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> handlePlayerChat(player, message,
                chatLocation == null ? player.getLocation().clone() : chatLocation));
    }

    private void handlePlayerChat(Player player, String message, Location chatLocation) {
        List<NpcInstance> nearby = nearbyChatInstances(instances.findActive(), chatLocation);
        List<NpcInstance> aiGroup = new ArrayList<>();
        String detail = "Player " + player.getName() + " said: \"" + message + "\"";
        for (NpcInstance instance : nearby) {
            NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
            if (definition == null)
                continue;
            if (aiControlService != null && definition.getAiControlSettings().enabled()
                    && definition.getAiControlSettings().respondToChat()) {
                aiControlService.rememberPlayerMessage(instance, player, message);
                aiGroup.add(instance);
            }
        }
        if (aiControlService != null && !aiGroup.isEmpty()) {
            aiControlService.invokeChatGroup(detail, aiGroup, player,
                    (instance, result) -> definitions.find(instance.getDefinitionKey())
                            .ifPresent(definition -> applyAiDecision(BehaviourEvent.PLAYER_CHAT, result, instance,
                                    definition, player, false)));
        }
    }

    static List<NpcInstance> nearbyChatInstances(Collection<NpcInstance> candidates, Location chatLocation) {
        return candidates.stream()
                .filter(instance -> instance.getLocation().getWorld() == chatLocation.getWorld()
                        && coordinateDistanceSquared(instance.getLocation(), chatLocation) <= CHAT_RANGE_SQUARED)
                .sorted(java.util.Comparator
                        .comparingDouble(instance -> coordinateDistanceSquared(instance.getLocation(), chatLocation)))
                .toList();
    }

    private static double coordinateDistanceSquared(Location one, Location two) {
        double x = one.getX() - two.getX();
        double y = one.getY() - two.getY();
        double z = one.getZ() - two.getZ();
        return x * x + y * y + z * z;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        playerLocationSnapshots.put(event.getPlayer().getUniqueId(), event.getTo().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerLocationSnapshots.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerLocationSnapshots.remove(event.getPlayer().getUniqueId());
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
        } else if (!low) {
            lowHealthTriggered.remove(instance.getId());
        }
    }

    private void execute(BehaviourEvent event, BehaviourAction action, NpcInstance instance, NpcDefinition definition,
            Entity actor, String eventDetail) {
        switch (action.type()) {
            case SEND_DIALOG -> sendDialog(event, instance, definition, action.value(), actor);
            case SHOW_HOLO_DIALOG -> dialogService.showHologram(instance, definition, action.value());
            case ASK_QUESTION -> {
                /* Asynchronous and handled by executeSequence. */ }
            case SET_ROUTE -> {
                if (action.value() != null) {
                    try {
                        routeOverrides.put(instance.getId(), dev.blockfolk.model.NpcRoute.normalizeKey(action.value()));
                    } catch (IllegalArgumentException exception) {
                        break;
                    }
                    routePaused.remove(instance.getId());
                    instances.stopNavigating(instance);
                }
            }
            case RUN_CONSOLE_COMMAND -> {
                if (action.value() != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            placeholders(action.value(), instance, definition, actor));
                }
            }
            case START_COMBAT -> {
                if (combatService != null) {
                    combatService.startCombat(instance, actor);
                }
            }
            case CHANGE_FIGHT_OPTIONS -> {
                if (combatService != null) {
                    combatService.changeFightOptions(instance, FightOptions.fromStored(action.value()));
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
            case SET_WALK_SPEED -> speedOverrides.put(instance.getId(), WalkingSpeed.fromStored(action.value()));
            case MOVE_TO -> ActionLocation.parse(action.value()).map(ActionLocation::toLocation)
                    .filter(java.util.Objects::nonNull).ifPresent(target -> {
                        stopFollowing(instance);
                        moveTargets.put(instance.getId(), target);
                        instances.stand(instance);
                        instances.stopNavigating(instance);
                    });
            case TELEPORT_TO -> ActionLocation.parse(action.value()).map(ActionLocation::toLocation)
                    .filter(java.util.Objects::nonNull).ifPresent(target -> {
                        stopFollowing(instance);
                        moveTargets.remove(instance.getId());
                        instances.stand(instance);
                        instances.move(instance, target);
                    });
            // WAIT is handled by executeSequence: it only delays the next action.
            case WAIT -> {
            }
            case AI_TRIGGER -> invokeAi(event, eventDetail, instance, definition, actor);
            case INTERACT -> interactWithNearbySwitches(instance);
            case MINE_BLOCKS -> mineNearbyBlocks(instance);
            case TAKE_ITEM -> takeNearbyItem(instance, actor);
            case SHOW_INVENTORY -> showInventory(instance, actor);
            case DROP_INVENTORY -> dropInventory(instance);
            case HARVEST -> harvestNearbyCrops(instance);
            case EMIT_EVENT ->
                emitCustomEvent(action.value(), actor != null ? actor : instances.findEntity(instance).orElse(null));
            case SLEEP -> instances.pose(instance, Pose.SLEEPING);
            case SWIM -> instances.pose(instance, Pose.SWIMMING);
            case FALL_FLY -> instances.pose(instance, Pose.FALL_FLYING);
            case STAND -> instances.stand(instance);
            case SNEAK -> instances.pose(instance, Pose.SNEAKING);
            case WAVE -> instances.wave(instance);
            case JUMP -> {
                instances.stand(instance);
                instances.jump(instance);
            }
            case FOLLOW -> startFollowing(instance);
            case UNFOLLOW -> stopFollowing(instance);
        }
    }

    private void applyAiDecision(BehaviourEvent event, AiDecisionResult result, NpcInstance instance,
            NpcDefinition definition, Entity actor, boolean rememberOwnSpeech) {
        for (AiDecision.Action action : result.decision().actions()) {
            Entity target = resolveAiTarget(action.target(), result.targets());
            switch (action.type()) {
                case SAY -> {
                    sendDialog(event, instance, definition, action.text(), actor);
                    if (rememberOwnSpeech) {
                        aiControlService.rememberNpcSpeech(instance, definition,
                                actor instanceof Player player ? player : null, action.text());
                    }
                }
                case PLAY_ANIMATION -> playAiAnimation(instance, action.animation());
                case START_COMBAT -> {
                    if (combatService != null) {
                        Entity selected = action.target() == null
                                ? resolveAiTarget("nearest_attackable", result.targets())
                                : target;
                        combatService.startDirectedCombat(instance, selected);
                    }
                }
                case STOP_COMBAT -> {
                    if (combatService != null)
                        combatService.exitCombat(instance);
                }
                case FLEE_FROM -> fleeFrom(instance, target);
                case FOLLOW -> {
                    if (target instanceof Player player)
                        startFollowing(instance, player);
                }
                case UNFOLLOW -> stopFollowing(instance);
                case INTERACT -> startAiInteraction(instance, action.target(), result.targets());
                case MOVE_TO -> resolveAiMoveTarget(action.target(), result.targets()).ifPresent(targetLocation -> {
                    stopFollowing(instance);
                    moveTargets.put(instance.getId(), targetLocation);
                    instances.stand(instance);
                    instances.stopNavigating(instance);
                });
                case MINE_BLOCKS -> mineNearbyBlocks(instance, action.target(), true,
                        definition.getAiControlSettings().inventoryEnabled());
                case RETURN_HOME -> {
                    stopFollowing(instance);
                    moveTargets.put(instance.getId(), instance.getSpawnLocation());
                    instances.stopNavigating(instance);
                }
                case START_ROUTE -> {
                    routePaused.remove(instance.getId());
                    instances.stopNavigating(instance);
                }
                case PAUSE_ROUTE -> {
                    routePaused.add(instance.getId());
                    instances.stopNavigating(instance);
                }
                case REMEMBER_FACT -> {
                    aiControlService.rememberFact(definition, action.text());
                    announceMemory(instance, definition);
                }
                case DROP_ITEM -> dropAiInventoryItem(instance, definition, action.target());
                case DO_NOTHING -> {
                }
            }
        }
    }

    private void invokeAi(BehaviourEvent event, String eventDetail, NpcInstance instance, NpcDefinition definition,
            Entity actor) {
        if (aiControlService != null)
            aiControlService.invoke(event, eventDetail, instance, definition, actor,
                    result -> applyAiDecision(event, result, instance, definition, actor, true));
    }

    private Entity resolveAiTarget(String alias, AiTargetSnapshot targets) {
        if (alias == null || targets == null)
            return null;
        Entity entity = targets.entityId(alias).map(Bukkit::getEntity).orElse(null);
        if (entity != null && entity.isValid())
            return entity;
        return targets.npcInstanceId(alias).flatMap(instances::findById).flatMap(instances::findEntity)
                .filter(Entity::isValid).orElse(null);
    }

    private java.util.Optional<Location> resolveAiMoveTarget(String alias, AiTargetSnapshot targets) {
        if (alias == null || targets == null)
            return java.util.Optional.empty();
        Entity standardTarget = resolveAiTarget(alias, targets);
        if (standardTarget != null && standardTarget.isValid()) {
            return java.util.Optional.of(standardTarget.getLocation());
        }
        return targets.location(alias);
    }

    private static int targetIndex(String alias) {
        int separator = alias.lastIndexOf('_');
        if (separator < 0)
            return -1;
        try {
            return Integer.parseInt(alias.substring(separator + 1)) - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void dropAiInventoryItem(NpcInstance instance, NpcDefinition definition, String target) {
        if (!definition.getAiControlSettings().inventoryEnabled() || target == null
                || !target.startsWith("inventory_slot_"))
            return;
        int slot = targetIndex(target);
        ItemStack[] contents = instance.getTemporaryInventoryContents();
        if (slot < 0 || slot >= contents.length)
            return;
        ItemStack item = contents[slot];
        Location center = instance.getLocation();
        if (item == null || item.getType().isAir() || center.getWorld() == null)
            return;
        contents[slot] = null;
        dropItemForNpc(instance, item);
        updateTemporaryInventory(instance, contents, null);
    }

    private void playAiAnimation(NpcInstance instance, String animation) {
        if (animation == null)
            return;
        switch (animation) {
            case "wave" -> instances.wave(instance);
            case "jump" -> instances.jump(instance);
            case "sneak" -> instances.pose(instance, Pose.SNEAKING);
            case "stand" -> instances.stand(instance);
            default -> {
            }
        }
    }

    private void announceMemory(NpcInstance instance, NpcDefinition definition) {
        Component message = Component.text(definition.getDisplayName() + " remembered this...", NamedTextColor.GRAY)
                .decorate(TextDecoration.ITALIC);
        Location location = instance.getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == location.getWorld()
                    && player.getLocation().distanceSquared(location) <= DIALOG_RANGE_SQUARED) {
                player.sendMessage(message);
            }
        }
    }

    private void fleeFrom(NpcInstance instance, Entity target) {
        if (target == null || target.getWorld() != instance.getLocation().getWorld())
            return;
        Location origin = instance.getLocation();
        Vector away = origin.toVector().subtract(target.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.01)
            away = new Vector(1, 0, 0);
        Location destination = origin.clone().add(away.normalize().multiply(10));
        stopFollowing(instance);
        moveTargets.put(instance.getId(), destination);
        instances.stopNavigating(instance);
    }

    private void tickBehaviour() {
        currentTick++;
        if (++proximityTick >= 10) {
            proximityTick = 0;
            tickTimeEvents();
            tickProximity();
        }
        if (++playerLookTick >= PLAYER_LOOK_INTERVAL_TICKS) {
            playerLookTick = 0;
            tickPlayerLook();
        }
        if (++itemPickupTick >= ITEM_PICKUP_INTERVAL_TICKS) {
            itemPickupTick = 0;
            tickItemPickup();
        }
        if (++entityNearbyTick >= 20) {
            entityNearbyTick = 0;
            tickEntityNearby();
        }
        boolean cleanRuntimeState = currentTick % 20L == 0L;
        Set<UUID> active = cleanRuntimeState ? new HashSet<>() : null;
        for (NpcInstance instance : instances.findActive()) {
            if (cleanRuntimeState)
                active.add(instance.getId());
            if (!tickAiInteraction(instance)) {
                tickMoveTo(instance);
                tickFollow(instance);
            }
        }
        if (cleanRuntimeState) {
            routePaused.retainAll(active);
            moveTargets.keySet().retainAll(active);
            following.keySet().retainAll(active);
            waypointActionSequences.keySet().retainAll(active);
            aiInteractions.keySet().retainAll(active);
            aiInteractionQueues.keySet().retainAll(active);
            itemPickupLockedUntilTick.entrySet()
                    .removeIf(entry -> !active.contains(entry.getKey()) || entry.getValue() <= currentTick);
            idleCycles.keySet().retainAll(active);
            observedEntities.keySet().retainAll(active);
            entityNearbyCooldownUntilTick.keySet().retainAll(active);
        }
    }

    private void tickEntityNearby() {
        Set<Integer> npcEntityIds = instances.findActive().stream().map(NpcInstance::getEntityId)
                .collect(java.util.stream.Collectors.toSet());
        for (NpcInstance instance : instances.findActive()) {
            NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
            if (definition == null || definition.getBehaviourActions(BehaviourEvent.ENTITY_NEARBY).isEmpty())
                continue;
            Location center = instance.getLocation();
            if (center.getWorld() == null)
                continue;
            List<Entity> nearby = center.getWorld().getNearbyEntities(center, 8, 8, 8).stream()
                    .filter(entity -> entity instanceof LivingEntity && !(entity instanceof Player))
                    .filter(entity -> !npcEntityIds.contains(entity.getEntityId()))
                    .filter(entity -> !instances.isNavigationEntity(entity))
                    .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(center)))
                    .toList();
            Set<UUID> current = nearby.stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
            Set<UUID> previous = observedEntities.put(instance.getId(), current);
            if (previous == null)
                previous = Set.of();
            if (currentTick < entityNearbyCooldownUntilTick.getOrDefault(instance.getId(), 0L))
                continue;
            Set<UUID> old = previous;
            nearby.stream().filter(entity -> !old.contains(entity.getUniqueId())).findFirst().ifPresent(entity -> {
                entityNearbyCooldownUntilTick.put(instance.getId(), currentTick + 20L * 20L);
                trigger(BehaviourEvent.ENTITY_NEARBY, instance, entity,
                        "A " + entity.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                                + " entered the observation radius. Distance: "
                                + Math.round(entity.getLocation().distance(center)) + " blocks.");
            });
        }
    }

    private void tickTimeEvents() {
        Set<UUID> activeWorlds = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            UUID worldId = world.getUID();
            activeWorlds.add(worldId);
            long current = Math.floorMod(world.getTime(), 24000L);
            Long previous = lastWorldTimes.put(worldId, current);
            if (previous == null || previous == current) {
                continue;
            }
            long elapsed = Math.floorMod(current - previous, 24000L);
            // More than half a day backwards is most likely an administrative time change.
            if (elapsed == 0L || elapsed > 12000L) {
                continue;
            }
            if (crossedTime(previous, elapsed, 0L)) {
                triggerTimeEvent(world, BehaviourEvent.SUNRISE);
            }
            if (crossedTime(previous, elapsed, 6000L)) {
                triggerTimeEvent(world, BehaviourEvent.NOON);
            }
            if (crossedTime(previous, elapsed, 12000L)) {
                triggerTimeEvent(world, BehaviourEvent.SUNSET);
            }
        }
        lastWorldTimes.keySet().retainAll(activeWorlds);
    }

    private boolean crossedTime(long previous, long elapsed, long threshold) {
        long distance = Math.floorMod(threshold - previous, 24000L);
        return distance > 0L && distance <= elapsed;
    }

    private void triggerTimeEvent(World world, BehaviourEvent event) {
        for (NpcInstance instance : List.copyOf(instances.findActive())) {
            if (instance.getLocation().getWorld() == world) {
                trigger(event, instance, null);
            }
        }
    }

    private void tickMoveTo(NpcInstance instance) {
        Location target = moveTargets.get(instance.getId());
        if (target == null || isNavigationPaused(instance)
                || combatService != null && combatService.isEngaged(instance)) {
            return;
        }
        Location current = instance.getLocation();
        if (current.getWorld() == null || target.getWorld() == null || current.getWorld() != target.getWorld()) {
            moveTargets.remove(instance.getId());
            instances.stopNavigating(instance);
            return;
        }
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            moveTargets.remove(instance.getId());
            return;
        }
        WalkingSpeed speed = speedOverrides.getOrDefault(instance.getId(),
                definition.getMovementProfile().walkingSpeed());
        NativeNpcNavigationService.NavigationStatus status = instances.navigate(instance, target, speed);
        if (status == NativeNpcNavigationService.NavigationStatus.ARRIVED
                || status == NativeNpcNavigationService.NavigationStatus.STALLED) {
            moveTargets.remove(instance.getId());
            instances.stopNavigating(instance);
        }
    }

    private void startFollowing(NpcInstance instance) {
        startFollowing(instance, nearestPlayer(instance).orElse(null));
    }

    private void startFollowing(NpcInstance instance, Player player) {
        FollowState state = following.computeIfAbsent(instance.getId(), ignored -> new FollowState());
        updateFollowTarget(instance, state, player);
        state.navigationTarget = null;
        state.repathTicks = 0;
        state.moving = false;
        instances.stand(instance);
        instances.stopNavigating(instance);
    }

    private void stopFollowing(NpcInstance instance) {
        FollowState state = following.remove(instance.getId());
        if (state != null) {
            notifyFollowChange(instance, onlinePlayer(state.playerId), false);
            instances.stopNavigating(instance);
        }
    }

    private void updateFollowTarget(NpcInstance instance, FollowState state, Player player) {
        UUID playerId = player == null ? null : player.getUniqueId();
        if (java.util.Objects.equals(state.playerId, playerId))
            return;
        notifyFollowChange(instance, onlinePlayer(state.playerId), false);
        state.playerId = playerId;
        notifyFollowChange(instance, player, true);
    }

    private Player onlinePlayer(UUID playerId) {
        return playerId == null ? null : Bukkit.getPlayer(playerId);
    }

    private void notifyFollowChange(NpcInstance instance, Player player, boolean started) {
        if (player == null)
            return;
        definitions.find(instance.getDefinitionKey()).ifPresent(definition -> player.sendMessage(Component
                .text(definition.getDisplayName()
                        + (started ? " is now following you..." : " is no longer following you.."), NamedTextColor.GRAY)
                .decorate(TextDecoration.ITALIC)));
    }

    private void tickFollow(NpcInstance instance) {
        FollowState state = following.get(instance.getId());
        if (state == null || isNavigationPaused(instance)
                || combatService != null && combatService.isEngaged(instance)) {
            return;
        }
        Player player = state.playerId == null ? null : Bukkit.getPlayer(state.playerId);
        if (!isFollowTarget(instance, player)) {
            player = nearestPlayer(instance).orElse(null);
            updateFollowTarget(instance, state, player);
            state.navigationTarget = null;
        }
        if (player == null) {
            if (state.moving) {
                instances.stopNavigating(instance);
                state.moving = false;
            }
            return;
        }
        double distanceSquared = instance.getLocation().distanceSquared(player.getLocation());
        if (distanceSquared <= FOLLOW_STOP_RANGE_SQUARED) {
            if (state.moving) {
                instances.stopNavigating(instance);
                state.moving = false;
            }
            state.navigationTarget = null;
            return;
        }
        if (!state.moving && distanceSquared < FOLLOW_RESUME_RANGE_SQUARED) {
            return;
        }
        state.moving = true;
        if (state.navigationTarget == null || state.repathTicks-- <= 0) {
            state.navigationTarget = player.getLocation();
            state.repathTicks = FOLLOW_REPATH_TICKS;
        }
        WalkingSpeed speed = distanceSquared >= FOLLOW_CATCH_UP_RANGE_SQUARED
                ? WalkingSpeed.VERY_FAST
                : WalkingSpeed.NORMAL;
        instances.navigate(instance, state.navigationTarget, speed);
    }

    private void interactWithNearbySwitches(NpcInstance instance) {
        Location center = instance.getLocation();
        if (center.getWorld() == null)
            return;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (block.getType() != Material.LEVER && !Tag.BUTTONS.isTagged(block.getType()))
                        continue;
                    toggleSwitch(block);
                }
            }
        }
    }

    private void startAiInteraction(NpcInstance instance, String requestedTarget, AiTargetSnapshot targets) {
        String normalizedTarget = requestedTarget == null ? "nearest_switch" : requestedTarget;
        AiInteractionKind kind = normalizedTarget.startsWith("take_from_container")
                ? AiInteractionKind.TAKE_FROM_CONTAINER
                : normalizedTarget.startsWith("store_in_container")
                        ? AiInteractionKind.STORE_IN_CONTAINER
                        : AiInteractionKind.SWITCH;
        Location explicitLocation = targets == null ? null : targets.location(normalizedTarget).orElse(null);
        boolean requiresExplicitLocation = normalizedTarget.startsWith("nearby_lever_")
                || normalizedTarget.startsWith("nearby_button_")
                || normalizedTarget.matches("(?:take_from|store_in)_container_[1-9][0-9]*");
        if (requiresExplicitLocation && explicitLocation == null)
            return;
        AiInteractionRequest request = new AiInteractionRequest(kind, explicitLocation);
        if (aiInteractions.containsKey(instance.getId())) {
            ArrayDeque<AiInteractionRequest> queue = aiInteractionQueues.computeIfAbsent(instance.getId(),
                    ignored -> new ArrayDeque<>());
            if (queue.size() < MAX_QUEUED_AI_INTERACTIONS)
                queue.addLast(request);
            return;
        }
        beginAiInteraction(instance, request);
    }

    private boolean beginAiInteraction(NpcInstance instance, AiInteractionRequest request) {
        Location current = instances.currentLocation(instance);
        Block target = request.blockLocation() == null
                ? request.kind() == AiInteractionKind.SWITCH
                        ? findNearestSwitch(current)
                        : findNearestContainer(current, instance, request.kind())
                : request.blockLocation().getBlock();
        if (target == null || !isUsableInteractionTarget(target, request.kind()))
            return false;
        stopFollowing(instance);
        moveTargets.remove(instance.getId());
        instances.stand(instance);
        instances.stopNavigating(instance);
        aiInteractions.put(instance.getId(),
                new AiInteraction(request.kind(), target.getLocation(), interactionDestination(target, current)));
        return true;
    }

    private boolean tickAiInteraction(NpcInstance instance) {
        AiInteraction interaction = aiInteractions.get(instance.getId());
        if (interaction == null)
            return false;
        if (combatService != null && combatService.isEngaged(instance))
            return true;
        Block target = interaction.blockLocation().getBlock();
        if (!isUsableInteractionTarget(target, interaction.kind())) {
            return finishAiInteraction(instance);
        }
        Location current = instance.getLocation();
        if (current.getWorld() != target.getWorld()) {
            return finishAiInteraction(instance);
        }
        Location switchCenter = target.getLocation().add(0.5, 0.5, 0.5);
        if (current.distanceSquared(switchCenter) <= SWITCH_USE_RANGE_SQUARED) {
            performAiInteraction(instance, target, interaction.kind());
            return finishAiInteraction(instance);
        }
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            return finishAiInteraction(instance);
        }
        WalkingSpeed speed = speedOverrides.getOrDefault(instance.getId(),
                definition.getMovementProfile().walkingSpeed());
        NativeNpcNavigationService.NavigationStatus status = instances.navigate(instance,
                interaction.navigationTarget(), speed);
        if (status == NativeNpcNavigationService.NavigationStatus.ARRIVED) {
            performAiInteraction(instance, target, interaction.kind());
            return finishAiInteraction(instance);
        }
        if (status == NativeNpcNavigationService.NavigationStatus.STALLED) {
            return finishAiInteraction(instance);
        }
        return true;
    }

    /** Returns true when another queued interaction was started. */
    private boolean finishAiInteraction(NpcInstance instance) {
        aiInteractions.remove(instance.getId());
        instances.stopNavigating(instance);
        ArrayDeque<AiInteractionRequest> queue = aiInteractionQueues.get(instance.getId());
        while (queue != null && !queue.isEmpty()) {
            if (beginAiInteraction(instance, queue.removeFirst()))
                return true;
        }
        aiInteractionQueues.remove(instance.getId());
        return false;
    }

    private boolean isUsableInteractionTarget(Block block, AiInteractionKind kind) {
        return kind == AiInteractionKind.SWITCH ? isUsableSwitch(block) : block.getState() instanceof Container;
    }

    private void performAiInteraction(NpcInstance instance, Block block, AiInteractionKind kind) {
        if (kind == AiInteractionKind.SWITCH) {
            toggleSwitch(block);
            return;
        }
        if (!(block.getState() instanceof Container container))
            return;
        if (kind == AiInteractionKind.TAKE_FROM_CONTAINER) {
            takeFromContainer(instance, container);
        } else {
            storeInContainer(instance, container);
        }
    }

    private Block findNearestSwitch(Location center) {
        if (center.getWorld() == null)
            return null;
        Block nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = -AI_INTERACT_RANGE; x <= AI_INTERACT_RANGE; x++) {
            for (int y = -AI_INTERACT_RANGE; y <= AI_INTERACT_RANGE; y++) {
                int blockY = center.getBlockY() + y;
                if (blockY < center.getWorld().getMinHeight() || blockY >= center.getWorld().getMaxHeight())
                    continue;
                for (int z = -AI_INTERACT_RANGE; z <= AI_INTERACT_RANGE; z++) {
                    double distance = x * x + y * y + z * z;
                    if (distance > AI_INTERACT_RANGE * AI_INTERACT_RANGE || distance >= nearestDistance)
                        continue;
                    Block block = center.getWorld().getBlockAt(center.getBlockX() + x, blockY, center.getBlockZ() + z);
                    if (!isUsableSwitch(block))
                        continue;
                    nearest = block;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }

    private Block findNearestContainer(Location center, NpcInstance instance, AiInteractionKind kind) {
        if (center.getWorld() == null)
            return null;
        Block nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = -AI_INTERACT_RANGE; x <= AI_INTERACT_RANGE; x++) {
            for (int y = -AI_INTERACT_RANGE; y <= AI_INTERACT_RANGE; y++) {
                int blockY = center.getBlockY() + y;
                if (blockY < center.getWorld().getMinHeight() || blockY >= center.getWorld().getMaxHeight())
                    continue;
                for (int z = -AI_INTERACT_RANGE; z <= AI_INTERACT_RANGE; z++) {
                    double distance = x * x + y * y + z * z;
                    if (distance > AI_INTERACT_RANGE * AI_INTERACT_RANGE || distance >= nearestDistance)
                        continue;
                    Block block = center.getWorld().getBlockAt(center.getBlockX() + x, blockY, center.getBlockZ() + z);
                    if (!(block.getState() instanceof Container container)
                            || !isUsableContainer(container.getInventory(), instance, kind))
                        continue;
                    nearest = block;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }

    private boolean isUsableContainer(Inventory inventory, NpcInstance instance, AiInteractionKind kind) {
        if (kind == AiInteractionKind.TAKE_FROM_CONTAINER) {
            for (ItemStack item : inventory.getContents()) {
                if (item != null && !item.getType().isAir())
                    return true;
            }
            return false;
        }
        for (ItemStack carried : instance.getTemporaryInventoryContents()) {
            if (carried == null || carried.getType().isAir())
                continue;
            if (inventory.firstEmpty() >= 0)
                return true;
            for (ItemStack stored : inventory.getContents()) {
                if (stored != null && stored.isSimilar(carried) && stored.getAmount() < stored.getMaxStackSize())
                    return true;
            }
        }
        return false;
    }

    private boolean isUsableSwitch(Block block) {
        if (block.getType() != Material.LEVER && !Tag.BUTTONS.isTagged(block.getType()))
            return false;
        return block.getBlockData() instanceof Powerable powerable
                && (!Tag.BUTTONS.isTagged(block.getType()) || !powerable.isPowered());
    }

    private Location interactionDestination(Block target, Location origin) {
        List<Location> candidates = new ArrayList<>();
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            for (int yOffset : List.of(0, -1)) {
                Block feet = target.getRelative(face).getRelative(0, yOffset, 0);
                if (feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable()
                        && feet.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    candidates.add(feet.getLocation().add(0.5, 0, 0.5));
                }
            }
        }
        return candidates.stream().min(Comparator.comparingDouble(origin::distanceSquared))
                .orElse(target.getLocation().add(0.5, 0, 0.5));
    }

    private void toggleSwitch(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Powerable powerable) || !(data instanceof Switch switchData))
            return;
        boolean button = Tag.BUTTONS.isTagged(block.getType());
        if (button && powerable.isPowered())
            return;
        int oldCurrent = powerable.isPowered() ? 15 : 0;
        BlockRedstoneEvent event = new BlockRedstoneEvent(block, oldCurrent, oldCurrent == 0 ? 15 : 0);
        Bukkit.getPluginManager().callEvent(event);
        powerable.setPowered(event.getNewCurrent() > 0);
        block.setBlockData(data, true);
        notifyAttachedBlockNeighbors(block, switchData);
        if (button && powerable.isPowered()) {
            Material pressedType = block.getType();
            long releaseDelay = pressedType == Material.STONE_BUTTON
                    || pressedType == Material.POLISHED_BLACKSTONE_BUTTON ? 20L : 30L;
            Location buttonLocation = block.getLocation();
            Bukkit.getScheduler().runTaskLater(plugin, () -> releaseButton(buttonLocation), releaseDelay);
        }
    }

    private void releaseButton(Location location) {
        Block block = location.getBlock();
        if (!Tag.BUTTONS.isTagged(block.getType()))
            return;
        BlockData data = block.getBlockData();
        if (!(data instanceof Powerable powerable) || !powerable.isPowered())
            return;
        BlockRedstoneEvent event = new BlockRedstoneEvent(block, 15, 0);
        Bukkit.getPluginManager().callEvent(event);
        powerable.setPowered(event.getNewCurrent() > 0);
        block.setBlockData(data, true);
        notifyAttachedBlockNeighbors(block, (Switch) data);
    }

    private void notifyAttachedBlockNeighbors(Block block, Switch switchData) {
        BlockFace supportFace = switch (switchData.getAttachedFace()) {
            case FLOOR -> BlockFace.DOWN;
            case CEILING -> BlockFace.UP;
            case WALL -> switchData.getFacing().getOppositeFace();
        };
        Block support = block.getRelative(supportFace);
        // Re-apply the support block through Bukkit with physics enabled. This
        // notifies its redstone neighbours without reaching into versioned NMS.
        support.getState().update(true, true);
    }

    private void mineNearbyBlocks(NpcInstance instance) {
        mineNearbyBlocks(instance, "mineable_blocks", false, true);
    }

    private void mineNearbyBlocks(NpcInstance instance, String requestedTarget, boolean autonomousRange,
            boolean collectDrops) {
        Location feet = instances.currentLocation(instance);
        if (feet.getWorld() == null)
            return;
        LivingEntity entity = instances.findEntity(instance).orElse(null);
        ItemStack tool = entity == null || entity.getEquipment() == null
                ? null
                : entity.getEquipment().getItemInMainHand();
        Inventory carried = Bukkit.createInventory(null, 27);
        carried.setContents(instance.getTemporaryInventoryContents());
        String target = requestedTarget == null
                ? "mineable_blocks"
                : requestedTarget.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        boolean mined = false;
        int minedBlocks = 0;
        int minY = autonomousRange ? -4 : 0;
        int maxY = autonomousRange ? 8 : 3;
        int horizontalRange = autonomousRange ? 5 : 2;
        int blockLimit = autonomousRange ? 64 : 100;
        for (int y = minY; y <= maxY && minedBlocks < blockLimit; y++) {
            for (int x = -horizontalRange; x <= horizontalRange && minedBlocks < blockLimit; x++) {
                for (int z = -horizontalRange; z <= horizontalRange && minedBlocks < blockLimit; z++) {
                    if (autonomousRange && x * x + y * y + z * z > 64)
                        continue;
                    Block block = feet.getBlock().getRelative(x, y, z);
                    if (!matchesMiningTarget(block.getType(), target) || block.getState() instanceof TileState)
                        continue;
                    ItemStack effectiveTool = tool == null ? new ItemStack(Material.AIR) : tool;
                    Inventory updated = carried;
                    if (collectDrops) {
                        List<ItemStack> drops = new ArrayList<>(block.getDrops(effectiveTool, entity));
                        if (drops.isEmpty())
                            continue;
                        updated = Bukkit.createInventory(null, 27);
                        updated.setContents(carried.getContents());
                        boolean fits = true;
                        for (ItemStack drop : drops) {
                            if (!updated.addItem(drop.clone()).isEmpty()) {
                                fits = false;
                                break;
                            }
                        }
                        if (!fits)
                            continue;
                    }
                    if (!authorizeBlockChange(instance, block, Material.AIR.createBlockData()))
                        continue;
                    if (collectDrops) {
                        carried = updated;
                        block.setType(Material.AIR, true);
                    } else {
                        block.breakNaturally(effectiveTool, true);
                    }
                    mined = true;
                    minedBlocks++;
                }
            }
        }
        if (mined) {
            if (collectDrops)
                updateTemporaryInventory(instance, carried.getContents(), entity);
            if (entity != null)
                entity.swingMainHand();
        }
    }

    private void takeNearbyItem(NpcInstance instance, Entity actor) {
        InventorySource source = nearbySourceInventory(instance, actor);
        if (source == null)
            return;

        takeFromInventory(instance, source.inventory(), source.containerLocation(), actor);
    }

    private void takeFromContainer(NpcInstance instance, Container container) {
        takeFromInventory(instance, container.getInventory(), container.getLocation(), null);
    }

    private void takeFromInventory(NpcInstance instance, Inventory source, Location containerLocation, Entity actor) {

        Inventory carried = Bukkit.createInventory(null, 27);
        carried.setContents(instance.getTemporaryInventoryContents());
        animateContainerInteraction(containerLocation);
        for (int slot = 0; slot < source.getSize(); slot++) {
            ItemStack item = source.getItem(slot);
            if (item == null || item.getType().isAir())
                continue;
            ItemStack offered = item.clone();
            InventoryMoveItemEvent moveEvent = new InventoryMoveItemEvent(source, offered, carried, true);
            Bukkit.getPluginManager().callEvent(moveEvent);
            if (moveEvent.isCancelled() || !moveEvent.getItem().isSimilar(item)
                    || moveEvent.getItem().getAmount() > item.getAmount())
                return;
            offered = moveEvent.getItem().clone();
            Map<Integer, ItemStack> leftovers = carried.addItem(offered);
            int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int moved = offered.getAmount() - remaining;
            if (moved <= 0)
                return;
            if (moved == item.getAmount())
                source.setItem(slot, null);
            else
                item.setAmount(item.getAmount() - moved);
            updateTemporaryInventory(instance, carried.getContents(), actor);
            return;
        }
    }

    private void storeInContainer(NpcInstance instance, Container container) {
        Inventory carried = Bukkit.createInventory(null, 27);
        carried.setContents(instance.getTemporaryInventoryContents());
        Inventory destination = container.getInventory();
        boolean moved = false;
        for (int slot = 0; slot < carried.getSize(); slot++) {
            ItemStack item = carried.getItem(slot);
            if (item == null || item.getType().isAir())
                continue;
            InventoryMoveItemEvent moveEvent = new InventoryMoveItemEvent(carried, item.clone(), destination, true);
            Bukkit.getPluginManager().callEvent(moveEvent);
            if (moveEvent.isCancelled() || !moveEvent.getItem().isSimilar(item)
                    || moveEvent.getItem().getAmount() > item.getAmount())
                continue;
            ItemStack offered = moveEvent.getItem().clone();
            int before = offered.getAmount();
            Map<Integer, ItemStack> leftovers = destination.addItem(offered);
            ItemStack leftover = leftovers.isEmpty() ? null : leftovers.values().iterator().next();
            int transferred = before - (leftover == null ? 0 : leftover.getAmount());
            int remainingCarried = item.getAmount() - transferred;
            if (remainingCarried <= 0)
                carried.setItem(slot, null);
            else {
                ItemStack remaining = item.clone();
                remaining.setAmount(remainingCarried);
                carried.setItem(slot, remaining);
            }
            moved |= leftover == null || leftover.getAmount() < before;
        }
        if (!moved)
            return;
        animateContainerInteraction(container.getLocation());
        updateTemporaryInventory(instance, carried.getContents(), null);
    }

    private InventorySource nearbySourceInventory(NpcInstance instance, Entity actor) {
        Location center = instance.getLocation();
        if (center.getWorld() == null)
            return null;
        Inventory nearest = null;
        Location nearestContainer = null;
        double nearestDistance = Double.MAX_VALUE;
        if (actor instanceof Player player && player.getWorld() == center.getWorld()
                && player.getLocation().distanceSquared(center) <= 16.0) {
            nearest = player.getInventory();
            nearestDistance = player.getLocation().distanceSquared(center);
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (!(block.getState() instanceof Container container))
                        continue;
                    double distance = block.getLocation().add(.5, .5, .5).distanceSquared(center);
                    if (distance < nearestDistance) {
                        nearest = container.getInventory();
                        nearestContainer = block.getLocation();
                        nearestDistance = distance;
                    }
                }
            }
        }
        for (Player player : center.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(center);
            if (distance <= 16.0 && distance < nearestDistance) {
                nearest = player.getInventory();
                nearestContainer = null;
                nearestDistance = distance;
            }
        }
        return nearest == null ? null : new InventorySource(nearest, nearestContainer);
    }

    private void showInventory(NpcInstance instance, Entity actor) {
        Player player = actor instanceof Player direct ? direct : nearestPlayer(instance).orElse(null);
        if (player == null)
            return;
        Inventory inventory = Bukkit.createInventory(new NpcInventoryHolder(instance.getId()), 27,
                UiText.title("NPC Inventory"));
        inventory.setContents(instance.getTemporaryInventoryContents());
        player.openInventory(inventory);
    }

    private void dropInventory(NpcInstance instance) {
        Location center = instance.getLocation();
        if (center.getWorld() == null)
            return;

        Inventory carried = Bukkit.createInventory(null, 27);
        carried.setContents(instance.getTemporaryInventoryContents());
        List<Container> containers = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (block.getState() instanceof Container container)
                        containers.add(container);
                }
            }
        }
        containers.sort(Comparator
                .comparingDouble(container -> container.getLocation().add(.5, .5, .5).distanceSquared(center)));
        Set<Inventory> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Container container : containers) {
            Inventory destination = container.getInventory();
            if (!visited.add(destination))
                continue;
            boolean inspected = false;
            for (int slot = 0; slot < carried.getSize(); slot++) {
                ItemStack item = carried.getItem(slot);
                if (item == null || item.getType().isAir())
                    continue;
                inspected = true;
                InventoryMoveItemEvent moveEvent = new InventoryMoveItemEvent(carried, item.clone(), destination, true);
                Bukkit.getPluginManager().callEvent(moveEvent);
                if (moveEvent.isCancelled() || !moveEvent.getItem().isSimilar(item)
                        || moveEvent.getItem().getAmount() > item.getAmount())
                    continue;
                ItemStack offered = moveEvent.getItem().clone();
                Map<Integer, ItemStack> leftovers = destination.addItem(offered);
                int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                int transferred = offered.getAmount() - leftoverAmount;
                if (transferred <= 0)
                    continue;
                int remaining = item.getAmount() - transferred;
                if (remaining <= 0)
                    carried.setItem(slot, null);
                else {
                    ItemStack remainder = item.clone();
                    remainder.setAmount(remaining);
                    carried.setItem(slot, remainder);
                }
            }
            if (inspected)
                animateContainerInteraction(container.getLocation());
        }
        for (ItemStack item : carried.getContents()) {
            if (item != null && !item.getType().isAir())
                dropItemForNpc(instance, item);
        }
        updateTemporaryInventory(instance, new ItemStack[27], null);
    }

    private void dropItemForNpc(NpcInstance instance, ItemStack item) {
        Location center = instance.getLocation();
        if (center.getWorld() == null)
            return;
        center.getWorld().dropItemNaturally(center, item);
        itemPickupLockedUntilTick.put(instance.getId(), currentTick + OWN_DROP_PICKUP_LOCK_TICKS);
    }

    private void harvestNearbyCrops(NpcInstance instance) {
        Location center = instance.getLocation();
        if (center.getWorld() == null)
            return;
        Inventory carried = Bukkit.createInventory(null, 27);
        carried.setContents(instance.getTemporaryInventoryContents());
        LivingEntity entity = instances.findEntity(instance).orElse(null);
        boolean worked = false;

        for (int y = -1; y <= 2; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    Planting planting = plantingForCrop(block.getType());
                    if (planting == null || !(block.getBlockData() instanceof Ageable age)
                            || age.getAge() < age.getMaximumAge())
                        continue;
                    boolean replant = carried.contains(planting.item());
                    BlockData replacement = replant
                            ? planting.crop().createBlockData()
                            : Material.AIR.createBlockData();
                    if (replacement instanceof Ageable replanted)
                        replanted.setAge(0);
                    if (!authorizeBlockChange(instance, block, replacement))
                        continue;
                    for (ItemStack drop : block.getDrops())
                        addHarvestDrop(carried, center, drop);
                    if (replant)
                        consumeOne(carried, planting.item());
                    block.setBlockData(replacement, true);
                    worked = true;
                }
            }
        }

        for (int y = -1; y <= 1; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    Block soil = center.getBlock().getRelative(x, y, z);
                    Block above = soil.getRelative(0, 1, 0);
                    if (!above.getType().isAir())
                        continue;
                    Planting planting = firstPlantingForSoil(carried, soil.getType());
                    if (planting == null || !consumeOne(carried, planting.item()))
                        continue;
                    BlockData planted = planting.crop().createBlockData();
                    if (!authorizeBlockChange(instance, above, planted)) {
                        carried.addItem(new ItemStack(planting.item()));
                        continue;
                    }
                    above.setBlockData(planted, true);
                    worked = true;
                }
            }
        }
        updateTemporaryInventory(instance, carried.getContents(), null);
        if (worked && entity != null)
            entity.swingMainHand();
    }

    private void addHarvestDrop(Inventory carried, Location fallback, ItemStack drop) {
        for (ItemStack leftover : carried.addItem(drop).values()) {
            fallback.getWorld().dropItemNaturally(fallback, leftover);
        }
    }

    private boolean authorizeBlockChange(NpcInstance instance, Block block, BlockData replacement) {
        LivingEntity npc = instances.findEntity(instance).orElse(null);
        if (npc == null)
            return false;
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(npc, block, replacement);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private boolean consumeOne(Inventory inventory, Material material) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != material)
                continue;
            if (item.getAmount() == 1)
                inventory.setItem(slot, null);
            else
                item.setAmount(item.getAmount() - 1);
            return true;
        }
        return false;
    }

    private Planting firstPlantingForSoil(Inventory inventory, Material soil) {
        List<Planting> options = soil == Material.FARMLAND
                ? List.of(new Planting(Material.WHEAT_SEEDS, Material.WHEAT),
                        new Planting(Material.CARROT, Material.CARROTS),
                        new Planting(Material.POTATO, Material.POTATOES),
                        new Planting(Material.BEETROOT_SEEDS, Material.BEETROOTS),
                        new Planting(Material.TORCHFLOWER_SEEDS, Material.TORCHFLOWER_CROP))
                : soil == Material.SOUL_SAND
                        ? List.of(new Planting(Material.NETHER_WART, Material.NETHER_WART))
                        : List.of();
        for (Planting option : options) {
            if (inventory.contains(option.item()))
                return option;
        }
        return null;
    }

    private Planting plantingForCrop(Material crop) {
        return switch (crop) {
            case WHEAT -> new Planting(Material.WHEAT_SEEDS, crop);
            case CARROTS -> new Planting(Material.CARROT, crop);
            case POTATOES -> new Planting(Material.POTATO, crop);
            case BEETROOTS -> new Planting(Material.BEETROOT_SEEDS, crop);
            case NETHER_WART -> new Planting(Material.NETHER_WART, crop);
            case TORCHFLOWER_CROP -> new Planting(Material.TORCHFLOWER_SEEDS, crop);
            default -> null;
        };
    }

    private record Planting(Material item, Material crop) {
    }

    private record InventorySource(Inventory inventory, Location containerLocation) {
    }

    private void animateContainerInteraction(Location location) {
        if (location == null || location.getWorld() == null)
            return;
        BlockState state = location.getBlock().getState();
        if (!(state instanceof Lidded lidded) || lidded.isOpen())
            return;
        lidded.open();
        Location blockLocation = location.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BlockState current = blockLocation.getBlock().getState();
            if (current instanceof Lidded currentLid && currentLid.isOpen()) {
                currentLid.close();
            }
        }, CONTAINER_CLOSE_DELAY_TICKS);
    }

    public record NpcInventoryHolder(UUID instanceId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static boolean isMineable(Material material) {
        return Tag.MINEABLE_PICKAXE.isTagged(material);
    }

    private static boolean matchesMiningTarget(Material material, String target) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT);
        if (target.equals("ores") || target.equals("all_ores") || target.equals("ore")) {
            return name.endsWith("_ore") || material == Material.ANCIENT_DEBRIS;
        }
        if (target.equals("trees") || target.equals("all_trees") || target.equals("logs") || target.equals("wood")) {
            return Tag.LOGS.isTagged(material);
        }
        if (target.equals("mineable_blocks") || target.equals("all_mineable_blocks") || target.equals("all_blocks"))
            return isMineable(material);
        if (name.equals(target))
            return isMineable(material) || Tag.LOGS.isTagged(material);
        String resource = target.endsWith("s") ? target.substring(0, target.length() - 1) : target;
        return (name.equals(resource + "_ore") || name.equals("deepslate_" + resource + "_ore"))
                && (name.endsWith("_ore") || material == Material.ANCIENT_DEBRIS);
    }

    private java.util.Optional<Player> nearestPlayer(NpcInstance instance) {
        Location location = instance.getLocation();
        if (location.getWorld() == null) {
            return java.util.Optional.empty();
        }
        return Bukkit.getOnlinePlayers().stream().map(player -> (Player) player)
                .filter(player -> player.getWorld() == location.getWorld())
                .filter(player -> player.getLocation().distanceSquared(location) <= FOLLOW_ACQUIRE_RANGE_SQUARED)
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(location)));
    }

    private void tickPlayerLook() {
        for (NpcInstance instance : instances.findActive()) {
            if (combatService != null && combatService.isEngaged(instance))
                continue;
            NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
            if (definition == null || !definition.isLookAtPlayer())
                continue;
            nearestPlayer(instance).ifPresent(player -> instances.lookAt(instance, player.getEyeLocation()));
        }
    }

    private void tickItemPickup() {
        for (NpcInstance instance : instances.findActive()) {
            NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
            if (definition == null || !definition.isItemPickup())
                continue;
            if (currentTick < itemPickupLockedUntilTick.getOrDefault(instance.getId(), 0L))
                continue;
            LivingEntity npc = instances.findEntity(instance).orElse(null);
            Location location = instance.getLocation();
            if (npc == null || location.getWorld() == null)
                continue;
            location.getWorld()
                    .getNearbyEntities(location, ITEM_PICKUP_HORIZONTAL_RANGE, ITEM_PICKUP_VERTICAL_RANGE,
                            ITEM_PICKUP_HORIZONTAL_RANGE, entity -> entity instanceof Item)
                    .stream().map(entity -> (Item) entity)
                    .sorted(Comparator.comparingDouble(item -> item.getLocation().distanceSquared(location)))
                    .forEach(item -> pickUpItem(instance, npc, item));
        }
    }

    private void pickUpItem(NpcInstance instance, LivingEntity npc, Item item) {
        if (!item.isValid() || item.getPickupDelay() > 0)
            return;
        UUID owner = item.getOwner();
        if (owner != null && !owner.equals(npc.getUniqueId()))
            return;

        Inventory carried = Bukkit.createInventory(null, 27);
        carried.setContents(instance.getTemporaryInventoryContents());
        ItemStack offered = item.getItemStack().clone();
        Map<Integer, ItemStack> leftovers = carried.addItem(offered);
        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int pickedUp = item.getItemStack().getAmount() - remaining;
        if (pickedUp <= 0)
            return;

        EntityPickupItemEvent event = new EntityPickupItemEvent(npc, item, remaining);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        updateTemporaryInventory(instance, carried.getContents(), item);
        if (remaining == 0) {
            item.remove();
        } else {
            ItemStack leftover = item.getItemStack().clone();
            leftover.setAmount(remaining);
            item.setItemStack(leftover);
        }
        npc.getWorld().playSound(npc.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.0f);
    }

    private boolean isFollowTarget(NpcInstance instance, Player player) {
        return player != null && player.isOnline() && player.getWorld() == instance.getLocation().getWorld();
    }

    private void tickProximity() {
        Set<ProximityKey> nowNearby = new HashSet<>();
        Set<ProximityKey> evaluated = new HashSet<>();
        Map<UUID, NpcInstance> activeInstances = new HashMap<>();
        for (NpcInstance instance : instances.findActive()) {
            activeInstances.put(instance.getId(), instance);
            Location npcLocation = instance.getLocation();
            if (npcLocation.getWorld() == null) {
                continue;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                ProximityKey key = new ProximityKey(instance.getId(), player.getUniqueId());
                evaluated.add(key);
                boolean wasNearby = nearbyPlayers.contains(key);
                double range = wasNearby ? LEAVE_RANGE_SQUARED : APPROACH_RANGE_SQUARED;
                boolean withinRange = player.getWorld() == npcLocation.getWorld()
                        && player.getLocation().distanceSquared(npcLocation) <= range;
                if (withinRange == wasNearby) {
                    if (wasNearby) {
                        nowNearby.add(key);
                    }
                    continue;
                }
                if (isProximityCoolingDown(key)) {
                    // Preserve the last logical state and retry after the
                    // debounce. This prevents NPC movement caused by the first
                    // action from immediately firing its opposite event.
                    if (wasNearby) {
                        nowNearby.add(key);
                    }
                    continue;
                }
                markProximityTransition(key);
                if (withinRange) {
                    nowNearby.add(key);
                    trigger(BehaviourEvent.PLAYER_APPROACH, instance, player);
                } else {
                    trigger(BehaviourEvent.PLAYER_LEAVES, instance, player);
                }
            }
        }
        for (ProximityKey key : nearbyPlayers) {
            if (evaluated.contains(key)) {
                continue;
            }
            NpcInstance instance = activeInstances.get(key.instanceId());
            if (instance == null) {
                continue;
            }
            if (isProximityCoolingDown(key)) {
                nowNearby.add(key);
            } else {
                markProximityTransition(key);
                trigger(BehaviourEvent.PLAYER_LEAVES, instance, Bukkit.getPlayer(key.playerId()));
            }
        }
        nearbyPlayers.clear();
        nearbyPlayers.addAll(nowNearby);
        proximityCooldownUntilTick.entrySet()
                .removeIf(entry -> entry.getValue() <= currentTick && !nearbyPlayers.contains(entry.getKey()));
    }

    private boolean isProximityCoolingDown(ProximityKey key) {
        return currentTick < proximityCooldownUntilTick.getOrDefault(key, 0L);
    }

    private void markProximityTransition(ProximityKey key) {
        if (proximityCooldownTicks > 0L) {
            proximityCooldownUntilTick.put(key, currentTick + proximityCooldownTicks);
        }
    }

    private void sendDialog(BehaviourEvent event, NpcInstance instance, NpcDefinition definition, String line,
            Entity actor) {
        if (line == null) {
            return;
        }
        List<Component> messages = UiText.npcDialogMessages(definition.getDisplayName(), line,
                definition.getColor().textColor());
        if ((event == BehaviourEvent.PLAYER_APPROACH || event == BehaviourEvent.PLAYER_LEAVES)
                && actor instanceof Player player && player.isOnline()) {
            messages.forEach(player::sendMessage);
            return;
        }
        Location location = instance.getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == location.getWorld()
                    && player.getLocation().distanceSquared(location) <= DIALOG_RANGE_SQUARED) {
                messages.forEach(player::sendMessage);
            }
        }
    }

    private String placeholders(String value, NpcInstance instance, NpcDefinition definition, Entity actor) {
        return value.replace("%npc%", definition.getDisplayName()).replace("%instance%", instance.getId().toString())
                .replace("%player%", actor instanceof Player player ? player.getName() : "");
    }

    private record ProximityKey(UUID instanceId, UUID playerId) {

    }

    private static final class FollowState {

        private UUID playerId;
        private Location navigationTarget;
        private int repathTicks;
        private boolean moving;
    }

    private enum AiInteractionKind {
        SWITCH, TAKE_FROM_CONTAINER, STORE_IN_CONTAINER
    }

    private record AiInteraction(AiInteractionKind kind, Location blockLocation, Location navigationTarget) {
    }

    private record AiInteractionRequest(AiInteractionKind kind, Location blockLocation) {
        private AiInteractionRequest {
            blockLocation = blockLocation == null ? null : blockLocation.clone();
        }
    }
}
