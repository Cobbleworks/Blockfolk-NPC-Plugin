package dev.blockfolk.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
import dev.blockfolk.fighters.AbilityVisuals;
import dev.blockfolk.fighters.FighterAttack;
import dev.blockfolk.fighters.FighterAttack.*;
import dev.blockfolk.fighters.FighterTemplates;
import dev.blockfolk.model.SpecialAttackOptions;

/**
 * Executes shared ability definitions on the combat tick, with per-instance
 * casting state.
 */
public final class NpcSpecialAttackService {
    private static final int CHARGE_TIMEOUT_TICKS = 30 * 20;
    /** Lingering areas per NPC; the oldest is dropped beyond this. */
    private static final int MAX_ZONES = 4;
    /** Dashes stop this far in front of their target. */
    private static final double DASH_STOP_DISTANCE = 1.5;

    private final Map<UUID, SpecialAttackScheduler> schedulers = new HashMap<>();
    private final Map<UUID, Cast> casts = new HashMap<>();
    private final Map<UUID, List<Zone>> zones = new HashMap<>();
    private final Supplier<List<FighterAttack>> definitions;
    private final BiPredicate<UUID, Location> teleport;
    private final Consumer<LivingEntity> beforeKnockback;
    private int executionDepth;

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

    /**
     * True while a delayed cast or impact pauses weapon combat. Charges and
     * lingering pulses never pause it.
     */
    public boolean tick(UUID instanceId, LivingEntity npc, LivingEntity target, SpecialAttackOptions options,
            Predicate<LivingEntity> canHit, long tick) {
        List<FighterAttack> library = definitions.get();
        tickZones(instanceId, npc, canHit, tick, library);
        Cast cast = casts.get(instanceId);
        if (cast == null && target == null)
            return false;
        LivingEntity castTarget = cast != null && cast.scripted() ? cast.target() : target;
        if (cast != null && (!library.contains(cast.attack()) || cast.center().getWorld() != npc.getWorld()
                || (!cast.scripted() && (!options.assignedAttackKeys().contains(cast.attack().key()) || target == null
                        || cast.target() == null || !cast.target().getUniqueId().equals(target.getUniqueId())))
                || (cast.attack().castMode() != CastMode.NEXT_ATTACK && castTarget != null && (!castTarget.isValid()
                        || castTarget.isDead() || castTarget.getWorld() != npc.getWorld())))) {
            casts.remove(instanceId);
            cast = null;
        }
        if (cast != null) {
            if (cast.attack().castMode() == CastMode.NEXT_ATTACK) {
                if (tick >= cast.impactAt())
                    casts.remove(instanceId);
                else if (tick % 4 == 0)
                    casterParticles(npc, cast.attack(), tick, true);
                return false;
            }
            if (tick < cast.impactAt()) {
                if (tick % 4 == 0) {
                    visualize(cast.attack(), source(npc, cast), cast.direction(), false);
                    casterParticles(npc, cast.attack(), tick, false);
                }
            } else {
                casts.remove(instanceId);
                if (castTarget == null || (canHit.test(castTarget) && npc.hasLineOfSight(castTarget)))
                    execute(instanceId, npc, castTarget, cast, canHit, tick);
            }
            return true;
        }
        if (target == null || options.assignedAttackKeys().isEmpty() || !npc.hasLineOfSight(target))
            return false;
        SpecialAttackScheduler scheduler = schedulers.computeIfAbsent(instanceId,
                ignored -> new SpecialAttackScheduler(tick, options));
        CastContext context = new CastContext(npc.getLocation().distanceSquared(target.getLocation()),
                () -> healthFraction(npc), () -> healthFraction(target));
        FighterAttack attack = scheduler.select(tick, options, library, context, ThreadLocalRandom.current());
        if (attack == null)
            return false;
        begin(instanceId, npc, target, attack, canHit, tick, false);
        return attack.castMode() != CastMode.NEXT_ATTACK;
    }

    /**
     * Explicit casts do not require random assignment, active combat, minimum
     * range, or trigger conditions. Returns delay, or -1 if unavailable.
     */
    public int useAbility(UUID instanceId, LivingEntity npc, LivingEntity target, String key,
            SpecialAttackOptions options, Predicate<LivingEntity> canHit, long tick) {
        FighterAttack attack = definitions.get().stream().filter(a -> a.key().equals(key)).findFirst().orElse(null);
        if (attack == null || casts.containsKey(instanceId))
            return -1;
        boolean needsTarget = attack.castMode() != CastMode.NEXT_ATTACK
                && (attack.origin() == Origin.TARGET || attack.shape().aimed());
        if (target != null
                && (!canHit.test(target) || target.getWorld() != npc.getWorld() || !npc.hasLineOfSight(target)
                        || npc.getLocation().distanceSquared(target.getLocation()) > attack.range() * attack.range()))
            target = null;
        if (needsTarget && target == null)
            return -1;
        SpecialAttackScheduler scheduler = schedulers.computeIfAbsent(instanceId,
                ignored -> new SpecialAttackScheduler(tick, options));
        if (!scheduler.isReady(key, tick))
            return -1;
        scheduler.markUsed(attack, tick);
        begin(instanceId, npc, target, attack, canHit, tick, true);
        return attack.delayTicks();
    }

    private void begin(UUID id, LivingEntity npc, LivingEntity target, FighterAttack attack,
            Predicate<LivingEntity> canHit, long tick, boolean scripted) {
        Cast cast = createCast(npc, target, attack, tick, scripted);
        if (attack.castMode() == CastMode.INSTANT)
            execute(id, npc, target, cast, canHit, tick);
        else {
            casts.put(id, cast);
            boolean charged = attack.castMode() == CastMode.NEXT_ATTACK;
            casterParticles(npc, attack, tick, charged);
            if (!charged)
                visualize(attack, cast.center(), cast.direction(), false);
            npc.getWorld().playSound(npc.getLocation(), AbilityVisuals.CAST_SOUND, 0.7f, 1.3f);
        }
    }

    private static Cast createCast(LivingEntity npc, LivingEntity target, FighterAttack attack, long tick,
            boolean scripted) {
        Vector direction = target == null
                ? npc.getEyeLocation().getDirection()
                : target.getEyeLocation().toVector().subtract(npc.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01)
            direction = npc.getLocation().getDirection();
        direction.normalize();
        Location center = attack.origin() == Origin.TARGET && target != null
                ? target.getLocation().clone()
                : origin(npc, attack);
        return new Cast(attack, target, center, direction,
                tick + (attack.castMode() == CastMode.NEXT_ATTACK ? CHARGE_TIMEOUT_TICKS : attack.delayTicks()),
                scripted, UUID.randomUUID());
    }

    public LivingEntity scriptedTarget(UUID id) {
        Cast cast = casts.get(id);
        return cast != null && cast.scripted() ? cast.target() : null;
    }

    public UUID chargeId(UUID id) {
        Cast cast = casts.get(id);
        return cast != null && cast.attack().castMode() == CastMode.NEXT_ATTACK ? cast.id() : null;
    }

    public boolean isExecutingAbility() {
        return executionDepth > 0;
    }

    /**
     * Called only after a non-cancelled weapon hit actually removed health or
     * absorption.
     */
    public boolean onSuccessfulWeaponHit(UUID id, UUID chargeId, LivingEntity npc, LivingEntity victim,
            SpecialAttackOptions options, Predicate<LivingEntity> canHit, long tick) {
        Cast cast = casts.get(id);
        if (cast == null || !cast.id().equals(chargeId) || cast.attack().castMode() != CastMode.NEXT_ATTACK
                || tick >= cast.impactAt() || !definitions.get().contains(cast.attack())
                || !cast.scripted() && !options.assignedAttackKeys().contains(cast.attack().key()) || !npc.isValid()
                || npc.isDead() || victim.getWorld() != npc.getWorld() || !canHit.test(victim))
            return false;
        casts.remove(id); // Consume before damage events so the effect cannot recursively trigger itself.
        schedulers.get(id).markUsed(cast.attack(), tick);
        execute(id, npc, victim, createCast(npc, victim, cast.attack(), tick, cast.scripted()), canHit, tick);
        return true;
    }

    private static void casterParticles(LivingEntity npc, FighterAttack attack, long tick, boolean charged) {
        Particle particle = AbilityVisuals.warningParticle(attack.visual());
        Location center = npc.getLocation().add(0, 1, 0);
        for (int i = 0; i < 6; i++) {
            double angle = tick * 0.16 + i * Math.PI / 3;
            npc.getWorld().spawnParticle(particle,
                    center.clone().add(Math.cos(angle) * 0.6, Math.sin(angle * 2) * 0.35, Math.sin(angle) * 0.6), 1, 0,
                    0, 0, 0);
        }
        if (charged)
            npc.getWorld().spawnParticle(Particle.ENCHANT, npc.getEyeLocation(), 4, 0.25, 0.25, 0.25, 0);
    }

    public boolean isCasting(UUID id) {
        Cast cast = casts.get(id);
        return cast != null && cast.attack().castMode() == CastMode.DELAYED;
    }
    public void cancelAutomaticCast(UUID id) {
        Cast cast = casts.get(id);
        if (cast != null && !cast.scripted())
            casts.remove(id);
    }
    /** Cancels the pending cast and any lingering areas of this NPC. */
    public void cancelCast(UUID id) {
        casts.remove(id);
        zones.remove(id);
    }
    public void retainInstances(Set<UUID> active) {
        casts.keySet().retainAll(active);
        schedulers.keySet().retainAll(active);
        zones.keySet().retainAll(active);
    }
    public void clear() {
        casts.clear();
        schedulers.clear();
        zones.clear();
    }

    private static Location origin(LivingEntity npc, FighterAttack attack) {
        return switch (attack.shape()) {
            case CONE, BEAM, CHAIN -> npc.getEyeLocation();
            default -> npc.getLocation();
        };
    }
    private static Location source(LivingEntity npc, Cast cast) {
        return cast.attack().origin() == Origin.NPC ? origin(npc, cast.attack()) : cast.center();
    }
    private static double healthFraction(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? 0 : attribute.getValue();
        return max <= 0 ? 1 : entity.getHealth() / max;
    }

    private void execute(UUID instanceId, LivingEntity npc, LivingEntity target, Cast cast,
            Predicate<LivingEntity> canHit, long tick) {
        executionDepth++;
        try {
            executeImpact(instanceId, npc, target, cast, canHit, false);
        } finally {
            executionDepth--;
        }
        FighterAttack attack = cast.attack();
        if (attack.pulses() > 1 && npc.isValid() && !npc.isDead()) {
            List<Zone> active = zones.computeIfAbsent(instanceId, ignored -> new ArrayList<>());
            if (active.size() >= MAX_ZONES)
                active.removeFirst();
            active.add(new Zone(cast, attack.pulses() - 1, tick + attack.pulseIntervalTicks()));
        }
    }

    private void tickZones(UUID instanceId, LivingEntity npc, Predicate<LivingEntity> canHit, long tick,
            List<FighterAttack> library) {
        List<Zone> active = zones.get(instanceId);
        if (active == null)
            return;
        List<Zone> due = new ArrayList<>();
        for (Iterator<Zone> iterator = active.iterator(); iterator.hasNext();) {
            Zone zone = iterator.next();
            if (!library.contains(zone.cast().attack()) || zone.cast().center().getWorld() != npc.getWorld()) {
                iterator.remove();
            } else if (tick >= zone.nextAt()) {
                iterator.remove();
                due.add(zone);
            } else if (tick % 4 == 0)
                visualize(zone.cast().attack(), source(npc, zone.cast()), zone.cast().direction(), false);
        }
        for (Zone zone : due) {
            executionDepth++;
            try {
                // Pulses never use the charge's hurt-immunity bypass, so no target is passed.
                executeImpact(instanceId, npc, null, zone.cast(), canHit, true);
            } finally {
                executionDepth--;
            }
            if (zone.remaining() > 1)
                active.add(new Zone(zone.cast(), zone.remaining() - 1,
                        tick + zone.cast().attack().pulseIntervalTicks()));
        }
        if (active.isEmpty())
            zones.remove(instanceId);
    }

    private void executeImpact(UUID instanceId, LivingEntity npc, LivingEntity target, Cast cast,
            Predicate<LivingEntity> canHit, boolean pulse) {
        FighterAttack attack = cast.attack();
        if (!npc.isValid() || npc.isDead())
            return;
        Location center = source(npc, cast);
        List<LivingEntity> victims = switch (attack.shape()) {
            case TELEPORT -> {
                blink(instanceId, npc, attack);
                yield List.of();
            }
            case SELF -> {
                visualize(attack, center, cast.direction(), true);
                yield List.of();
            }
            case CHAIN -> chainVictims(npc, target, attack, canHit);
            case DASH -> dash(instanceId, npc, target, cast, canHit);
            default -> {
                visualize(attack, center, cast.direction(), !pulse);
                yield areaVictims(npc, target, attack, center, cast.direction(), canHit);
            }
        };
        if (attack.shape().hitsVictims())
            npc.swingMainHand();
        if (!pulse)
            applyCasterBuffs(npc, attack);
        if (!attack.affectsVictims())
            return;
        double drained = 0;
        for (LivingEntity victim : victims) {
            if (!npc.isValid() || npc.isDead())
                break;
            Hit hit = attack.castMode() == CastMode.NEXT_ATTACK && victim.equals(target)
                    ? chargedHit(npc, victim, attack.damage())
                    : hit(npc, victim, attack.damage());
            if (!hit.allowed())
                continue;
            if (attack.effects().contains(Effect.LIFE_DRAIN))
                drained += hit.damage();
            if (victim.isValid() && !victim.isDead())
                applyVictimEffects(npc, victim, attack, center, cast.direction());
        }
        if (drained > 0 && npc.isValid() && !npc.isDead()) {
            var maxHealth = npc.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null)
                npc.setHealth(Math.min(maxHealth.getValue(), npc.getHealth() + drained));
        }
    }

    private static List<LivingEntity> areaVictims(LivingEntity npc, LivingEntity target, FighterAttack attack,
            Location center, Vector direction, Predicate<LivingEntity> canHit) {
        double range = visibleRange(attack, center, direction);
        boolean round = attack.shape() == Shape.SPHERE || attack.shape() == Shape.RING;
        Set<LivingEntity> candidates = new LinkedHashSet<>(
                center.getNearbyLivingEntities(round ? attack.size() : attack.reach() + attack.size()));
        if (target != null)
            candidates.add(target);
        List<LivingEntity> victims = new ArrayList<>();
        for (LivingEntity victim : candidates) {
            if (!eligible(npc, victim, canHit))
                continue;
            Location hitPoint = round ? victim.getLocation() : victim.getEyeLocation();
            if (AttackGeometry.contains(attack, hitPoint.toVector().subtract(center.toVector()), direction, range))
                victims.add(victim);
        }
        return victims;
    }

    private static boolean eligible(LivingEntity npc, LivingEntity victim, Predicate<LivingEntity> canHit) {
        return !victim.equals(npc) && canHit.test(victim) && npc.hasLineOfSight(victim);
    }

    /**
     * Strikes the target, then repeatedly jumps to the nearest eligible entity
     * within jump radius that the previous victim can see.
     */
    private static List<LivingEntity> chainVictims(LivingEntity npc, LivingEntity target, FighterAttack attack,
            Predicate<LivingEntity> canHit) {
        List<LivingEntity> chain = new ArrayList<>();
        if (target == null || !eligible(npc, target, canHit))
            return chain;
        chain.add(target);
        double jumpSquared = attack.size() * attack.size();
        while (chain.size() < attack.chainTargets()) {
            LivingEntity previous = chain.getLast();
            Location from = previous.getLocation();
            LivingEntity next = from.getNearbyLivingEntities(attack.size()).stream()
                    .filter(candidate -> !candidate.equals(npc) && !chain.contains(candidate))
                    .filter(candidate -> candidate.getWorld() == from.getWorld()
                            && candidate.getLocation().distanceSquared(from) <= jumpSquared)
                    .filter(candidate -> canHit.test(candidate) && previous.hasLineOfSight(candidate))
                    .min(Comparator.comparingDouble(candidate -> candidate.getLocation().distanceSquared(from)))
                    .orElse(null);
            if (next == null)
                break;
            chain.add(next);
        }
        Particle particle = AbilityVisuals.particle(attack.visual());
        Location from = npc.getEyeLocation();
        for (LivingEntity victim : chain) {
            Location to = victim.getEyeLocation();
            line(particle, from, to, 0.4);
            from = to;
        }
        npc.getWorld().playSound(npc.getLocation(), AbilityVisuals.impactSound(attack.visual()), 0.7f, 1.1f);
        return chain;
    }

    /**
     * Rushes towards the target, stopping short of it and of blocks. Victims along
     * the path are chosen before moving so the caster's sight is checked from where
     * the dash started. A dash without a safe landing spot fizzles.
     */
    private List<LivingEntity> dash(UUID id, LivingEntity npc, LivingEntity target, Cast cast,
            Predicate<LivingEntity> canHit) {
        FighterAttack attack = cast.attack();
        Location start = npc.getLocation();
        Vector flat = cast.direction().clone().setY(0);
        if (flat.lengthSquared() < 0.01)
            flat = start.getDirection().setY(0);
        if (flat.lengthSquared() < 0.01)
            flat = new Vector(1, 0, 0);
        flat.normalize();
        double distance = attack.range();
        if (target != null) {
            Vector offset = target.getLocation().toVector().subtract(start.toVector()).setY(0);
            distance = Math.min(distance, Math.max(0, offset.length() - DASH_STOP_DISTANCE));
        }
        var obstacle = start.getWorld().rayTraceBlocks(start.clone().add(0, 0.5, 0), flat, distance + 1,
                FluidCollisionMode.NEVER, true);
        if (obstacle != null)
            distance = Math.min(distance,
                    obstacle.getHitPosition().distance(start.toVector().add(new Vector(0, 0.5, 0))) - 0.8);
        Location landing = null;
        for (double step = distance; step >= 1 && landing == null; step -= 0.5) {
            for (int dy : new int[]{0, 1, -1}) {
                Location to = start.clone().add(flat.clone().multiply(step));
                if (dy != 0) // Step onto or down from a neighbouring block level.
                    to.setY(Math.floor(start.getY()) + dy);
                to.setDirection(flat);
                if (safeBlinkDestination(to)) {
                    landing = to;
                    break;
                }
            }
        }
        if (landing == null)
            return List.of();
        double travelled = landing.toVector().setY(0).distance(start.toVector().setY(0));
        Location eye = npc.getEyeLocation();
        Vector aim = flat;
        Set<LivingEntity> candidates = new LinkedHashSet<>(
                start.getNearbyLivingEntities(travelled + DASH_STOP_DISTANCE + attack.size()));
        if (target != null)
            candidates.add(target);
        List<LivingEntity> victims = candidates.stream().filter(victim -> eligible(npc, victim, canHit))
                .filter(victim -> AttackGeometry.contains(attack,
                        victim.getEyeLocation().toVector().subtract(eye.toVector()), aim,
                        travelled + DASH_STOP_DISTANCE))
                .toList();
        if (!teleport.test(id, landing))
            return List.of();
        Particle particle = AbilityVisuals.particle(attack.visual());
        line(particle, start.clone().add(0, 0.8, 0), landing.clone().add(0, 0.8, 0), 0.35);
        start.getWorld().playSound(landing, AbilityVisuals.impactSound(attack.visual()), 0.8f, 1.2f);
        return victims;
    }

    private void applyCasterBuffs(LivingEntity npc, FighterAttack attack) {
        for (Effect effect : attack.effects()) {
            if (effect.casterBuff())
                potion(npc, casterPotion(effect), attack);
        }
    }

    private void applyVictimEffects(LivingEntity npc, LivingEntity victim, FighterAttack attack, Location center,
            Vector direction) {
        for (Effect effect : attack.effects()) {
            if (effect.category() == EffectCategory.CASTER || effect.movement())
                continue;
            if (effect == Effect.FIRE)
                victim.setFireTicks(Math.max(victim.getFireTicks(), attack.effectSeconds() * 20));
            else
                potion(victim, victimPotion(effect), attack);
        }
        move(npc, victim, attack, center, direction);
    }

    /** Knockback or pull sets horizontal motion; launch sets vertical motion. */
    private void move(LivingEntity npc, LivingEntity victim, FighterAttack attack, Location center,
            Vector direction) {
        Set<Effect> effects = attack.effects();
        double force = attack.knockback();
        if (force <= 0 || effects.stream().noneMatch(Effect::movement))
            return;
        Vector velocity = new Vector();
        if (effects.contains(Effect.KNOCKBACK)) {
            Vector away = victim.getLocation().toVector().subtract(npc.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < 0.01)
                away = direction.clone().setY(0);
            if (away.lengthSquared() < 0.01)
                away = new Vector(1, 0, 0);
            velocity = away.normalize().multiply(force).setY(Math.min(0.5, force * 0.45));
        } else if (effects.contains(Effect.PULL)) {
            Vector towards = center.toVector().subtract(victim.getLocation().toVector()).setY(0);
            double distance = towards.length();
            if (distance > 0.5)
                velocity = towards.multiply(Math.min(force, 0.15 + distance * 0.15) / distance).setY(0.1);
        }
        if (effects.contains(Effect.LAUNCH))
            velocity.setY(Math.min(1.2, 0.35 + force * 0.35));
        if (velocity.lengthSquared() < 0.0001)
            return;
        beforeKnockback.accept(victim);
        victim.setVelocity(velocity);
    }

    private static PotionEffectType victimPotion(Effect effect) {
        return switch (effect) {
            case POISON -> PotionEffectType.POISON;
            case WITHER -> PotionEffectType.WITHER;
            case WEAKNESS -> PotionEffectType.WEAKNESS;
            case BLINDNESS -> PotionEffectType.BLINDNESS;
            case DARKNESS -> PotionEffectType.DARKNESS;
            case NAUSEA -> PotionEffectType.NAUSEA;
            case HUNGER -> PotionEffectType.HUNGER;
            case SLOWNESS -> PotionEffectType.SLOWNESS;
            case MINING_FATIGUE -> PotionEffectType.MINING_FATIGUE;
            case LEVITATION -> PotionEffectType.LEVITATION;
            case GLOWING -> PotionEffectType.GLOWING;
            default -> throw new IllegalArgumentException(effect + " is not a victim potion");
        };
    }
    private static PotionEffectType casterPotion(Effect effect) {
        return switch (effect) {
            case REGENERATION -> PotionEffectType.REGENERATION;
            case SPEED -> PotionEffectType.SPEED;
            case STRENGTH -> PotionEffectType.STRENGTH;
            case RESISTANCE -> PotionEffectType.RESISTANCE;
            case ABSORPTION -> PotionEffectType.ABSORPTION;
            default -> throw new IllegalArgumentException(effect + " is not a caster buff");
        };
    }

    private Hit chargedHit(LivingEntity npc, LivingEntity target, double amount) {
        if (amount == 0)
            return hit(npc, target, 0);
        // The weapon hit just opened vanilla's hurt-immunity window. Permit this
        // bonus hit, then restore the window; normal damage/protection events still
        // run.
        int previous = target.getNoDamageTicks();
        target.setNoDamageTicks(0);
        try {
            return hit(npc, target, amount);
        } finally {
            target.setNoDamageTicks(Math.max(previous, target.getNoDamageTicks()));
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
    private static void potion(LivingEntity entity, PotionEffectType type, FighterAttack attack) {
        entity.addPotionEffect(new PotionEffect(type, attack.effectSeconds() * 20, attack.effectLevel() - 1));
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
                    Particle particle = AbilityVisuals.particle(attack.visual());
                    from.getWorld().spawnParticle(particle, from.clone().add(0, 1, 0), 36, 0.4, 0.7, 0.4, 0.1);
                    to.getWorld().spawnParticle(particle, to.clone().add(0, 1, 0), 36, 0.4, 0.7, 0.4, 0.1);
                    to.getWorld().playSound(to, AbilityVisuals.impactSound(attack.visual()), 0.8f, 1.2f);
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
        if (attack.shape() == Shape.CHAIN) {
            Particle particle = AbilityVisuals.particle(attack.visual());
            Location first = center.clone().add(direction.clone().multiply(Math.min(6, attack.range())));
            line(particle, center, first, 0.4);
            circle(particle, first.clone().subtract(0, 1.4, 0), attack.size(), 0.15);
            player.getWorld().playSound(player.getLocation(), AbilityVisuals.impactSound(attack.visual()), 0.7f, 1.1f);
            return;
        }
        visualize(attack, center, direction, true);
    }

    private static void visualize(FighterAttack attack, Location center, Vector direction, boolean impact) {
        Particle particle = impact
                ? AbilityVisuals.particle(attack.visual())
                : AbilityVisuals.warningParticle(attack.visual());
        switch (attack.shape()) {
            case SPHERE, TELEPORT -> {
                circle(particle, center, attack.size(), 0.15);
                if (impact && attack.shape() == Shape.SPHERE && attack.visual() == Visual.LIGHTNING)
                    center.getWorld().strikeLightningEffect(center);
            }
            case RING -> {
                circle(particle, center, attack.size(), 0.15);
                circle(particle, center, attack.innerRadius(), 0.15);
                if (impact)
                    for (double radius = attack.innerRadius() + 0.75; radius < attack.size(); radius += 0.75)
                        circle(particle, center, radius, 0.3);
            }
            case BEAM -> {
                double range = visibleRange(attack, center, direction);
                for (double step = 0.5; step <= range; step += 0.5)
                    center.getWorld().spawnParticle(particle, center.clone().add(direction.clone().multiply(step)), 1,
                            0, 0, 0, 0);
            }
            case CONE -> cone(attack, particle, center, direction, impact);
            case DASH -> {
                // Telegraph the charge path at ground level.
                Vector flat = direction.clone().setY(0);
                if (flat.lengthSquared() > 0.01)
                    line(particle, center.clone().add(0, 0.2, 0),
                            center.clone().add(0, 0.2, 0).add(flat.normalize().multiply(attack.range())), 0.5);
            }
            case SELF -> {
                for (int i = 0; i < 16; i++) {
                    double angle = i * Math.PI / 4;
                    center.getWorld().spawnParticle(particle,
                            center.clone().add(Math.cos(angle) * 0.7, i * 0.13, Math.sin(angle) * 0.7), 1, 0, 0, 0,
                            0);
                }
            }
            case CHAIN -> {
                // Chains draw their hops on impact; while casting only the caster glows.
            }
        }
        if (impact && attack.shape() != Shape.CHAIN)
            center.getWorld().playSound(center, AbilityVisuals.impactSound(attack.visual()), 0.7f, 1.1f);
    }

    private static void cone(FighterAttack attack, Particle particle, Location center, Vector direction,
            boolean impact) {
        double range = attack.reach();
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
                center.getWorld().spawnParticle(particle, center.clone().add(offset), impact ? 3 : 1, 0.05, 0.05,
                        0.05, 0.01);
            }
        }
    }

    private static void circle(Particle particle, Location center, double radius, double height) {
        int points = (int) Math.clamp(Math.round(radius * Math.PI * 2.5), 12, 64);
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            center.getWorld().spawnParticle(particle,
                    center.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius), 1, 0, 0, 0, 0);
        }
    }

    private static void line(Particle particle, Location from, Location to, double spacing) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 0.01)
            return;
        path.multiply(1 / length);
        for (double step = 0; step <= length; step += spacing)
            from.getWorld().spawnParticle(particle, from.clone().add(path.clone().multiply(step)), 1, 0, 0, 0, 0);
    }

    private record Hit(boolean allowed, double damage) {
    }
    private record Cast(FighterAttack attack, LivingEntity target, Location center, Vector direction, long impactAt,
            boolean scripted, UUID id) {
    }
    /** A released area that strikes again at the cast's locked position and aim. */
    private record Zone(Cast cast, int remaining, long nextAt) {
    }
}
