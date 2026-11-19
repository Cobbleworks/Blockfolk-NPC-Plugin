package dev.blockfolk.integration.beautyquests;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.blockfolk.gui.GuiService;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.runtime.NpcBehaviourService;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import fr.skytasul.quests.api.QuestsAPI;
import fr.skytasul.quests.api.npcs.NpcClickType;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

class BeautyQuestsClickTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final NpcDefinitionRepository definitions = mock(NpcDefinitionRepository.class);
    private final NpcInstanceRegistry instances = mock(NpcInstanceRegistry.class);
    private final Player player = mock(Player.class);
    private final Entity entity = mock(Entity.class);
    private final NpcInstance instance = new NpcInstance(UUID.randomUUID(), "guard", new Location(null, 0, 64, 0));
    private final List<Listener> listeners = new ArrayList<>();
    private final List<Listener> questListeners = new ArrayList<>();
    private final List<NpcClickType> questClicks = new ArrayList<>();
    private NpcBehaviourService behaviours;
    private GuiService gui;
    private boolean consumeClick;

    @BeforeEach
    void setUp() {
        when(plugin.getName()).thenReturn("Blockfolk");
        when(plugin.namespace()).thenReturn("blockfolk");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(entity.getEntityId()).thenReturn(42);
        when(instances.findByEntityId(42)).thenReturn(Optional.of(instance));
        when(definitions.find("guard")).thenReturn(Optional.of(new NpcDefinition("guard")));

        behaviours = spy(new NpcBehaviourService(plugin, definitions, instances, null, null, 0));
        doNothing().when(behaviours).trigger(any(BehaviourEvent.class), eq(instance), eq(player));
        doNothing().when(behaviours).onDamage(any());
        gui = spy(new GuiService(plugin, definitions, null, instances, null, null, null, null, null, null, null, null));
        gui.setBehaviourService(behaviours);
        doNothing().when(gui).openEditor(eq(player), any(NpcDefinition.class));

        BeautyQuestsIntegration integration = spy(
                new BeautyQuestsIntegration(plugin, definitions, instances, behaviours));
        // Model the BeautyQuests API contract: cancelled source events are ignored,
        // and consuming a quest interaction cancels the original event synchronously.
        doAnswer(call -> {
            Cancellable event = call.getArgument(0);
            if (!event.isCancelled()) {
                questClicks.add(call.getArgument(3));
                event.setCancelled(consumeClick);
            }
            return null;
        }).when(integration).npcClicked(any(Cancellable.class), anyString(), eq(player), any(NpcClickType.class));
        try (var api = mockStatic(QuestsAPI.class)) {
            api.when(QuestsAPI::getAPI).thenReturn(mock(QuestsAPI.class));
            integration.register();
        }
        ArgumentCaptor<Listener> registered = ArgumentCaptor.forClass(Listener.class);
        // The kill-target factory and the click listener, in registration order.
        verify(manager, times(2)).registerEvents(registered.capture(), eq(plugin));
        questListeners.addAll(registered.getAllValues());
        listeners.addAll(questListeners);
        listeners.add(gui);
        listeners.add(behaviours);
    }

    @Test
    void questRightClickRunsBeforeVanillaInteractionIsCancelled() throws Exception {
        consumeClick = true;
        PlayerInteractEntityEvent event = rightClick(EquipmentSlot.HAND);

        dispatch(event);

        assertEquals(List.of(NpcClickType.RIGHT), questClicks);
        assertTrue(event.isCancelled());
        verify(behaviours, never()).trigger(eq(BehaviourEvent.RIGHT_CLICK), any(), any());
    }

    @Test
    void unclaimedRightClickFallsBackToNormalBehaviour() throws Exception {
        PlayerInteractEntityEvent event = rightClick(EquipmentSlot.HAND);

        dispatch(event);

        assertEquals(List.of(NpcClickType.RIGHT), questClicks);
        verify(behaviours).trigger(BehaviourEvent.RIGHT_CLICK, instance, player);
        assertTrue(event.isCancelled(), "Vanilla mannequin interaction remains suppressed");
    }

    @Test
    void normalRightClickStillWorksWithoutBeautyQuests() throws Exception {
        listeners.removeAll(questListeners);

        dispatch(rightClick(EquipmentSlot.HAND));

        assertTrue(questClicks.isEmpty());
        verify(behaviours).trigger(BehaviourEvent.RIGHT_CLICK, instance, player);
    }

    @Test
    void shiftRightClickKeepsAdminEditorPriority() throws Exception {
        when(player.isSneaking()).thenReturn(true);
        when(player.hasPermission("blockfolk.admin")).thenReturn(true);

        dispatch(rightClick(EquipmentSlot.HAND));

        assertTrue(questClicks.isEmpty());
        verify(gui).openEditor(eq(player), any(NpcDefinition.class));
        verify(behaviours, never()).trigger(eq(BehaviourEvent.RIGHT_CLICK), any(), any());
    }

    @Test
    void nonAdminShiftRightClickReachesQuests() throws Exception {
        when(player.isSneaking()).thenReturn(true);
        consumeClick = true;

        dispatch(rightClick(EquipmentSlot.HAND));

        assertEquals(List.of(NpcClickType.SHIFT_RIGHT), questClicks);
        verify(gui, never()).openEditor(any(), any());
    }

    @Test
    void offhandAndAlreadyCancelledRightClicksAreIgnored() throws Exception {
        dispatch(rightClick(EquipmentSlot.OFF_HAND));
        PlayerInteractEntityEvent cancelled = rightClick(EquipmentSlot.HAND);
        cancelled.setCancelled(true);
        dispatch(cancelled);

        assertTrue(questClicks.isEmpty());
        verify(behaviours, never()).trigger(eq(BehaviourEvent.RIGHT_CLICK), any(), any());
    }

    @Test
    void selectingAnInvulnerableNpcConsumesAttackBeforeDamageOrLocalActions() throws Exception {
        when(entity.isInvulnerable()).thenReturn(true);
        consumeClick = true;
        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(player, entity, true);

        dispatch(event);
        dispatchDamageIfAttackContinues(event);

        assertEquals(List.of(NpcClickType.LEFT), questClicks);
        assertTrue(event.isCancelled());
        verify(behaviours, never()).trigger(eq(BehaviourEvent.LEFT_CLICK), any(), any());
    }

    @Test
    void questSelectionOnAVulnerableNpcPreventsLocalLeftClickActions() throws Exception {
        consumeClick = true;
        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(player, entity, true);

        dispatch(event);
        dispatchDamageIfAttackContinues(event);

        assertEquals(List.of(NpcClickType.LEFT), questClicks);
        assertTrue(event.isCancelled());
        verify(behaviours, never()).trigger(eq(BehaviourEvent.LEFT_CLICK), any(), any());
    }

    @Test
    void unclaimedLeftClickStillReachesNormalBehaviourOnce() throws Exception {
        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(player, entity, true);

        dispatch(event);
        dispatchDamageIfAttackContinues(event);

        assertFalse(event.isCancelled());
        assertEquals(List.of(NpcClickType.LEFT), questClicks);
        verify(behaviours).trigger(BehaviourEvent.LEFT_CLICK, instance, player);
    }

    @Test
    void shiftLeftClickAndCancelledAttacksRespectQuestRouting() throws Exception {
        when(player.isSneaking()).thenReturn(true);
        consumeClick = true;
        dispatch(new PrePlayerAttackEntityEvent(player, entity, true));
        PrePlayerAttackEntityEvent cancelled = new PrePlayerAttackEntityEvent(player, entity, true);
        cancelled.setCancelled(true);
        dispatch(cancelled);

        assertEquals(List.of(NpcClickType.SHIFT_LEFT), questClicks);
    }

    private PlayerInteractEntityEvent rightClick(EquipmentSlot hand) {
        return new PlayerInteractEntityEvent(player, entity, hand);
    }

    private void dispatchDamageIfAttackContinues(PrePlayerAttackEntityEvent attack) throws Exception {
        if (attack.isCancelled() || entity.isInvulnerable())
            return;
        EntityDamageByEntityEvent damage = mock(EntityDamageByEntityEvent.class);
        when(damage.getDamager()).thenReturn(player);
        when(damage.getEntity()).thenReturn(entity);
        dispatch(damage);
    }

    // Exercise Bukkit's priority and ignoreCancelled semantics without a server.
    private void dispatch(Event event) throws Exception {
        record Handler(Listener listener, Method method, EventHandler annotation) {
        }
        List<Handler> handlers = new ArrayList<>();
        for (Listener listener : listeners) {
            for (Method method : listener.getClass().getDeclaredMethods()) {
                EventHandler annotation = method.getAnnotation(EventHandler.class);
                if (annotation != null && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(event)) {
                    method.setAccessible(true);
                    handlers.add(new Handler(listener, method, annotation));
                }
            }
        }
        handlers.sort(Comparator.comparing(handler -> handler.annotation().priority()));
        for (Handler handler : handlers) {
            new RegisteredListener(handler.listener(), (listener, dispatched) -> {
                try {
                    handler.method().invoke(listener, dispatched);
                } catch (ReflectiveOperationException exception) {
                    throw new org.bukkit.event.EventException(exception);
                }
            }, handler.annotation().priority(), plugin, handler.annotation().ignoreCancelled()).callEvent(event);
        }
    }
}
