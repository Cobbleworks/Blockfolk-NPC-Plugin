package dev.blockfolk.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import dev.blockfolk.model.SpecialAttack;
import dev.blockfolk.model.SpecialAttackOptions;

/** Runs on the combat tick; holds no tasks, summons, or world block changes. */
public final class NpcSpecialAttackService {
    private static final int CAST_TICKS = 20;
    private static final double IMPACT_RADIUS = 2.0;
    private final Map<UUID, SpecialAttackScheduler> schedulers = new HashMap<>();
    private final Map<UUID, Cast> casts = new HashMap<>();

    /** Returns true while casting, so weapon attacks and navigation pause. */
    public boolean tick(UUID instanceId, LivingEntity npc, LivingEntity target, SpecialAttackOptions options,
            Predicate<LivingEntity> canHit, long tick) {
        Cast cast = casts.get(instanceId);
        if (cast != null && (!options.enabled().contains(cast.attack()) || !cast.targetId().equals(target.getUniqueId())
                || cast.center().getWorld() != npc.getWorld())) {
            casts.remove(instanceId);
            cast = null;
        }
        if (cast != null) {
            if (tick < cast.impactAt()) {
                if (tick % 4 == 0)
                    warning(cast);
            } else {
                casts.remove(instanceId);
                if (canHit.test(target) && npc.hasLineOfSight(target)
                        && npc.getLocation().distanceSquared(target.getLocation()) <= cast.attack().rangeSquared()) {
                    execute(npc, target, cast, canHit);
                }
            }
            return true;
        }
        if (options.enabled().isEmpty() || !npc.hasLineOfSight(target))
            return false;
        SpecialAttackScheduler scheduler = schedulers.computeIfAbsent(instanceId,
                ignored -> new SpecialAttackScheduler(tick, options));
        SpecialAttack attack = scheduler.select(tick, options, npc.getLocation().distanceSquared(target.getLocation()),
                ThreadLocalRandom.current());
        if (attack == null)
            return false;
        Location center = (attack == SpecialAttack.SHOCKWAVE ? npc.getLocation() : target.getLocation()).clone();
        cast = new Cast(attack, target.getUniqueId(), center, tick + CAST_TICKS);
        casts.put(instanceId, cast);
        warning(cast);
        npc.getWorld().playSound(npc.getLocation(), "minecraft:entity.evoker.prepare_attack", 0.7f, 1.3f);
        return true;
    }

    public void cancelCast(UUID instanceId) {
        casts.remove(instanceId);
    }

    public void retainInstances(Set<UUID> active) {
        casts.keySet().retainAll(active);
        schedulers.keySet().retainAll(active);
    }

    public void clear() {
        casts.clear();
        schedulers.clear();
    }

    private void warning(Cast cast) {
        Location center = cast.center();
        double radius = cast.attack() == SpecialAttack.SHOCKWAVE ? 4.0 : IMPACT_RADIUS;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            center.getWorld().spawnParticle(particle(cast.attack()),
                    center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius), 1, 0, 0, 0, 0);
        }
    }

    private void execute(LivingEntity npc, LivingEntity target, Cast cast, Predicate<LivingEntity> canHit) {
        Location center = cast.center();
        if (cast.attack() == SpecialAttack.SHOCKWAVE) {
            center.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 0.3, 0), 40, 2, 0.2, 2, 0.05);
            center.getWorld().playSound(center, "minecraft:entity.generic.explode", 0.6f, 1.4f);
            for (LivingEntity victim : center.getNearbyLivingEntities(4)) {
                if (victim.equals(npc) || victim.getLocation().distanceSquared(center) > 16 || !canHit.test(victim)
                        || !npc.hasLineOfSight(victim))
                    continue;
                if (damage(npc, victim, 4) <= 0)
                    continue;
                Vector away = victim.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (away.lengthSquared() < 0.01)
                    away = new Vector(1, 0, 0);
                victim.setVelocity(away.normalize().multiply(0.8).setY(0.35));
            }
            return;
        }
        // Attacks land at the warned spot. Moving out of the ring dodges them.
        if (target.getLocation().distanceSquared(center) > IMPACT_RADIUS * IMPACT_RADIUS)
            return;
        npc.swingMainHand();
        center.getWorld().spawnParticle(particle(cast.attack()), target.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4,
                0.02);
        switch (cast.attack()) {
            case LIFE_DRAIN -> {
                double drained = damage(npc, target, 4);
                var maxHealth = npc.getAttribute(Attribute.MAX_HEALTH);
                if (drained > 0 && maxHealth != null && npc.isValid() && !npc.isDead()) {
                    npc.setHealth(Math.min(maxHealth.getValue(), npc.getHealth() + drained));
                    npc.getWorld().spawnParticle(Particle.HEART, npc.getEyeLocation(), 4, 0.3, 0.3, 0.3, 0);
                }
            }
            case FREEZING_SPELL -> {
                if (damage(npc, target, 2) > 0)
                    effect(target, PotionEffectType.SLOWNESS, 60, 3);
            }
            case POISON_SPIT -> {
                if (damage(npc, target, 2) > 0)
                    effect(target, PotionEffectType.POISON, 80, 0);
            }
            case WITHER_CURSE -> {
                if (damage(npc, target, 2) > 0)
                    effect(target, PotionEffectType.WITHER, 60, 0);
            }
            case FLAME_BURST -> {
                if (damage(npc, target, 4) > 0)
                    target.setFireTicks(Math.max(target.getFireTicks(), 60));
            }
            case LIGHTNING_MARK -> {
                // Visual lightning only: damage is attributed to the NPC and cancellable.
                center.getWorld().strikeLightningEffect(center);
                damage(npc, target, 6);
            }
            case FEAR -> {
                if (damage(npc, target, 1) > 0) {
                    effect(target, PotionEffectType.BLINDNESS, 60, 0);
                    effect(target, PotionEffectType.WEAKNESS, 60, 0);
                }
            }
            case SHOCKWAVE -> {
            }
        }
        center.getWorld().playSound(center, "minecraft:entity.evoker.cast_spell", 0.7f, 1.1f);
    }

    private double damage(LivingEntity npc, LivingEntity target, double amount) {
        double before = target.getHealth();
        double absorption = target.getAbsorptionAmount();
        target.damage(amount, npc);
        // Suppress secondary effects when damage was cancelled or fully resisted.
        return Math.max(0, before - target.getHealth()) + Math.max(0, absorption - target.getAbsorptionAmount());
    }

    private void effect(LivingEntity target, PotionEffectType type, int ticks, int amplifier) {
        if (target.isValid() && !target.isDead())
            target.addPotionEffect(new PotionEffect(type, ticks, amplifier));
    }

    private Particle particle(SpecialAttack attack) {
        return switch (attack) {
            case LIFE_DRAIN -> Particle.DAMAGE_INDICATOR;
            case FREEZING_SPELL -> Particle.SNOWFLAKE;
            case POISON_SPIT -> Particle.WITCH;
            case WITHER_CURSE, FEAR -> Particle.SOUL;
            case FLAME_BURST -> Particle.FLAME;
            case LIGHTNING_MARK -> Particle.ELECTRIC_SPARK;
            case SHOCKWAVE -> Particle.CLOUD;
        };
    }

    private record Cast(SpecialAttack attack, UUID targetId, Location center, long impactAt) {
    }
}
