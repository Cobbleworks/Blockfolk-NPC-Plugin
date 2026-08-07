package dev.blockfolk.runtime;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.blockfolk.combat.NpcAttack;
import dev.blockfolk.combat.NpcAttackSelector;
import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.FightOptions;
import dev.blockfolk.model.LootTier;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.util.LegacyText;
import net.kyori.adventure.text.Component;

public final class NpcCombatService implements Listener {

    private static final double SIGHT_RANGE = 16.0;
    private static final double MAX_CHASE_RANGE_SQUARED = 32.0 * 32.0;
    private static final int LOST_SIGHT_TIMEOUT_TICKS = 5 * 20;
    private static final int STALLED_CHASE_TIMEOUT_TICKS = 4 * 20;
    private static final int UNREACHABLE_TARGET_COOLDOWN_TICKS = 3 * 20;
    private static final double CHASE_PROGRESS_DISTANCE_SQUARED = 0.5 * 0.5;
    private static final int FLEE_TICKS = 8 * 20;
    private static final double BOSS_BAR_RANGE_SQUARED = 16.0 * 16.0;

    private final Plugin plugin;
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final NativeNpcNavigationService navigationService;
    private final NpcAttackSelector attackSelector = new NpcAttackSelector();
    private final Map<UUID, CombatState> states = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingRespawns = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, FightOptions> fightOptionsOverrides = new HashMap<>();
    private final Map<TargetKey, Long> unreachableUntil = new HashMap<>();
    private NpcBehaviourService behaviourService;
    private BukkitTask task;
    private long currentTick;

    public NpcCombatService(Plugin plugin, NpcDefinitionRepository definitionRepository,
            NpcInstanceRegistry instanceRegistry, NativeNpcNavigationService navigationService) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.navigationService = navigationService;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        for (NpcInstance instance : instanceRegistry.findAll()) {
            if (instance.isAwaitingRespawn())
                scheduleRespawn(instance);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (NpcInstance instance : instanceRegistry.findAll()) {
            if (states.containsKey(instance.getId())) {
                instanceRegistry.stopNavigating(instance);
            }
        }
        pendingRespawns.values().forEach(BukkitTask::cancel);
        pendingRespawns.clear();
        states.clear();
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
        fightOptionsOverrides.clear();
        unreachableUntil.clear();
    }

    public boolean isEngaged(NpcInstance instance) {
        return states.containsKey(instance.getId());
    }

    public Entity currentTarget(NpcInstance instance) {
        CombatState state = states.get(instance.getId());
        return state == null ? null : Bukkit.getEntity(state.entityId);
    }

    public void setBehaviourService(NpcBehaviourService behaviourService) {
        this.behaviourService = behaviourService;
    }

    public boolean startCombat(NpcInstance instance, Entity target) {
        return startDirectedCombat(instance, target);
    }

    /**
     * Starts explicitly directed combat without consulting normal category
     * targeting preferences.
     */
    public boolean startDirectedCombat(NpcInstance instance, Entity target) {
        if (!(target instanceof LivingEntity living) || !isAttackable(instance, living)) {
            return false;
        }
        NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
        if (definition != null && !definition.getCombatProfile().invulnerable()) {
            engage(instance, definition, CombatMode.FIGHT, living);
            return true;
        }
        return false;
    }

    /**
     * Finds the closest entity the NPC may legally attack, independent of
     * aggression settings.
     */
    public LivingEntity findNearestAttackableTarget(NpcInstance instance) {
        LivingEntity npc = instanceRegistry.findEntity(instance).orElse(null);
        if (npc == null)
            return null;
        return npc.getNearbyEntities(SIGHT_RANGE, SIGHT_RANGE, SIGHT_RANGE).stream()
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .filter(target -> isAttackable(instance, target))
                .filter(target -> target.getLocation().distanceSquared(npc.getLocation()) <= SIGHT_RANGE * SIGHT_RANGE)
                .filter(npc::hasLineOfSight)
                .min(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(npc.getLocation())))
                .orElse(null);
    }

    public void exitCombat(NpcInstance instance) {
        clearState(instance);
    }

    public void changeFightOptions(NpcInstance instance, FightOptions options) {
        fightOptionsOverrides.put(instance.getId(), options);
        clearState(instance);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcSuffocation(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) {
            return;
        }
        if (instanceRegistry.findByEntityId(event.getEntity().getEntityId()).isPresent()) {
            // Movement is driven by a deliberately tiny, invisible pathfinder so
            // it cannot steal player attacks from the visible mannequin. Near a
            // wall, that navigator can briefly place the full-sized mannequin's
            // bounding box inside a block. This is a renderer/navigation artifact,
            // not meaningful combat damage, so suppress only the resulting
            // suffocation tick while preserving every other damage source.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageByEntityEvent event) {
        NpcInstance instance = instanceRegistry.findByEntityId(event.getEntity().getEntityId()).orElse(null);
        if (instance == null) {
            return;
        }
        NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
        if (definition == null || definition.getCombatProfile().invulnerable()) {
            event.setCancelled(true);
            return;
        }

        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !isAttackable(instance, attacker)) {
            return;
        }
        FightOptions options = fightOptions(instance, definition.getCombatProfile());
        AttackReaction reaction = options.attackReaction();
        boolean selectedTarget = isSelectedTarget(attacker, options);
        if (reaction == AttackReaction.FLEE && !selectedTarget) {
            engage(instance, definition, CombatMode.FLEE, attacker);
        } else if (reaction == AttackReaction.FIGHT_BACK || selectedTarget) {
            engage(instance, definition, CombatMode.FIGHT, attacker);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcDeath(EntityDeathEvent event) {
        NpcInstance instance = instanceRegistry.findByEntityId(event.getEntity().getEntityId()).orElse(null);
        if (instance == null) {
            return;
        }
        event.getDrops().clear();
        NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
        if (definition != null) {
            ItemStack[] contents = definition.getInventoryContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (LootTier.isRowStarterSlot(slot)) {
                    continue;
                }
                ItemStack item = contents[slot];
                LootTier tier = LootTier.forInventorySlot(slot);
                if (item != null && !item.getType().isAir() && item.getAmount() > 0
                        && tier.shouldDrop(ThreadLocalRandom.current().nextDouble())) {
                    event.getDrops().add(item);
                }
            }
        }
        for (ItemStack item : instance.getTemporaryInventoryContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                event.getDrops().add(item);
            }
        }
        event.setDroppedExp(definition == null ? 0 : definition.getCombatProfile().droppedExperience());
        clearState(instance);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (definition == null || definition.getCombatProfile().respawnSeconds() == 0) {
                instanceRegistry.deleteInstance(instance.getId());
                return;
            }
            long respawnAt = System.currentTimeMillis() + definition.getCombatProfile().respawnSeconds() * 1_000L;
            instanceRegistry.markAwaitingRespawn(instance, respawnAt);
            scheduleRespawn(instance);
        });
    }

    private void scheduleRespawn(NpcInstance instance) {
        BukkitTask previous = pendingRespawns.remove(instance.getId());
        if (previous != null)
            previous.cancel();
        long remainingMillis = Math.max(0L, instance.getRespawnAtEpochMillis() - System.currentTimeMillis());
        long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
        pendingRespawns.put(instance.getId(),
                Bukkit.getScheduler().runTaskLater(plugin, () -> attemptRespawn(instance), delayTicks));
    }

    private void attemptRespawn(NpcInstance instance) {
        pendingRespawns.remove(instance.getId());
        if (instanceRegistry.findById(instance.getId()).isEmpty())
            return;
        NpcDefinition current = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
        if (current == null || current.getCombatProfile().respawnSeconds() == 0) {
            instanceRegistry.deleteInstance(instance.getId());
            return;
        }
        if (!instanceRegistry.respawn(instance, current))
            scheduleRespawn(instance);
    }

    private void tick() {
        currentTick++;
        unreachableUntil.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
        Set<UUID> activeBossBars = new HashSet<>();
        Set<UUID> activeInstances = new HashSet<>();
        for (NpcInstance instance : instanceRegistry.findActive()) {
            activeInstances.add(instance.getId());
            NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
            LivingEntity npc = instanceRegistry.findEntity(instance).orElse(null);
            if (definition == null || npc == null || !npc.isValid() || npc.isDead()) {
                states.remove(instance.getId());
                fightOptionsOverrides.remove(instance.getId());
                continue;
            }
            CombatProfile profile = definition.getCombatProfile();
            FightOptions fightOptions = fightOptions(instance, profile);
            updateBossBar(instance, definition, npc, profile, activeBossBars);
            if (profile.invulnerable()) {
                clearState(instance);
                continue;
            }

            CombatState state = states.get(instance.getId());
            if (state == null && fightOptions.attackReaction() == AttackReaction.HUNTING
                    && currentTick % 10 == Math.floorMod(instance.getId().hashCode(), 10)) {
                LivingEntity target = findNearestTarget(instance, npc, fightOptions);
                if (target != null) {
                    engage(instance, definition, CombatMode.FIGHT, target);
                    state = states.get(instance.getId());
                }
            }
            if (state == null) {
                continue;
            }
            if (state.mode == CombatMode.FLEE) {
                flee(instance, npc, state);
            } else {
                fight(instance, npc, state);
            }
        }
        states.keySet().retainAll(activeInstances);
        fightOptionsOverrides.keySet().retainAll(activeInstances);
        bossBars.entrySet().removeIf(entry -> {
            if (activeBossBars.contains(entry.getKey()))
                return false;
            entry.getValue().removeAll();
            return true;
        });
    }

    private void updateBossBar(NpcInstance instance, NpcDefinition definition, LivingEntity npc, CombatProfile profile,
            Set<UUID> activeBossBars) {
        if (!profile.showBossBar() || profile.invulnerable()) {
            return;
        }
        activeBossBars.add(instance.getId());
        boolean updateViewers = currentTick % 5L == 0L || !bossBars.containsKey(instance.getId());
        BossBar bossBar = bossBars.computeIfAbsent(instance.getId(),
                ignored -> Bukkit.createBossBar(definition.getDisplayName(), BarColor.RED, BarStyle.SOLID));
        bossBar.setTitle(definition.getDisplayName());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, npc.getHealth() / profile.maxHealth())));
        if (!updateViewers)
            return;

        Set<Player> nearby = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == npc.getWorld()
                    && player.getLocation().distanceSquared(npc.getLocation()) <= BOSS_BAR_RANGE_SQUARED) {
                nearby.add(player);
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
        }
        for (Player viewer : List.copyOf(bossBar.getPlayers())) {
            if (!nearby.contains(viewer)) {
                bossBar.removePlayer(viewer);
            }
        }
    }

    private void flee(NpcInstance instance, LivingEntity npc, CombatState state) {
        Entity threat = Bukkit.getEntity(state.entityId);
        Location threatLocation = threat != null && threat.isValid() ? threat.getLocation() : state.lastKnownLocation;
        if (currentTick >= state.expiresAt || threatLocation == null || threatLocation.getWorld() != npc.getWorld()) {
            clearState(instance);
            return;
        }
        state.lastKnownLocation = threatLocation.clone();
        Location current = npc.getLocation();
        Vector away = current.toVector().subtract(threatLocation.toVector()).setY(0.0);
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1.0, 0.0, 0.0);
        }
        if (state.navigationTarget == null || currentTick >= state.nextRepathAt) {
            state.navigationTarget = current.clone().add(away.normalize().multiply(10.0));
            state.nextRepathAt = currentTick + 10;
        }
        instanceRegistry.navigate(instance, state.navigationTarget, WalkingSpeed.VERY_FAST);
    }

    private void fight(NpcInstance instance, LivingEntity npc, CombatState state) {
        Entity entity = Bukkit.getEntity(state.entityId);
        if (!(entity instanceof LivingEntity target) || !isAttackable(instance, target)
                || target.getWorld() != npc.getWorld()
                || target.getLocation().distanceSquared(npc.getLocation()) > MAX_CHASE_RANGE_SQUARED) {
            clearState(instance);
            return;
        }
        makeMobFightBack(target, npc);
        state.lastKnownLocation = target.getLocation();
        if (npc.hasLineOfSight(target)) {
            state.lastSeenAt = currentTick;
        } else if (currentTick - state.lastSeenAt >= LOST_SIGHT_TIMEOUT_TICKS) {
            abandonUnreachable(instance, state);
            return;
        }
        NpcAttack attack = attackSelector.select(npc.getEquipment().getItemInMainHand());
        double distanceSquared = npc.getLocation().distanceSquared(target.getLocation());
        if (distanceSquared > attack.rangeSquared()) {
            Location currentLocation = npc.getLocation();
            if (state.lastProgressLocation == null
                    || currentLocation.distanceSquared(state.lastProgressLocation) >= CHASE_PROGRESS_DISTANCE_SQUARED) {
                state.lastProgressLocation = currentLocation;
                state.lastProgressAt = currentTick;
            } else if (currentTick - state.lastProgressAt >= STALLED_CHASE_TIMEOUT_TICKS) {
                abandonUnreachable(instance, state);
                return;
            }
            if (state.navigationTarget == null || state.retreating || currentTick >= state.nextRepathAt) {
                state.navigationTarget = target.getLocation();
                state.nextRepathAt = currentTick + 10;
            }
            state.retreating = false;
            instanceRegistry.navigate(instance, state.navigationTarget, WalkingSpeed.FAST);
            return;
        }
        if (distanceSquared < attack.minimumRangeSquared()) {
            if (state.navigationTarget == null || !state.retreating || currentTick >= state.nextRepathAt) {
                state.navigationTarget = retreatLocation(npc, target, Math.sqrt(attack.minimumRangeSquared()));
                state.nextRepathAt = currentTick + 10;
            }
            state.retreating = true;
            instanceRegistry.navigate(instance, state.navigationTarget, WalkingSpeed.FAST);
            return;
        }

        state.lastProgressLocation = npc.getLocation();
        state.lastProgressAt = currentTick;
        instanceRegistry.stopNavigating(instance);
        state.navigationTarget = null;
        state.retreating = false;
        face(npc, target);
        if (currentTick >= state.nextAttackAt && npc.hasLineOfSight(target)) {
            attack.execute(npc, target);
            state.nextAttackAt = currentTick + attack.cooldownTicks();
        }
    }

    private Location retreatLocation(LivingEntity npc, LivingEntity target, double minimumRange) {
        Location current = npc.getLocation();
        Vector away = current.toVector().subtract(target.getLocation().toVector()).setY(0.0);
        if (away.lengthSquared() < 0.01) {
            away = current.getDirection().setY(0.0).multiply(-1.0);
        }
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1.0, 0.0, 0.0);
        }
        Location targetLocation = target.getLocation();
        double targetDistance = Math.sqrt(Math.pow(current.getX() - targetLocation.getX(), 2.0)
                + Math.pow(current.getZ() - targetLocation.getZ(), 2.0));
        double retreatDistance = Math.max(2.0, minimumRange - targetDistance + 1.5);
        return current.clone().add(away.normalize().multiply(retreatDistance));
    }

    private void engage(NpcInstance instance, NpcDefinition definition, CombatMode mode, LivingEntity entity) {
        CombatState previous = states.get(instance.getId());
        boolean enteringCombat = previous == null || previous.mode != mode
                || !previous.entityId.equals(entity.getUniqueId());
        if (previous != null && previous.mode == CombatMode.FIGHT
                && (mode != CombatMode.FIGHT || !previous.entityId.equals(entity.getUniqueId()))) {
            releaseMobTarget(instance, previous);
        }
        states.put(instance.getId(), new CombatState(mode, entity.getUniqueId(), entity.getLocation(),
                currentTick + (mode == CombatMode.FLEE ? FLEE_TICKS : Long.MAX_VALUE), currentTick));
        if (mode == CombatMode.FIGHT) {
            instanceRegistry.findEntity(instance).ifPresent(npc -> makeMobFightBack(entity, npc));
        }
        if (enteringCombat) {
            if (behaviourService != null) {
                behaviourService.trigger(dev.blockfolk.model.BehaviourEvent.COMBAT_ENTERED, instance, entity);
            }
        }
    }

    private LivingEntity findNearestTarget(NpcInstance instance, LivingEntity npc, FightOptions options) {
        return npc.getNearbyEntities(SIGHT_RANGE, SIGHT_RANGE, SIGHT_RANGE).stream()
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .filter(target -> isAttackable(instance, target)).filter(target -> isSelectedTarget(target, options))
                .filter(target -> !isTemporarilyUnreachable(instance, target)).filter(npc::hasLineOfSight)
                .min(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(npc.getLocation())))
                .orElse(null);
    }

    private boolean isSelectedTarget(LivingEntity target, FightOptions options) {
        if (target instanceof Mannequin) {
            return options.npcs();
        }
        if (target instanceof Player) {
            return options.players();
        }
        if (target instanceof Animals) {
            return options.animals();
        }
        return target instanceof Mob && options.mobs();
    }

    private FightOptions fightOptions(NpcInstance instance, CombatProfile profile) {
        return fightOptionsOverrides.getOrDefault(instance.getId(), FightOptions.from(profile));
    }

    private boolean isAttackable(NpcInstance attacker, LivingEntity target) {
        if (!target.isValid() || target.isDead() || target.getEntityId() == attacker.getEntityId()
                || navigationService.isNavigator(target)) {
            return false;
        }
        if (target instanceof Player player) {
            NpcDefinition attackerDefinition = definitionRepository.find(attacker.getDefinitionKey()).orElse(null);
            if (attackerDefinition != null
                    && carriesAlliance(player, attackerDefinition.getCombatProfile().alliance())) {
                return false;
            }
            return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
        }
        if (target instanceof Mannequin) {
            NpcInstance targetInstance = instanceRegistry.findByEntityId(target.getEntityId()).orElse(null);
            if (targetInstance == null) {
                return false;
            }
            NpcDefinition targetDefinition = definitionRepository.find(targetInstance.getDefinitionKey()).orElse(null);
            NpcDefinition attackerDefinition = definitionRepository.find(attacker.getDefinitionKey()).orElse(null);
            return targetDefinition != null && !targetDefinition.getCombatProfile().invulnerable()
                    && (attackerDefinition == null
                            || !attackerDefinition.getCombatProfile().alliedWith(targetDefinition.getCombatProfile()));
        }
        return target instanceof Mob;
    }

    private boolean carriesAlliance(Player player, String alliance) {
        if (alliance == null) {
            return false;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            Component displayName = meta == null ? null : meta.displayName();
            if (displayName != null) {
                String name = LegacyText.plainText(displayName);
                if (alliance.equalsIgnoreCase(name.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
    }

    private void face(LivingEntity npc, LivingEntity target) {
        Vector direction = target.getEyeLocation().toVector().subtract(npc.getEyeLocation().toVector());
        Location location = npc.getLocation();
        location.setDirection(direction);
        npc.setRotation(location.getYaw(), location.getPitch());
        npc.setBodyYaw(location.getYaw());
    }

    private void clearState(NpcInstance instance) {
        CombatState removed = states.remove(instance.getId());
        if (removed != null) {
            releaseMobTarget(instance, removed);
            instanceRegistry.stopNavigating(instance);
            if (behaviourService != null) {
                behaviourService.trigger(dev.blockfolk.model.BehaviourEvent.COMBAT_EXITED, instance,
                        Bukkit.getEntity(removed.entityId));
            }
        }
    }

    private void abandonUnreachable(NpcInstance instance, CombatState state) {
        unreachableUntil.put(new TargetKey(instance.getId(), state.entityId),
                currentTick + UNREACHABLE_TARGET_COOLDOWN_TICKS);
        clearState(instance);
    }

    private boolean isTemporarilyUnreachable(NpcInstance instance, LivingEntity target) {
        return unreachableUntil.getOrDefault(new TargetKey(instance.getId(), target.getUniqueId()), 0L) > currentTick;
    }

    private void makeMobFightBack(LivingEntity target, LivingEntity npc) {
        if (target instanceof Mob mob
                && (mob.getTarget() == null || !mob.getTarget().getUniqueId().equals(npc.getUniqueId()))) {
            mob.setTarget(npc);
        }
    }

    private void releaseMobTarget(NpcInstance instance, CombatState state) {
        Entity target = Bukkit.getEntity(state.entityId);
        LivingEntity npc = instanceRegistry.findEntity(instance).orElse(null);
        if (target instanceof Mob mob && npc != null && mob.getTarget() != null
                && mob.getTarget().getUniqueId().equals(npc.getUniqueId())) {
            mob.setTarget(null);
        }
    }

    private enum CombatMode {
        FLEE, FIGHT
    }

    private record TargetKey(UUID instanceId, UUID targetId) {
    }

    private static final class CombatState {

        private final CombatMode mode;
        private final UUID entityId;
        private Location lastKnownLocation;
        private final long expiresAt;
        private long nextAttackAt;
        private Location navigationTarget;
        private long nextRepathAt;
        private boolean retreating;
        private long lastSeenAt;
        private long lastProgressAt;
        private Location lastProgressLocation;

        private CombatState(CombatMode mode, UUID entityId, Location lastKnownLocation, long expiresAt,
                long nextAttackAt) {
            this.mode = mode;
            this.entityId = entityId;
            this.lastKnownLocation = lastKnownLocation.clone();
            this.expiresAt = expiresAt;
            this.nextAttackAt = nextAttackAt;
            this.lastSeenAt = nextAttackAt;
            this.lastProgressAt = nextAttackAt;
            this.lastProgressLocation = null;
        }
    }
}
