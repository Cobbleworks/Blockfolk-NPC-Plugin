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
                case "spawnParticle", "playSound" -> null;
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
                            if (!cancelDamage)
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
