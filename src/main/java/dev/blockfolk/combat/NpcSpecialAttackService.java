package dev.blockfolk.combat;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import dev.blockfolk.fighters.AttackGeometry;
import dev.blockfolk.fighters.FighterAttack;
import dev.blockfolk.fighters.FighterAttack.*;
import dev.blockfolk.fighters.FighterTemplates;
import dev.blockfolk.model.SpecialAttackOptions;

/**
 * Executes shared Fighter definitions on the combat tick, with per-instance
 * casting state.
 */
public final class NpcSpecialAttackService {
    private final Map<UUID, SpecialAttackScheduler> schedulers = new HashMap<>();
    private final Map<UUID, Cast> casts = new HashMap<>();
    private final Supplier<List<FighterAttack>> definitions;
    private final BiPredicate<UUID, Location> teleport;
    private final Consumer<LivingEntity> beforeKnockback;

    public NpcSpecialAttackService() {
        this(FighterTemplates::defaults, (id, location) -> false, victim -> {
        });
    }
    public NpcSpecialAttackService(Supplier<List<FighterAttack>> definitions, BiPredicate<UUID, Location> teleport,
            Consumer<LivingEntity> beforeKnockback) {
        this.definitions = definitions;
        this.teleport = teleport;
        this.beforeKnockback = beforeKnockback;
    }

    /** True while casting or firing, to pause weapon attacks and navigation. */
    public boolean tick(UUID instanceId, LivingEntity npc, LivingEntity target, SpecialAttackOptions options,
            Predicate<LivingEntity> canHit, long tick) {
        List<FighterAttack> library = definitions.get();
        Cast cast = casts.get(instanceId);
        if (cast != null && (!options.assignedAttackKeys().contains(cast.attack().key())
                || !cast.targetId().equals(target.getUniqueId()) || cast.center().getWorld() != npc.getWorld()
                || !library.contains(cast.attack()))) {
            casts.remove(instanceId);
            cast = null;
        }
        if (cast != null) {
            if (tick < cast.impactAt()) {
                if (tick % 4 == 0)
                    visualize(cast.attack(), source(npc, cast), cast.direction(), false);
            } else {
                casts.remove(instanceId);
                if (canHit.test(target) && npc.hasLineOfSight(target))
                    execute(instanceId, npc, target, cast, canHit);
            }
            return true;
        }
        if (options.assignedAttackKeys().isEmpty() || !npc.hasLineOfSight(target))
            return false;
        SpecialAttackScheduler scheduler = schedulers.computeIfAbsent(instanceId,
                ignored -> new SpecialAttackScheduler(tick, options));
        FighterAttack attack = scheduler.select(tick, options, library,
                npc.getLocation().distanceSquared(target.getLocation()), ThreadLocalRandom.current());
        if (attack == null)
            return false;
        Vector direction = target.getEyeLocation().toVector().subtract(npc.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01)
            direction = npc.getLocation().getDirection();
        direction.normalize();
        Location center = attack.origin() == Origin.TARGET ? target.getLocation().clone() : origin(npc, attack);
        cast = new Cast(attack, target.getUniqueId(), center, direction, tick + attack.delayTicks());
        if (attack.delayTicks() == 0)
            execute(instanceId, npc, target, cast, canHit);
        else {
            casts.put(instanceId, cast);
            visualize(attack, center, direction, false);
            npc.getWorld().playSound(npc.getLocation(), "minecraft:entity.evoker.prepare_attack", 0.7f, 1.3f);
        }
        return true;
    }

    public void cancelCast(UUID id) {
        casts.remove(id);
    }
    public void retainInstances(Set<UUID> active) {
        casts.keySet().retainAll(active);
        schedulers.keySet().retainAll(active);
    }
    public void clear() {
        casts.clear();
        schedulers.clear();
    }

    private static Location origin(LivingEntity npc, FighterAttack attack) {
        return attack.shape() == Shape.CONE || attack.shape() == Shape.BEAM ? npc.getEyeLocation() : npc.getLocation();
    }
    private Location source(LivingEntity npc, Cast cast) {
        return cast.attack().origin() == Origin.NPC ? origin(npc, cast.attack()) : cast.center();
    }
    private void execute(UUID instanceId, LivingEntity npc, LivingEntity target, Cast cast,
            Predicate<LivingEntity> canHit) {
        FighterAttack attack = cast.attack();
        Location center = source(npc, cast);
        if (attack.shape() == Shape.TELEPORT) {
            blink(instanceId, npc, attack);
            return;
        }
        double range = visibleRange(attack, center, cast.direction());
        visualize(attack, center, cast.direction(), true);
        npc.swingMainHand();
        if (attack.damage() == 0 && attack.effects().isEmpty())
            return;
        Set<LivingEntity> victims = new LinkedHashSet<>(center.getNearbyLivingEntities(
                attack.shape() == Shape.SPHERE ? attack.size() : attack.reach() + attack.size()));
        victims.add(target);
        double drained = 0;
        for (LivingEntity victim : victims) {
            if (!npc.isValid() || npc.isDead())
                break;
            if (victim.equals(npc) || !canHit.test(victim) || !npc.hasLineOfSight(victim))
                continue;
            Location hitPoint = attack.shape() == Shape.SPHERE ? victim.getLocation() : victim.getEyeLocation();
            if (!AttackGeometry.contains(attack, hitPoint.toVector().subtract(center.toVector()), cast.direction(),
                    range))
                continue;
            Hit hit = hit(npc, victim, attack.damage());
            if (!hit.allowed())
                continue;
            if (attack.effects().contains(Effect.LIFE_DRAIN))
                drained += hit.damage();
            if (!victim.isValid() || victim.isDead())
                continue;
            for (Effect effect : attack.effects()) {
                switch (effect) {
                    case FIRE -> victim.setFireTicks(Math.max(victim.getFireTicks(), attack.effectSeconds() * 20));
                    case POISON -> potion(victim, PotionEffectType.POISON, attack);
                    case SLOWNESS -> potion(victim, PotionEffectType.SLOWNESS, attack);
                    case WITHER -> potion(victim, PotionEffectType.WITHER, attack);
                    case BLINDNESS -> potion(victim, PotionEffectType.BLINDNESS, attack);
                    case WEAKNESS -> potion(victim, PotionEffectType.WEAKNESS, attack);
                    case KNOCKBACK -> {
                        if (attack.knockback() <= 0)
                            break;
                        beforeKnockback.accept(victim);
                        Vector away = victim.getLocation().toVector().subtract(npc.getLocation().toVector()).setY(0);
                        if (away.lengthSquared() < 0.01)
                            away = cast.direction().clone().setY(0);
                        if (away.lengthSquared() < 0.01)
                            away = new Vector(1, 0, 0);
                        victim.setVelocity(away.normalize().multiply(attack.knockback())
                                .setY(Math.min(0.5, attack.knockback() * 0.45)));
                    }
                    case LIFE_DRAIN -> {
                    }
                }
            }
        }
        if (drained > 0 && npc.isValid() && !npc.isDead()) {
            var maxHealth = npc.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null)
                npc.setHealth(Math.min(maxHealth.getValue(), npc.getHealth() + drained));
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private Hit hit(LivingEntity npc, LivingEntity target, double amount) {
        if (amount == 0) {
            // A zero-damage protection event also lets region plugins reject pure debuffs.
            DamageSource source = DamageSource.builder(DamageType.MAGIC).withCausingEntity(npc).withDirectEntity(npc)
                    .build();
            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(npc, target, DamageCause.MAGIC, source, 0);
            Bukkit.getPluginManager().callEvent(event);
            return new Hit(!event.isCancelled(), 0);
        }
        double before = target.getHealth() + target.getAbsorptionAmount();
        target.damage(amount, npc);
        double dealt = Math.max(0, before - target.getHealth() - target.getAbsorptionAmount());
        return new Hit(dealt > 0, dealt);
    }
    private void potion(LivingEntity victim, PotionEffectType type, FighterAttack attack) {
        victim.addPotionEffect(new PotionEffect(type, attack.effectSeconds() * 20, attack.effectLevel() - 1));
    }

    private void blink(UUID id, LivingEntity npc, FighterAttack attack) {
        Location from = npc.getLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double distance = Math.max(1, attack.size()) * ThreadLocalRandom.current().nextDouble(0.6, 1);
            int x = (int) Math.floor(from.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(from.getZ() + Math.sin(angle) * distance);
            if (!from.getWorld().isChunkLoaded(x >> 4, z >> 4))
                continue;
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                Location to = new Location(from.getWorld(), x + 0.5, from.getBlockY() + dy, z + 0.5, from.getYaw(),
                        from.getPitch());
                if (to.distanceSquared(from) > attack.size() * attack.size() || !safeBlinkDestination(to))
                    continue;
                if (teleport.test(id, to)) {
                    from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 36, 0.4, 0.7, 0.4, 0.1);
                    to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 36, 0.4, 0.7, 0.4, 0.1);
                    to.getWorld().playSound(to, "minecraft:entity.enderman.teleport", 0.8f, 1.2f);
                    return;
                }
            }
        }
    }
    public static boolean safeBlinkDestination(Location to) {
        var world = to.getWorld();
        if (world == null || to.getY() < world.getMinHeight() + 1 || to.getY() + 2 >= world.getMaxHeight()
                || !world.isChunkLoaded(to.getBlockX() >> 4, to.getBlockZ() >> 4)
                || !world.getWorldBorder().isInside(to))
            return false;
        var feet = to.getBlock();
        var head = to.clone().add(0, 1, 0).getBlock();
        var floor = to.clone().subtract(0, 1, 0).getBlock();
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid() && floor.isSolid()
                && !hazard(feet.getType()) && !hazard(head.getType()) && !hazard(floor.getType());
    }
    private static boolean hazard(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, SWEET_BERRY_BUSH, POWDER_SNOW ->
                true;
            default -> false;
        };
    }
    private static double visibleRange(FighterAttack attack, Location center, Vector direction) {
        if (attack.shape() != Shape.BEAM)
            return attack.reach();
        var hit = center.getWorld().rayTraceBlocks(center, direction, attack.range(), FluidCollisionMode.NEVER, true);
        return hit == null
                ? attack.range()
                : Math.min(attack.range(), hit.getHitPosition().distance(center.toVector()));
    }

    /**
     * Preview from the administrator's position and view direction; particles and
     * sound only.
     */
    public static void preview(LivingEntity player, FighterAttack attack) {
        Vector direction = player.getEyeLocation().getDirection();
        Location center = origin(player, attack);
        if (attack.origin() == Origin.TARGET)
            center.add(direction.clone().multiply(Math.min(6, attack.range())));
        visualize(attack, center, direction, true);
    }
    private static void visualize(FighterAttack attack, Location center, Vector direction, boolean impact) {
        Particle particle = !impact && attack.visual() == Visual.SONIC
                ? Particle.ELECTRIC_SPARK
                : particle(attack.visual());
        if (attack.shape() == Shape.SPHERE || attack.shape() == Shape.TELEPORT) {
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12;
                center.getWorld().spawnParticle(particle,
                        center.clone().add(Math.cos(angle) * attack.size(), 0.15, Math.sin(angle) * attack.size()), 1,
                        0, 0, 0, 0);
            }
            if (impact && attack.visual() == Visual.LIGHTNING)
                center.getWorld().strikeLightningEffect(center);
        } else {
            double range = visibleRange(attack, center, direction);
            if (attack.shape() == Shape.BEAM) {
                for (double step = 0.5; step <= range; step += 0.5)
                    center.getWorld().spawnParticle(particle, center.clone().add(direction.clone().multiply(step)), 1,
                            0, 0, 0, 0);
            } else {
                Vector right = new Vector(-direction.getZ(), 0, direction.getX());
                if (right.lengthSquared() < 0.01)
                    right = new Vector(1, 0, 0);
                right.normalize();
                Vector up = direction.clone().crossProduct(right).normalize();
                double spread = Math.tan(Math.toRadians(attack.angle() / 2));
                for (int ray = 0; ray < 12; ray++) {
                    double angle = ray * Math.PI / 6;
                    for (double step = 1; step <= range; step += 2) {
                        Vector offset = direction.clone().add(right.clone().multiply(Math.cos(angle) * spread))
                                .add(up.clone().multiply(Math.sin(angle) * spread)).normalize().multiply(step);
                        center.getWorld().spawnParticle(particle, center.clone().add(offset), impact ? 3 : 1, 0.05,
                                0.05, 0.05, 0.01);
                    }
                }
            }
        }
        if (impact)
            center.getWorld().playSound(center,
                    attack.visual() == Visual.SONIC
                            ? "minecraft:entity.warden.sonic_boom"
                            : "minecraft:entity.evoker.cast_spell",
                    0.7f, 1.1f);
    }
    private static Particle particle(Visual visual) {
        return switch (visual) {
            case FLAME -> Particle.FLAME;
            case SONIC -> Particle.SONIC_BOOM;
            case SOUL -> Particle.SOUL;
            case ICE -> Particle.SNOWFLAKE;
            case POISON -> Particle.WITCH;
            case CLOUD -> Particle.CLOUD;
            case BLOOD -> Particle.DAMAGE_INDICATOR;
            case LIGHTNING -> Particle.ELECTRIC_SPARK;
            case ENDER -> Particle.PORTAL;
        };
    }
    private record Hit(boolean allowed, double damage) {
    }
    private record Cast(FighterAttack attack, UUID targetId, Location center, Vector direction, long impactAt) {
    }
}
