package dev.blockfolk.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import dev.blockfolk.fighters.FighterTemplates;
import dev.blockfolk.fighters.FighterAttack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.SpecialAttack;
import dev.blockfolk.model.SpecialAttackOptions;

class NpcSpecialAttackServiceTest {
    private final NpcSpecialAttackService service = new NpcSpecialAttackService();
    private final UUID instanceId = UUID.randomUUID();
    private List<LivingEntity> nearby = List.of();
    private int lightningEffects;
    private final List<Location> particlePositions = new java.util.ArrayList<>();
    private Vector beamObstacle;
    private boolean safeLanding = true;
    private boolean chunkLoaded = true;
    private Material floorMaterial = Material.STONE;
    private final WorldBorder border = (WorldBorder) Proxy.newProxyInstance(WorldBorder.class.getClassLoader(),
            new Class<?>[]{WorldBorder.class}, (proxy, method, args) -> {
                if (method.getName().equals("isInside"))
                    return true;
                throw new UnsupportedOperationException(method.getName());
            });
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                case "spawnParticle" -> {
                    particlePositions.add(((Location) args[1]).clone());
                    yield null;
                }
                case "playSound" -> null;
                case "strikeLightningEffect" -> {
                    lightningEffects++;
                    yield null;
                }
                case "getNearbyEntitiesByType" -> nearby;
                case "rayTraceBlocks" -> beamObstacle == null ? null : new RayTraceResult(beamObstacle);
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "isChunkLoaded" -> chunkLoaded;
                case "getWorldBorder" -> border;
                case "getBlockAt" -> {
                    int y = args[0] instanceof Location location ? location.getBlockY() : (Integer) args[1];
                    yield block(y < 0);
                }
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    private final Actor npc = new Actor(0);
    private final Actor target = new Actor(3);
    private final SpecialAttackOptions lightning = new SpecialAttackOptions(Set.of(SpecialAttack.LIGHTNING_MARK), 3);

    @Test
    void warnsBeforeImpactAndAttributesDamageToNpc() {
        start(lightning);
        assertEquals(20, target.health);
        assertTrue(tick(79, lightning));
        assertEquals(20, target.health);
        assertTrue(tick(80, lightning));
        assertEquals(14, target.health);
        assertSame(npc.entity, target.damager);
        assertEquals(1, lightningEffects);
    }

    @Test
    void movingOutsideWarningRingDodgesAttack() {
        start(lightning);
        target.x = 6;
        tick(80, lightning);
        assertEquals(20, target.health);
        assertEquals(1, lightningEffects);
    }

    @Test
    void lostSightOrNewlyProtectedTargetCancelsImpact() {
        start(lightning);
        npc.lineOfSight = false;
        tick(80, lightning);
        assertEquals(20, target.health);
        service.clear();
        npc.lineOfSight = true;
        start(lightning);
        assertTrue(service.tick(instanceId, npc.entity, target.entity, lightning, victim -> false, 80));
        assertEquals(20, target.health);
    }

    @Test
    void disablingAttackOrLeavingCombatCancelsPendingCast() {
        start(lightning);
        tick(80, SpecialAttackOptions.disabled());
        assertEquals(20, target.health);
        service.clear();
        start(lightning);
        service.cancelCast(instanceId);
        assertFalse(tick(80, lightning));
        assertEquals(20, target.health);
    }

    @Test
    void changingOpponentCancelsCastAndKeepsCooldown() {
        start(lightning);
        Actor replacement = new Actor(3);
        assertFalse(service.tick(instanceId, npc.entity, replacement.entity, lightning, victim -> true, 80));
        assertEquals(20, target.health);
        assertEquals(20, replacement.health);
    }

    @Test
    void shockwaveFiltersCollateralAndHonorsCancelledDamage() {
        Actor ally = new Actor(2);
        Actor outside = new Actor(5);
        Actor protectedVictim = new Actor(2);
        protectedVictim.cancelDamage = true;
        nearby = List.of(npc.entity, target.entity, ally.entity, protectedVictim.entity, outside.entity);
        SpecialAttackOptions shockwave = new SpecialAttackOptions(Set.of(SpecialAttack.SHOCKWAVE), 3);
        start(shockwave);
        assertTrue(service.tick(instanceId, npc.entity, target.entity, shockwave, victim -> victim != ally.entity, 80));
        assertEquals(16, target.health);
        assertTrue(target.velocity.lengthSquared() > 0);
        assertEquals(20, ally.health);
        assertEquals(20, outside.health);
        assertEquals(0, ally.velocity.lengthSquared());
        assertEquals(20, protectedVictim.health);
        assertEquals(0, protectedVictim.velocity.lengthSquared());
        assertEquals(20, npc.health);
    }

    @Test
    void removingInstanceDropsPendingCastsAndCooldowns() {
        start(lightning);
        service.retainInstances(Set.of());
        assertFalse(tick(80, lightning));
        assertEquals(20, target.health);
        assertTrue(tick(140, lightning));
    }

    @Test
    void fireBreathUsesLockedAimAndFiltersConeVictims() {
        Actor inside = new Actor(4);
        inside.z = 0.5;
        Actor outside = new Actor(4);
        outside.z = 4;
        Actor behind = new Actor(-2);
        nearby = List.of(inside.entity, outside.entity, behind.entity);
        SpecialAttackOptions options = SpecialAttackOptions.disabled().toggle("fire_breath").withIntervalSeconds(3);
        start(options);
        target.z = 4;
        tick(75, options);
        assertEquals(16, inside.health);
        assertEquals(60, inside.fireTicks);
        assertEquals(20, outside.health);
        assertEquals(20, behind.health);
        assertEquals(20, target.health);
    }

    @Test
    void sonicBeamStopsAtBlocksAndHitsOnlyItsWidth() {
        Actor inside = new Actor(2);
        Actor outside = new Actor(2);
        outside.z = 2;
        nearby = List.of(inside.entity, outside.entity);
        SpecialAttackOptions options = SpecialAttackOptions.disabled().toggle("sonic_blast").withIntervalSeconds(3);
        start(options);
        beamObstacle = new Vector(2.5, 0, 0);
        tick(85, options);
        assertEquals(14, inside.health);
        assertEquals(20, outside.health);
        assertEquals(20, target.health);
    }

    @Test
    void defensiveShockwaveFollowsTheNpcInsteadOfTheOriginalGroundSpot() {
        Actor nearOldSpot = new Actor(-3);
        nearby = List.of(nearOldSpot.entity);
        SpecialAttackOptions options = SpecialAttackOptions.disabled().toggle(SpecialAttack.SHOCKWAVE)
                .withIntervalSeconds(3);
        start(options);
        npc.x = 2;
        tick(70, options);
        assertEquals(16, target.health);
        assertEquals(20, nearOldSpot.health);
    }

    @Test
    void deletingOrEditingTheSharedDefinitionInterruptsItsCast() {
        List<FighterAttack> definitions = new java.util.ArrayList<>(FighterTemplates.defaults());
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> definitions, (id, to) -> false, victim -> {
        });
        assertFalse(local.tick(instanceId, npc.entity, target.entity, lightning, victim -> true, 0));
        assertTrue(local.tick(instanceId, npc.entity, target.entity, lightning, victim -> true, 60));
        definitions.removeIf(a -> a.key().equals("lightning_mark"));
        assertFalse(local.tick(instanceId, npc.entity, target.entity, lightning, victim -> true, 80));
        assertEquals(20, target.health);
    }

    @Test
    void instantBlinkRepositionsThroughTheRegistryCallbackWithinItsConfiguredDistance() {
        Location[] destination = {null};
        NpcSpecialAttackService local = new NpcSpecialAttackService(FighterTemplates::defaults, (id, to) -> {
            assertEquals(instanceId, id);
            destination[0] = to;
            return true;
        }, victim -> {
        });
        SpecialAttackOptions options = SpecialAttackOptions.disabled().toggle("blink").withIntervalSeconds(3);
        assertFalse(local.tick(instanceId, npc.entity, target.entity, options, victim -> true, 0));
        assertTrue(local.tick(instanceId, npc.entity, target.entity, options, victim -> true, 60));
        assertNotNull(destination[0]);
        assertTrue(destination[0].distanceSquared(npc.entity.getLocation()) <= 25);
        assertEquals(20, target.health);
    }

    @Test
    void blinkRejectsBlockedHazardousOrUnloadedLandingSpots() {
        Location to = new Location(world, 0.5, 0, 0.5);
        assertTrue(NpcSpecialAttackService.safeBlinkDestination(to));
        safeLanding = false;
        assertFalse(NpcSpecialAttackService.safeBlinkDestination(to));
        safeLanding = true;
        floorMaterial = Material.MAGMA_BLOCK;
        assertFalse(NpcSpecialAttackService.safeBlinkDestination(to));
        floorMaterial = Material.STONE;
        chunkLoaded = false;
        assertFalse(NpcSpecialAttackService.safeBlinkDestination(to));
    }

    @Test
    void scriptedDelayedAbilityWorksWithoutAnAssignmentOrCombatOpponent() {
        var options = SpecialAttackOptions.disabled();
        assertEquals(20, service.useAbility(instanceId, npc.entity, target.entity, "lightning_mark", options,
                victim -> true, 0));
        assertTrue(service.isCasting(instanceId));
        assertTrue(service.tick(instanceId, npc.entity, null, options, victim -> true, 4));
        assertEquals(20, target.health);
        assertTrue(particlePositions.stream().anyMatch(location -> location.getX() < 1 && location.getY() > 0.5));
        assertTrue(service.tick(instanceId, npc.entity, null, options, victim -> true, 20));
        assertEquals(14, target.health);
        assertFalse(service.isCasting(instanceId));
        assertEquals(-1, service.useAbility(instanceId, npc.entity, target.entity, "lightning_mark", options,
                victim -> true, 21));
    }

    @Test
    void scriptedNpcSphereNeedsNoTargetButAimedAndTargetOriginAbilitiesDo() {
        var options = SpecialAttackOptions.disabled();
        assertEquals(-1, service.useAbility(instanceId, npc.entity, null, "sonic_blast", options, victim -> true, 0));
        assertEquals(-1,
                service.useAbility(instanceId, npc.entity, null, "lightning_mark", options, victim -> true, 0));
        nearby = List.of(target.entity);
        assertEquals(10, service.useAbility(instanceId, npc.entity, null, "shockwave", options, victim -> true, 0));
        assertTrue(service.tick(instanceId, npc.entity, null, options, victim -> true, 10));
        assertEquals(16, target.health);
    }

    @Test
    void chargeAllowsWeaponCombatAndReleasesOnlyOnceAtTheActualHitPosition() {
        FighterAttack charge = FighterTemplates.defaults().stream().filter(a -> a.key().equals("lightning_mark"))
                .findFirst().orElseThrow().withCastMode(FighterAttack.CastMode.NEXT_ATTACK)
                .withEffects(4, Set.of(), 3, 1, 0);
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(charge), (id, to) -> false,
                victim -> {
                });
        var options = SpecialAttackOptions.disabled().toggle(charge.key()).withIntervalSeconds(3);
        assertFalse(local.tick(instanceId, npc.entity, target.entity, options, victim -> true, 0));
        assertFalse(local.tick(instanceId, npc.entity, target.entity, options, victim -> true, 60));
        UUID token = local.chargeId(instanceId);
        assertNotNull(token);
        assertFalse(local.isCasting(instanceId));
        assertFalse(local.tick(instanceId, npc.entity, target.entity, options, victim -> true, 64));
        assertEquals(20, target.health);
        assertFalse(local.onSuccessfulWeaponHit(instanceId, token, npc.entity, target.entity, options, victim -> false,
                65));
        assertEquals(token, local.chargeId(instanceId));
        target.x = 6; // Target moved after charging: impact must follow the hit, not the old marker.
        assertTrue(
                local.onSuccessfulWeaponHit(instanceId, token, npc.entity, target.entity, options, victim -> true, 66));
        assertEquals(16, target.health);
        assertNull(local.chargeId(instanceId));
        assertFalse(
                local.onSuccessfulWeaponHit(instanceId, token, npc.entity, target.entity, options, victim -> true, 67));
        assertEquals(16, target.health);
        long readyAt = 66 + charge.cooldownTicks();
        assertEquals(-1, local.useAbility(instanceId, npc.entity, target.entity, charge.key(), options, victim -> true,
                readyAt - 1));
        assertEquals(0, local.useAbility(instanceId, npc.entity, target.entity, charge.key(), options, victim -> true,
                readyAt));
    }

    @Test
    void cancelledBonusDamageConsumesTheChargeWithoutApplyingSecondaryEffects() {
        FighterAttack charge = FighterTemplates.defaults().get(8).withCastMode(FighterAttack.CastMode.NEXT_ATTACK);
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(charge), (id, to) -> false,
                victim -> {
                });
        var options = SpecialAttackOptions.disabled();
        local.useAbility(instanceId, npc.entity, null, charge.key(), options, victim -> true, 0);
        UUID token = local.chargeId(instanceId);
        target.cancelDamage = true;
        target.noDamageTicks = 10;
        assertTrue(
                local.onSuccessfulWeaponHit(instanceId, token, npc.entity, target.entity, options, victim -> true, 1));
        assertNull(local.chargeId(instanceId));
        assertEquals(20, target.health);
        assertEquals(0, target.fireTicks);
        assertEquals(10, target.noDamageTicks);
    }

    @Test
    void staleHitTokensCannotReleaseAReplacedChargeAndChargesExpire() {
        FighterAttack charge = FighterTemplates.defaults().get(8).withCastMode(FighterAttack.CastMode.NEXT_ATTACK);
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(charge), (id, to) -> false,
                victim -> {
                });
        var options = SpecialAttackOptions.disabled();
        assertEquals(0, local.useAbility(instanceId, npc.entity, null, charge.key(), options, victim -> true, 0));
        UUID stale = local.chargeId(instanceId);
        local.cancelCast(instanceId);
        assertEquals(0, local.useAbility(instanceId, npc.entity, null, charge.key(), options, victim -> true, 300));
        UUID fresh = local.chargeId(instanceId);
        assertNotEquals(stale, fresh);
        assertFalse(local.onSuccessfulWeaponHit(instanceId, stale, npc.entity, target.entity, options, victim -> true,
                301));
        assertEquals(fresh, local.chargeId(instanceId));
        assertFalse(local.tick(instanceId, npc.entity, null, options, victim -> true, 900));
        assertNull(local.chargeId(instanceId));
        assertEquals(20, target.health);
    }

    @Test
    void editingDeletingOrUnassigningAnAbilityPreventsAQueuedHitFromReleasingIt() {
        FighterAttack charge = FighterTemplates.defaults().get(8).withCastMode(FighterAttack.CastMode.NEXT_ATTACK);
        List<FighterAttack> library = new java.util.ArrayList<>(List.of(charge));
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> library, (id, to) -> false, victim -> {
        });
        var options = SpecialAttackOptions.disabled().toggle(charge.key()).withIntervalSeconds(3);
        local.tick(instanceId, npc.entity, target.entity, options, victim -> true, 0);
        local.tick(instanceId, npc.entity, target.entity, options, victim -> true, 60);
        UUID token = local.chargeId(instanceId);
        assertFalse(local.onSuccessfulWeaponHit(instanceId, token, npc.entity, target.entity,
                SpecialAttackOptions.disabled(), victim -> true, 61));
        library.set(0, charge.withName("Changed"));
        assertFalse(
                local.onSuccessfulWeaponHit(instanceId, token, npc.entity, target.entity, options, victim -> true, 61));
        library.clear();
        assertFalse(
                local.onSuccessfulWeaponHit(instanceId, token, npc.entity, target.entity, options, victim -> true, 61));
        assertEquals(20, target.health);
    }

    @Test
    void scriptedChargesSurviveEnteringCombatWithoutAutomaticAssignment() {
        FighterAttack charge = FighterTemplates.defaults().get(8).withCastMode(FighterAttack.CastMode.NEXT_ATTACK);
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(charge), (id, to) -> false,
                victim -> {
                });
        var options = SpecialAttackOptions.disabled();
        assertEquals(0, local.useAbility(instanceId, npc.entity, null, charge.key(), options, victim -> true, 0));
        UUID token = local.chargeId(instanceId);
        local.cancelAutomaticCast(instanceId);
        assertFalse(local.tick(instanceId, npc.entity, target.entity, options, victim -> true, 4));
        assertEquals(token, local.chargeId(instanceId));
        target.noDamageTicks = 10;
        assertTrue(
                local.onSuccessfulWeaponHit(instanceId, token, npc.entity, target.entity, options, victim -> true, 5));
        assertEquals(10, target.noDamageTicks);
        assertEquals(16, target.health);
        assertEquals(60, target.fireTicks);
    }

    @Test
    void lingeringAreasPulseAtTheMarkedSpotWithoutBlockingCombatAndStopWhenCancelled() {
        FighterAttack cloud = FighterAttack.builder("cloud", "Cloud").origin(FighterAttack.Origin.TARGET).size(2)
                .delayTicks(0).damage(2).effects(Set.of()).pulses(3).pulseIntervalTicks(10).build();
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(cloud), (id, to) -> false, victim -> {
        });
        var options = SpecialAttackOptions.disabled();
        nearby = List.of(target.entity);
        assertEquals(0, local.useAbility(instanceId, npc.entity, target.entity, "cloud", options, victim -> true, 0));
        assertEquals(18, target.health);
        assertFalse(local.tick(instanceId, npc.entity, null, options, victim -> true, 9));
        assertEquals(18, target.health);
        assertFalse(local.tick(instanceId, npc.entity, null, options, victim -> true, 10));
        assertEquals(16, target.health);
        target.x = 8; // Leaving the marked area avoids later pulses.
        local.tick(instanceId, npc.entity, null, options, victim -> true, 20);
        assertEquals(16, target.health);
        target.x = 3;
        local.tick(instanceId, npc.entity, null, options, victim -> true, 30);
        assertEquals(16, target.health); // All three pulses are spent.

        assertEquals(0, local.useAbility(instanceId, npc.entity, target.entity, "cloud", options, victim -> true,
                cloud.cooldownTicks()));
        local.cancelCast(instanceId);
        local.tick(instanceId, npc.entity, null, options, victim -> true, cloud.cooldownTicks() + 10);
        assertEquals(14, target.health);
    }

    @Test
    void chainJumpsBetweenNearbyEligibleVictimsUpToItsLimit() {
        FighterAttack chain = FighterAttack.builder("chain", "Chain").shape(FighterAttack.Shape.CHAIN).size(4)
                .chainTargets(3).delayTicks(0).damage(3).build();
        Actor near = new Actor(6);
        Actor next = new Actor(9);
        Actor ally = new Actor(4);
        Actor beyond = new Actor(10);
        nearby = List.of(npc.entity, target.entity, ally.entity, near.entity, next.entity, beyond.entity);
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(chain), (id, to) -> false, victim -> {
        });
        assertEquals(0, local.useAbility(instanceId, npc.entity, target.entity, "chain",
                SpecialAttackOptions.disabled(), victim -> victim != ally.entity, 0));
        assertEquals(17, target.health);
        assertEquals(17, near.health);
        assertEquals(17, next.health);
        assertEquals(20, beyond.health);
        assertEquals(20, ally.health);
        assertEquals(20, npc.health);
    }

    @Test
    void dashStopsInFrontOfTheTargetAndHitsAlongItsPath() {
        FighterAttack dash = FighterAttack.builder("dash", "Dash").shape(FighterAttack.Shape.DASH).range(10).size(1)
                .delayTicks(0).damage(4).build();
        Location[] landing = {null};
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(dash), (id, to) -> {
            landing[0] = to;
            return true;
        }, victim -> {
        });
        Actor inPath = new Actor(2);
        Actor aside = new Actor(2);
        aside.z = 3;
        target.x = 6;
        nearby = List.of(inPath.entity, aside.entity, target.entity);
        assertEquals(0, local.useAbility(instanceId, npc.entity, target.entity, "dash", SpecialAttackOptions.disabled(),
                victim -> true, 0));
        assertNotNull(landing[0]);
        assertEquals(4.5, landing[0].getX(), 0.01);
        assertEquals(16, inPath.health);
        assertEquals(16, target.health);
        assertEquals(20, aside.health);
    }

    @Test
    void dashWithoutASafeLandingFizzlesAndSelfAbilitiesNeverHitOthers() {
        FighterAttack dash = FighterAttack.builder("dash", "Dash").shape(FighterAttack.Shape.DASH).delayTicks(0)
                .damage(4).build();
        FighterAttack self = FighterAttack.builder("self", "Self").shape(FighterAttack.Shape.SELF).delayTicks(0)
                .damage(4).build();
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(dash, self), (id, to) -> true,
                victim -> {
                });
        nearby = List.of(target.entity);
        safeLanding = false;
        assertEquals(0, local.useAbility(instanceId, npc.entity, target.entity, "dash", SpecialAttackOptions.disabled(),
                victim -> true, 0));
        assertEquals(20, target.health);
        assertEquals(0, local.useAbility(instanceId, npc.entity, null, "self", SpecialAttackOptions.disabled(),
                victim -> true, 0));
        assertEquals(20, target.health);
    }

    @Test
    void pullDrawsVictimsInAndLaunchThrowsThemUp() {
        FighterAttack well = FighterAttack.builder("well", "Well").size(5).delayTicks(0).damage(1)
                .effects(FighterAttack.Effect.PULL, FighterAttack.Effect.LAUNCH).knockback(1).build();
        NpcSpecialAttackService local = new NpcSpecialAttackService(() -> List.of(well), (id, to) -> false, victim -> {
        });
        nearby = List.of(target.entity);
        assertEquals(0, local.useAbility(instanceId, npc.entity, null, "well", SpecialAttackOptions.disabled(),
                victim -> true, 0));
        assertTrue(target.velocity.getX() < 0, "pulled towards the caster");
        assertTrue(target.velocity.getY() > 0.5, "launched upwards");
    }

    private Block block(boolean floor) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isPassable" -> !floor && safeLanding;
                    case "isLiquid" -> false;
                    case "isSolid" -> floor;
                    case "getType" -> floor ? floorMaterial : Material.AIR;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private void start(SpecialAttackOptions options) {
        assertFalse(tick(0, options));
        assertTrue(tick(60, options));
    }

    private boolean tick(long tick, SpecialAttackOptions options) {
        return service.tick(instanceId, npc.entity, target.entity, options, victim -> true, tick);
    }

    private final class Actor {
        private final UUID id = UUID.randomUUID();
        private double x;
        private double z;
        private int fireTicks;
        private int noDamageTicks;
        private double health = 20;
        private boolean lineOfSight = true;
        private boolean cancelDamage;
        private Vector velocity = new Vector();
        private Object damager;
        private final LivingEntity entity;

        private Actor(double x) {
            this.x = x;
            entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                    new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getLocation", "getEyeLocation" -> new Location(world, this.x, 0, this.z);
                        case "getWorld" -> world;
                        case "getHealth" -> health;
                        case "getFireTicks" -> fireTicks;
                        case "getNoDamageTicks" -> noDamageTicks;
                        case "setNoDamageTicks" -> {
                            noDamageTicks = (Integer) args[0];
                            yield null;
                        }
                        case "setFireTicks" -> {
                            fireTicks = (Integer) args[0];
                            yield null;
                        }
                        case "getAbsorptionAmount" -> 0.0;
                        case "isDead" -> health <= 0;
                        case "isValid" -> true;
                        case "hasLineOfSight" -> lineOfSight;
                        case "swingMainHand" -> null;
                        case "setVelocity" -> {
                            velocity = (Vector) args[0];
                            yield null;
                        }
                        case "damage" -> {
                            damager = args[1];
                            if (!cancelDamage && noDamageTicks == 0)
                                health = Math.max(0, health - (Double) args[0]);
                            yield null;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
