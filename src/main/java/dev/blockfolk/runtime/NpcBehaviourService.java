package dev.blockfolk.runtime;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.dialog.DialogService;
import dev.blockfolk.model.ActionLocation;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
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
    private static final double FOLLOW_ACQUIRE_RANGE_SQUARED = 16.0 * 16.0;
    private static final double FOLLOW_STOP_RANGE_SQUARED = 3.0 * 3.0;
    private static final double FOLLOW_RESUME_RANGE_SQUARED = 5.0 * 5.0;
    private static final double FOLLOW_CATCH_UP_RANGE_SQUARED = 12.0 * 12.0;
    private static final int FOLLOW_REPATH_TICKS = 10;
    private static final long IDLE_REPEAT_TICKS = 1L * 20L;
    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final DialogService dialogService;
    private final Set<UUID> lowHealthTriggered = new HashSet<>();
    private final Map<UUID, String> routeOverrides = new HashMap<>();
    private final Map<UUID, WalkingSpeed> speedOverrides = new HashMap<>();
    private final Set<UUID> routePaused = new HashSet<>();
    private final Map<UUID, Location> moveTargets = new HashMap<>();
    private final Map<UUID, FollowState> following = new HashMap<>();
    private final Map<UUID, Object> idleCycles = new HashMap<>();
    private final Set<ProximityKey> nearbyPlayers = new HashSet<>();
    private final Map<ProximityKey, Long> proximityCooldownUntilTick = new HashMap<>();
    private final Map<UUID, Long> lastWorldTimes = new HashMap<>();
    private final long proximityCooldownTicks;
    private NpcCombatService combatService;
    private BukkitTask behaviourTask;
    private long currentTick;
    private int proximityTick;
    private long customEmissionTick = -1L;
    private int customEmissionsThisTick;

    public NpcBehaviourService(
            Plugin plugin,
            NpcDefinitionRepository definitions,
            NpcInstanceRegistry instances,
            DialogService dialogService,
            int proximityCooldownSeconds
    ) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
        this.dialogService = dialogService;
        this.proximityCooldownTicks = Math.max(0L, proximityCooldownSeconds) * 20L;
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
        moveTargets.clear();
        following.clear();
        idleCycles.clear();
        nearbyPlayers.clear();
        proximityCooldownUntilTick.clear();
        lastWorldTimes.clear();
    }

    public void trigger(BehaviourEvent event, NpcInstance instance, Entity actor) {
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null) {
            return;
        }
        Runnable completion = event == BehaviourEvent.SPAWN ? () -> startIdle(instance) : () -> { };
        executeSequence(event, definition.getBehaviourActions(event), 0, instance, definition, actor, completion);
    }

    public void triggerWaypointActions(List<BehaviourAction> actions, NpcInstance instance) {
        NpcDefinition definition = definitions.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null || actions == null || actions.isEmpty()) {
            return;
        }
        executeSequence(null, List.copyOf(actions), 0, instance, definition, null, () -> { });
    }

    public void emitCustomEvent(String eventName, Entity actor) {
        if (eventName == null || eventName.isBlank()) return;
        if (customEmissionTick != currentTick) {
            customEmissionTick = currentTick;
            customEmissionsThisTick = 0;
        }
        if (++customEmissionsThisTick > 64) {
            plugin.getLogger().warning("Stopped a custom NPC event chain after 64 emissions in one tick.");
            return;
        }
        for (NpcInstance target : List.copyOf(instances.findAll())) {
            NpcDefinition definition = definitions.find(target.getDefinitionKey()).orElse(null);
            if (definition == null) continue;
            List<BehaviourAction> actions = definition.getCustomEventActions(eventName);
            if (!actions.isEmpty()) executeSequence(null, actions, 0, target, definition, actor, () -> { });
        }
    }

    public void forget(NpcInstance instance) {
        lowHealthTriggered.remove(instance.getId());
        routeOverrides.remove(instance.getId());
        speedOverrides.remove(instance.getId());
        routePaused.remove(instance.getId());
        moveTargets.remove(instance.getId());
        following.remove(instance.getId());
        idleCycles.remove(instance.getId());
        nearbyPlayers.removeIf(key -> key.instanceId().equals(instance.getId()));
        proximityCooldownUntilTick.keySet().removeIf(key -> key.instanceId().equals(instance.getId()));
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

    public boolean isFollowing(NpcInstance instance) {
        return following.containsKey(instance.getId());
    }

    public boolean isMovingTo(NpcInstance instance) {
        return moveTargets.containsKey(instance.getId());
    }

    private void executeSequence(
            BehaviourEvent event,
            java.util.List<BehaviourAction> actions,
            int index,
            NpcInstance instance,
            NpcDefinition definition,
            Entity actor,
            Runnable completion
    ) {
        if (instances.findAll().stream().noneMatch(candidate -> candidate.getId().equals(instance.getId()))) {
            return;
        }
        if (index >= actions.size()) {
            completion.run();
            return;
        }
        BehaviourAction action = actions.get(index);
        execute(event, action, instance, definition, actor);
        long delayTicks = delayAfter(action);
        if (delayTicks <= 0L) {
            executeSequence(event, actions, index + 1, instance, definition, actor, completion);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> executeSequence(event, actions, index + 1, instance, definition, actor, completion), delayTicks);
        }
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
        executeSequence(BehaviourEvent.IDLE, definition.getBehaviourActions(BehaviourEvent.IDLE), 0,
                instance, definition, null, () -> Bukkit.getScheduler().runTaskLater(plugin,
                        () -> runIdleCycle(instance, token), IDLE_REPEAT_TICKS));
    }

    private long delayAfter(BehaviourAction action) {
        if (action.type() == BehaviourActionType.WAIT) {
            return secondsToTicks(action.value());
        }
        if (action.type() == BehaviourActionType.SEND_DIALOG
                || action.type() == BehaviourActionType.SHOW_HOLO_DIALOG) {
            return dialogService.secondsPerLine() * 20L;
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
            case WAIT -> { }
            case INTERACT -> interactWithNearbySwitches(instance);
            case MINE_BLOCKS -> mineNearbyBlocks(instance);
            case TAKE_ITEM -> takeNearbyItem(instance, actor);
            case SHOW_INVENTORY -> showInventory(instance, actor);
            case EMIT_EVENT -> emitCustomEvent(action.value(), actor != null
                    ? actor : instances.findEntity(instance).orElse(null));
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

    private void tickBehaviour() {
        currentTick++;
        tickTimeEvents();
        if (++proximityTick >= 10) {
            proximityTick = 0;
            tickProximity();
        }
        Set<UUID> active = new HashSet<>();
        for (NpcInstance instance : instances.findAll()) {
            active.add(instance.getId());
            tickMoveTo(instance);
            tickFollow(instance);
        }
        routePaused.retainAll(active);
        moveTargets.keySet().retainAll(active);
        following.keySet().retainAll(active);
        idleCycles.keySet().retainAll(active);
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
            if (crossedTime(previous, elapsed, 23000L)) {
                triggerTimeEvent(world, BehaviourEvent.DAWN);
            }
            if (crossedTime(previous, elapsed, 0L)) {
                triggerTimeEvent(world, BehaviourEvent.MORNING);
            }
        }
        lastWorldTimes.keySet().retainAll(activeWorlds);
    }

    private boolean crossedTime(long previous, long elapsed, long threshold) {
        long distance = Math.floorMod(threshold - previous, 24000L);
        return distance > 0L && distance <= elapsed;
    }

    private void triggerTimeEvent(World world, BehaviourEvent event) {
        for (NpcInstance instance : List.copyOf(instances.findAll())) {
            if (instance.getLocation().getWorld() == world) {
                trigger(event, instance, null);
            }
        }
    }

    private void tickMoveTo(NpcInstance instance) {
        Location target = moveTargets.get(instance.getId());
        if (target == null || combatService != null && combatService.isEngaged(instance)) {
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
        FollowState state = new FollowState();
        state.playerId = nearestPlayer(instance).map(Player::getUniqueId).orElse(null);
        following.put(instance.getId(), state);
        instances.stand(instance);
        instances.stopNavigating(instance);
    }

    private void stopFollowing(NpcInstance instance) {
        if (following.remove(instance.getId()) != null) {
            instances.stopNavigating(instance);
        }
    }

    private void tickFollow(NpcInstance instance) {
        FollowState state = following.get(instance.getId());
        if (state == null || combatService != null && combatService.isEngaged(instance)) {
            return;
        }
        Player player = state.playerId == null ? null : Bukkit.getPlayer(state.playerId);
        if (!isFollowTarget(instance, player)) {
            player = nearestPlayer(instance).orElse(null);
            state.playerId = player == null ? null : player.getUniqueId();
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
                ? WalkingSpeed.VERY_FAST : WalkingSpeed.NORMAL;
        instances.navigate(instance, state.navigationTarget, speed);
    }

    private void interactWithNearbySwitches(NpcInstance instance) {
        Location center = instance.getLocation();
        if (center.getWorld() == null) return;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (block.getType() != Material.LEVER && !Tag.BUTTONS.isTagged(block.getType())) continue;
                    BlockData data = block.getBlockData();
                    if (!(data instanceof Powerable powerable)) continue;
                    boolean button = Tag.BUTTONS.isTagged(block.getType());
                    if (button && powerable.isPowered()) continue;
                    int oldCurrent = powerable.isPowered() ? 15 : 0;
                    BlockRedstoneEvent event = new BlockRedstoneEvent(block, oldCurrent, oldCurrent == 0 ? 15 : 0);
                    Bukkit.getPluginManager().callEvent(event);
                    powerable.setPowered(event.getNewCurrent() > 0);
                    block.setBlockData(data, true);
                    if (button && powerable.isPowered()) {
                        Material pressedType = block.getType();
                        long releaseDelay = pressedType == Material.STONE_BUTTON
                                || pressedType == Material.POLISHED_BLACKSTONE_BUTTON ? 20L : 30L;
                        Location buttonLocation = block.getLocation();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> releaseButton(buttonLocation), releaseDelay);
                    }
                }
            }
        }
    }

    private void releaseButton(Location location) {
        Block block = location.getBlock();
        if (!Tag.BUTTONS.isTagged(block.getType())) return;
        BlockData data = block.getBlockData();
        if (!(data instanceof Powerable powerable) || !powerable.isPowered()) return;
        BlockRedstoneEvent event = new BlockRedstoneEvent(block, 15, 0);
        Bukkit.getPluginManager().callEvent(event);
        powerable.setPowered(event.getNewCurrent() > 0);
        block.setBlockData(data, true);
    }

    private void mineNearbyBlocks(NpcInstance instance) {
        Location feet = instance.getLocation();
        if (feet.getWorld() == null) return;
        LivingEntity entity = instances.findEntity(instance).orElse(null);
        org.bukkit.inventory.ItemStack tool = entity == null || entity.getEquipment() == null
                ? null : entity.getEquipment().getItemInMainHand();
        boolean mined = false;
        // The four layers begin at the NPC's feet. The supporting y - 1 layer is never visited.
        for (int y = 0; y < 4; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = feet.getBlock().getRelative(x, y, z);
                    if (!isMineable(block.getType()) || block.getState() instanceof TileState) continue;
                    ItemStack effectiveTool = tool == null ? new ItemStack(Material.AIR) : tool;
                    for (ItemStack drop : block.getDrops(effectiveTool, entity)) {
                        feet.getWorld().dropItemNaturally(block.getLocation(), drop);
                    }
                    block.setType(Material.AIR, true);
                    mined = true;
                }
            }
        }
        if (mined && entity != null) entity.swingMainHand();
    }

    private void takeNearbyItem(NpcInstance instance, Entity actor) {
        Inventory source = nearbySourceInventory(instance, actor);
        if (source == null) return;

        Inventory carried = Bukkit.createInventory(null, 27);
        carried.setContents(instance.getTemporaryInventoryContents());
        for (int slot = 0; slot < source.getSize(); slot++) {
            ItemStack item = source.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            ItemStack offered = item.clone();
            Map<Integer, ItemStack> leftovers = carried.addItem(offered);
            int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int moved = item.getAmount() - remaining;
            if (moved <= 0) return;
            if (moved == item.getAmount()) source.setItem(slot, null);
            else item.setAmount(item.getAmount() - moved);
            instance.setTemporaryInventoryContents(carried.getContents());
            return;
        }
    }

    private Inventory nearbySourceInventory(NpcInstance instance, Entity actor) {
        Location center = instance.getLocation();
        if (center.getWorld() == null) return null;
        Inventory nearest = null;
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
                    if (!(block.getState() instanceof Container container)) continue;
                    double distance = block.getLocation().add(.5, .5, .5).distanceSquared(center);
                    if (distance < nearestDistance) {
                        nearest = container.getInventory();
                        nearestDistance = distance;
                    }
                }
            }
        }
        for (Player player : center.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(center);
            if (distance <= 16.0 && distance < nearestDistance) {
                nearest = player.getInventory();
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void showInventory(NpcInstance instance, Entity actor) {
        Player player = actor instanceof Player direct ? direct : nearestPlayer(instance).orElse(null);
        if (player == null) return;
        Inventory inventory = Bukkit.createInventory(new NpcInventoryHolder(instance.getId()), 27,
                Component.text("NPC Inventory"));
        inventory.setContents(instance.getTemporaryInventoryContents());
        player.openInventory(inventory);
    }

    public record NpcInventoryHolder(UUID instanceId) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static boolean isMineable(Material material) {
        return Tag.MINEABLE_PICKAXE.isTagged(material);
    }

    private java.util.Optional<Player> nearestPlayer(NpcInstance instance) {
        Location location = instance.getLocation();
        if (location.getWorld() == null) {
            return java.util.Optional.empty();
        }
        return Bukkit.getOnlinePlayers().stream()
                .map(player -> (Player) player)
                .filter(player -> player.getWorld() == location.getWorld())
                .filter(player -> player.getLocation().distanceSquared(location) <= FOLLOW_ACQUIRE_RANGE_SQUARED)
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(location)));
    }

    private boolean isFollowTarget(NpcInstance instance, Player player) {
        return player != null && player.isOnline()
                && player.getWorld() == instance.getLocation().getWorld();
    }

    private void tickProximity() {
        Set<ProximityKey> nowNearby = new HashSet<>();
        Set<ProximityKey> evaluated = new HashSet<>();
        Map<UUID, NpcInstance> activeInstances = new HashMap<>();
        for (NpcInstance instance : instances.findAll()) {
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
        proximityCooldownUntilTick.entrySet().removeIf(entry -> entry.getValue() <= currentTick
                && !nearbyPlayers.contains(entry.getKey()));
    }

    private boolean isProximityCoolingDown(ProximityKey key) {
        return currentTick < proximityCooldownUntilTick.getOrDefault(key, 0L);
    }

    private void markProximityTransition(ProximityKey key) {
        if (proximityCooldownTicks > 0L) {
            proximityCooldownUntilTick.put(key, currentTick + proximityCooldownTicks);
        }
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

    private static final class FollowState {

        private UUID playerId;
        private Location navigationTarget;
        private int repathTicks;
        private boolean moving;
    }
}
