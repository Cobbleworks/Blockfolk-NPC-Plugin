package dev.easynpc.runtime;

import dev.easynpc.model.AggressionLevel;
import dev.easynpc.model.CombatProfile;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.model.WalkingSpeed;
import dev.easynpc.repository.NpcDefinitionRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NpcCombatService implements Listener {
    private static final double SIGHT_RANGE = 16.0;
    private static final double MAX_CHASE_RANGE_SQUARED = 32.0 * 32.0;
    private static final double ATTACK_RANGE_SQUARED = 3.0 * 3.0;
    private static final double SHOUT_RANGE_SQUARED = 24.0 * 24.0;
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    private static final int FLEE_TICKS = 8 * 20;

    private final Plugin plugin;
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final NativeNpcNavigationService navigationService;
    private final Map<UUID, CombatState> states = new HashMap<>();
    private BukkitTask task;
    private long currentTick;

    public NpcCombatService(
        Plugin plugin,
        NpcDefinitionRepository definitionRepository,
        NpcInstanceRegistry instanceRegistry,
        NativeNpcNavigationService navigationService
    ) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.navigationService = navigationService;
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
        for (NpcInstance instance : instanceRegistry.findAll()) {
            if (states.containsKey(instance.getId())) {
                instanceRegistry.stopNavigating(instance);
            }
        }
        states.clear();
    }

    public boolean isEngaged(NpcInstance instance) {
        return states.containsKey(instance.getId());
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
        AggressionLevel aggression = definition.getCombatProfile().aggressionLevel();
        if (aggression == AggressionLevel.FLEE) {
            engage(instance, definition, CombatMode.FLEE, attacker);
        } else if (aggression == AggressionLevel.FIGHT_BACK || aggression == AggressionLevel.FIGHTS_ON_SIGHT) {
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
        event.setDroppedExp(0);
        states.remove(instance.getId());
        Bukkit.getScheduler().runTask(plugin, () -> instanceRegistry.deleteInstance(instance.getId()));
    }

    private void tick() {
        currentTick++;
        for (NpcInstance instance : instanceRegistry.findAll()) {
            NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
            LivingEntity npc = instanceRegistry.findEntity(instance).orElse(null);
            if (definition == null || npc == null || !npc.isValid() || npc.isDead()) {
                states.remove(instance.getId());
                continue;
            }
            CombatProfile profile = definition.getCombatProfile();
            if (profile.invulnerable() || profile.aggressionLevel() == AggressionLevel.NONE) {
                clearState(instance);
                continue;
            }

            CombatState state = states.get(instance.getId());
            if (state != null && (profile.aggressionLevel() == AggressionLevel.FLEE) != (state.mode == CombatMode.FLEE)) {
                clearState(instance);
                state = null;
            }
            if (state == null && profile.aggressionLevel() == AggressionLevel.FIGHTS_ON_SIGHT
                && currentTick % 10 == Math.floorMod(instance.getId().hashCode(), 10)) {
                LivingEntity target = findNearestTarget(instance, npc);
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
        states.keySet().removeIf(id -> instanceRegistry.findAll().stream().noneMatch(instance -> instance.getId().equals(id)));
    }

    private void flee(NpcInstance instance, LivingEntity npc, CombatState state) {
        Entity threat = Bukkit.getEntity(state.entityId);
        Location threatLocation = threat != null && threat.isValid() ? threat.getLocation() : state.lastKnownLocation;
        if (currentTick >= state.expiresAt || threatLocation == null
            || threatLocation.getWorld() != npc.getWorld()) {
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
        state.lastKnownLocation = target.getLocation();
        if (npc.getLocation().distanceSquared(target.getLocation()) > ATTACK_RANGE_SQUARED) {
            if (state.navigationTarget == null || currentTick >= state.nextRepathAt) {
                state.navigationTarget = target.getLocation();
                state.nextRepathAt = currentTick + 10;
            }
            instanceRegistry.navigate(instance, state.navigationTarget, WalkingSpeed.FAST);
            return;
        }

        instanceRegistry.stopNavigating(instance);
        state.navigationTarget = null;
        face(npc, target);
        if (currentTick >= state.nextAttackAt && npc.hasLineOfSight(target)) {
            npc.swingMainHand();
            target.damage(NpcMeleeAttack.damage(npc.getEquipment().getItemInMainHand()), npc);
            state.nextAttackAt = currentTick + ATTACK_COOLDOWN_TICKS;
        }
    }

    private void engage(
        NpcInstance instance,
        NpcDefinition definition,
        CombatMode mode,
        LivingEntity entity
    ) {
        CombatState previous = states.get(instance.getId());
        boolean enteringCombat = previous == null || previous.mode != mode || !previous.entityId.equals(entity.getUniqueId());
        states.put(instance.getId(), new CombatState(
            mode,
            entity.getUniqueId(),
            entity.getLocation(),
            currentTick + (mode == CombatMode.FLEE ? FLEE_TICKS : Long.MAX_VALUE),
            currentTick
        ));
        if (enteringCombat) {
            shout(instance, definition);
        }
    }

    private LivingEntity findNearestTarget(NpcInstance instance, LivingEntity npc) {
        return npc.getNearbyEntities(SIGHT_RANGE, SIGHT_RANGE, SIGHT_RANGE).stream()
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(target -> isAttackable(instance, target))
            .filter(npc::hasLineOfSight)
            .min(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(npc.getLocation())))
            .orElse(null);
    }

    private boolean isAttackable(NpcInstance attacker, LivingEntity target) {
        if (!target.isValid() || target.isDead() || target.getEntityId() == attacker.getEntityId()
            || navigationService.isNavigator(target)) {
            return false;
        }
        if (target instanceof Player player) {
            return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
        }
        if (target instanceof Mannequin) {
            NpcInstance targetInstance = instanceRegistry.findByEntityId(target.getEntityId()).orElse(null);
            if (targetInstance == null) {
                return false;
            }
            NpcDefinition targetDefinition = definitionRepository.find(targetInstance.getDefinitionKey()).orElse(null);
            return targetDefinition != null && !targetDefinition.getCombatProfile().invulnerable();
        }
        return target instanceof Mob;
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

    private void shout(NpcInstance instance, NpcDefinition definition) {
        String shoutout = definition.getCombatProfile().shoutout();
        if (shoutout == null) {
            return;
        }
        Location location = instance.getLocation();
        Component message = Component.text(definition.getDisplayName() + ": " + shoutout);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == location.getWorld()
                && player.getLocation().distanceSquared(location) <= SHOUT_RANGE_SQUARED) {
                player.sendMessage(message);
            }
        }
    }

    private void face(LivingEntity npc, LivingEntity target) {
        Vector direction = target.getEyeLocation().toVector().subtract(npc.getEyeLocation().toVector());
        Location location = npc.getLocation();
        location.setDirection(direction);
        npc.setRotation(location.getYaw(), location.getPitch());
        npc.setBodyYaw(location.getYaw());
    }

    private void clearState(NpcInstance instance) {
        if (states.remove(instance.getId()) != null) {
            instanceRegistry.stopNavigating(instance);
        }
    }

    private enum CombatMode {
        FLEE,
        FIGHT
    }

    private static final class CombatState {
        private final CombatMode mode;
        private final UUID entityId;
        private Location lastKnownLocation;
        private final long expiresAt;
        private long nextAttackAt;
        private Location navigationTarget;
        private long nextRepathAt;

        private CombatState(
            CombatMode mode,
            UUID entityId,
            Location lastKnownLocation,
            long expiresAt,
            long nextAttackAt
        ) {
            this.mode = mode;
            this.entityId = entityId;
            this.lastKnownLocation = lastKnownLocation.clone();
            this.expiresAt = expiresAt;
            this.nextAttackAt = nextAttackAt;
        }
    }
}
