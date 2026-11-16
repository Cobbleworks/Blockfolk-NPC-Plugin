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
import org.junit.jupiter.params.provider.ValueSource;

import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
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
            EntityDamageByEntityEvent damage = mock(EntityDamageByEntityEvent.class);
            when(damage.getEntity()).thenReturn(npc);
            when(damage.getDamager()).thenReturn(target);
            combat.onNpcDamage(damage);
        } else {
            assertTrue(combat.startDirectedCombat(instance, target));
        }
    }

    private void tick() throws Exception {
        Method tick = NpcCombatService.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        tick.invoke(combat);
    }
}
