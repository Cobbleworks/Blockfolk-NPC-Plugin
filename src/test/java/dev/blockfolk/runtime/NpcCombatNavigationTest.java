package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.SpecialAttackOptions;
import dev.blockfolk.model.TargetCategory;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.repository.NpcDefinitionRepository;

class NpcCombatNavigationTest {
    private final Plugin plugin = mock(Plugin.class);
    private final NpcDefinitionRepository definitions = mock(NpcDefinitionRepository.class);
    private final NpcInstanceRegistry instances = mock(NpcInstanceRegistry.class);
    private final NativeNpcNavigationService navigation = mock(NativeNpcNavigationService.class);
    private final NpcDefinition definition = mock(NpcDefinition.class);
    private final LivingEntity npc = mock(LivingEntity.class);
    private final Player target = mock(Player.class);
    private final World world = mock(World.class);
    private final ItemStack weapon = mock(ItemStack.class);
    private final UUID targetId = UUID.randomUUID();
    private final NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 64, 0));
    private NpcBehaviourService behaviours;
    private NpcCombatService combat;

    @BeforeEach
    void setUp() {
        when(plugin.namespace()).thenReturn("blockfolk");
        behaviours = spy(new NpcBehaviourService(plugin, definitions, instances, null, null, 0));
        doNothing().when(behaviours).trigger(any(), eq(instance), any());
        doNothing().when(behaviours).trigger(any(), eq(instance), any(), any());
        combat = new NpcCombatService(plugin, definitions, instances, navigation);
        combat.setBehaviourService(behaviours);

        when(definitions.find("guard")).thenReturn(Optional.of(definition));
        when(definition.getCombatProfile()).thenReturn(CombatProfile.disabled().withMaxHealth(20));
        when(definition.getMainHand()).thenReturn(weapon);
        when(definition.getOffHand()).thenReturn(weapon);
        when(weapon.getType()).thenReturn(Material.IRON_SWORD);
        when(instances.findActive()).thenReturn(List.of(instance));
        when(instances.findEntity(instance)).thenReturn(Optional.of(npc));
        when(instances.findByEntityId(1)).thenReturn(Optional.of(instance));
        when(npc.getEntityId()).thenReturn(1);
        when(npc.isValid()).thenReturn(true);
        when(npc.getWorld()).thenReturn(world);
        when(npc.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0));
        when(npc.hasLineOfSight(target)).thenReturn(true);
        EntityEquipment equipment = mock(EntityEquipment.class);
        when(npc.getEquipment()).thenReturn(equipment);
        when(equipment.getItemInMainHand()).thenReturn(weapon);
        when(equipment.getItemInOffHand()).thenReturn(weapon);
        when(target.getEntityId()).thenReturn(2);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Player");
        when(target.isValid()).thenReturn(true);
        when(target.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenAnswer(call -> new Location(world, 10, 64, 0));
        when(target.getEyeLocation()).thenAnswer(call -> target.getLocation().add(0, 1.62, 0));
        when(npc.getEyeLocation()).thenAnswer(call -> new Location(world, 0, 65.62, 0));
    }

    @Test
    void npcHoldingItsPoseWatchesAnOpponentOutOfReachInsteadOfWalking() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(targetId)).thenReturn(target);
            when(definition.isHoldPose()).thenReturn(true);
            begin("chase");

            // Longer than the four-second stalled-chase timeout.
            for (int i = 0; i < 100; i++)
                tick();

            verify(instances, never()).navigate(eq(instance), any(Location.class), any(WalkingSpeed.class));
            verify(npc, atLeastOnce()).setRotation(anyFloat(), anyFloat());
            assertFalse(combat.isEngaged(instance), "an opponent that stays out of reach is let go");
        }
    }

    @Test
    void npcHoldingItsPoseDoesNotFlee() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(targetId)).thenReturn(target);
            when(definition.isHoldPose()).thenReturn(true);
            begin("flee");
            tick();

            assertFalse(combat.isEngaged(instance));
            verify(instances, never()).navigate(eq(instance), any(Location.class), any(WalkingSpeed.class));
        }
    }

    @Test
    void npcHoldingItsPoseDoesNotStepBackToShoot() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(targetId)).thenReturn(target);
            when(definition.isHoldPose()).thenReturn(true);
            // Without sight the archer only aims, so no arrow needs a server.
            when(npc.hasLineOfSight(target)).thenReturn(false);
            begin("retreat");
            for (int i = 0; i < 20; i++)
                tick();

            verify(instances, never()).navigate(eq(instance), any(Location.class), any(WalkingSpeed.class));
            assertTrue(combat.isEngaged(instance));
        }
    }

    @Test
    void huntingNpcHoldingItsPoseIgnoresTargetsOutOfReach() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(targetId)).thenReturn(target);
            when(definition.isHoldPose()).thenReturn(true);
            when(definition.getCombatProfile()).thenReturn(CombatProfile.disabled().withMaxHealth(20)
                    .withAttackReaction(AttackReaction.HUNTING).withTargetPlayers(true));
            when(npc.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(target));

            for (int i = 0; i < 20; i++)
                tick();

            assertFalse(combat.isEngaged(instance), "a seated guard does not pick fights it cannot reach");
        }
    }

    @Test
    void huntingNpcWithoutHoldPoseStillEngagesDistantTargets() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(targetId)).thenReturn(target);
            when(definition.getCombatProfile()).thenReturn(CombatProfile.disabled().withMaxHealth(20)
                    .withAttackReaction(AttackReaction.HUNTING).withTargetPlayers(true));
            when(npc.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(target));

            for (int i = 0; i < 20 && !combat.isEngaged(instance); i++)
                tick();

            assertTrue(combat.isEngaged(instance));
        }
    }

    @Test
    void abilityTargetsCanBeUsedByScriptedCastsWithoutBeingHunted() throws Exception {
        when(definition.getCombatProfile())
                .thenReturn(CombatProfile.disabled().withMaxHealth(20).withAttackReaction(AttackReaction.HUNTING)
                        .withSpecialAttacks(SpecialAttackOptions.disabled().toggleTarget(TargetCategory.PLAYERS)));
        when(npc.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(target));

        for (int i = 0; i < 10; i++)
            tick();

        assertFalse(combat.isEngaged(instance));
        assertEquals(20, combat.useAbility(instance, "lightning_mark", null));
    }

    @ParameterizedTest
    @CsvSource({"IGNORE, false", "IGNORE, true", "FLEE, false", "FLEE, true", "FIGHT_BACK, false", "FIGHT_BACK, true",
            "HUNTING, false", "HUNTING, true"})
    void damageReactionDependsOnModeAndIgnoresHuntTargets(AttackReaction reaction, boolean huntPlayers)
            throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(targetId)).thenReturn(target);
            when(definition.getCombatProfile()).thenReturn(CombatProfile.disabled().withMaxHealth(20)
                    .withAttackReaction(reaction).withTargetPlayers(huntPlayers));

            hitByTarget();
            tick();

            if (reaction == AttackReaction.IGNORE) {
                assertFalse(combat.isEngaged(instance));
                verify(instances, never()).navigate(eq(instance), any(Location.class), any(WalkingSpeed.class));
            } else {
                assertTrue(combat.isEngaged(instance));
                verify(instances).navigate(eq(instance), any(Location.class),
                        eq(reaction == AttackReaction.FLEE ? WalkingSpeed.VERY_FAST : WalkingSpeed.FAST));
            }
        }
    }

    @Test
    void hitNpcStandsUpUnlessItHoldsItsPose() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));
            when(instances.findActive()).thenReturn(List.of());
            org.bukkit.event.entity.EntityDamageEvent damage = mock(org.bukkit.event.entity.EntityDamageEvent.class);
            when(damage.getEntity()).thenReturn(npc);
            // The damage detail reads max health from the attribute registry, which
            // needs a server; standing up is decided before it.
            when(instances.findEntity(instance)).thenReturn(Optional.empty());

            when(definition.isHoldPose()).thenReturn(true);
            behaviours.onDamage(damage);
            verify(instances, never()).stand(instance);

            when(definition.isHoldPose()).thenReturn(false);
            behaviours.onDamage(damage);
            verify(instances).stand(instance);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chase", "retreat", "flee"})
    void questPauseStopsCombatWalkingAndRestoresItAfterDialog(String mode) throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(targetId)).thenReturn(target);
            begin(mode);
            tick();
            verify(instances).navigate(eq(instance), any(Location.class), any(WalkingSpeed.class));

            assertFalse(behaviours.setExternalNavigationPaused(instance, true));
            // Longer than the four-second stalled-chase timeout: a dialog is
            // intentional waiting, not a failed path to the combat target.
            for (int i = 0; i < 100; i++)
                tick();
            verify(instances).navigate(eq(instance), any(Location.class), any(WalkingSpeed.class));
            verify(instances, atLeastOnce()).stopNavigating(instance);
            assertTrue(combat.isEngaged(instance), "Pausing a dialog must preserve the combat target");

            assertTrue(behaviours.setExternalNavigationPaused(instance, false));
            tick();
            verify(instances, times(2)).navigate(eq(instance), any(Location.class), any(WalkingSpeed.class));
        }
    }

    @Test
    void routePauseAloneDoesNotPreventCombatChase() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(targetId)).thenReturn(target);
            doReturn(true).when(behaviours).isNavigationPaused(instance);
            begin("chase");

            tick();

            verify(instances).navigate(eq(instance), any(Location.class), eq(WalkingSpeed.FAST));
        }
    }

    private void begin(String mode) {
        if (mode.equals("retreat")) {
            when(weapon.getType()).thenReturn(Material.BOW);
            when(target.getLocation()).thenAnswer(call -> new Location(world, 2, 64, 0));
        }
        if (mode.equals("flee")) {
            when(definition.getCombatProfile())
                    .thenReturn(CombatProfile.disabled().withMaxHealth(20).withAttackReaction(AttackReaction.FLEE));
            hitByTarget();
        } else {
            assertTrue(combat.startDirectedCombat(instance, target));
        }
    }

    private void hitByTarget() {
        EntityDamageByEntityEvent damage = mock(EntityDamageByEntityEvent.class);
        when(damage.getEntity()).thenReturn(npc);
        when(damage.getDamager()).thenReturn(target);
        combat.onNpcDamage(damage);
    }

    private void tick() throws Exception {
        Method tick = NpcCombatService.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        tick.invoke(combat);
    }
}
