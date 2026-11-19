package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.RegionalEvent;
import dev.blockfolk.model.RegionalEvent.Phase;
import dev.blockfolk.model.RegionalEvent.Region;
import dev.blockfolk.model.RegionalEvent.Trigger;
import dev.blockfolk.model.StoredLocation;
import dev.blockfolk.repository.RegionalEventRepository;

class RegionalEventServiceTest {

    @Test
    void timeTriggerHandlesWorldTimeWrapWithoutRepeatingWithinTheSamePeriod() {
        assertTrue(RegionalEventService.crossed(22_999, 1_000, DayPeriod.MORNING));
        assertFalse(RegionalEventService.crossed(23_000, 1_000, DayPeriod.MORNING));
        assertFalse(RegionalEventService.crossed(13_003, 12_999, DayPeriod.EVENING));
        assertFalse(RegionalEventService.crossed(12_999, 12_999, DayPeriod.EVENING));
    }

    @Test
    void phaseWaitsForAllActorsAndReleasesEveryActorAtTheEnd() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        RegionalEventRepository repository = mock(RegionalEventRepository.class);
        NpcInstanceRegistry instances = mock(NpcInstanceRegistry.class);
        NpcBehaviourService behaviours = mock(NpcBehaviourService.class);
        RegionalEventService service = new RegionalEventService(plugin, repository, instances, behaviours);
        Region region = new Region(UUID.randomUUID(), "Town", "world", -10, -10, 10, 10);
        when(repository.region(region.id())).thenReturn(region);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("world");
        StoredLocation spawn = new StoredLocation("world", 0, 64, 0, 0, 0);
        NpcInstance watcher = new NpcInstance(UUID.randomUUID(), "watcher", spawn, spawn, 0);
        NpcInstance bartender = new NpcInstance(UUID.randomUUID(), "bartender", spawn, spawn, 0);
        for (NpcInstance npc : List.of(watcher, bartender)) {
            when(instances.findById(npc.getId())).thenReturn(Optional.of(npc));
            when(instances.findEntity(npc)).thenReturn(Optional.of(mock(LivingEntity.class)));
            when(behaviours.canJoinRegionalEvent(npc)).thenReturn(true);
            when(behaviours.isInRegionalEvent(npc)).thenReturn(true);
        }
        List<BehaviourAction> wait = List.of(new BehaviourAction(BehaviourActionType.WAIT, "2"));
        List<BehaviourAction> wave = List.of(new BehaviourAction(BehaviourActionType.WAVE, null));
        Player trigger = mock(Player.class);
        Map<UUID, Runnable> completions = new LinkedHashMap<>();
        doAnswer(call -> {
            NpcInstance npc = call.getArgument(1);
            completions.put(npc.getId(), call.getArgument(4));
            return null;
        }).when(behaviours).runRegionalActions(any(), any(), eq(trigger), anyString(), any());
        RegionalEvent event = new RegionalEvent(UUID.randomUUID(), region.id(), "Late Train", Trigger.MANUAL,
                DayPeriod.EVENING, false,
                List.of(new Phase(UUID.randomUUID(), Map.of(watcher.getId(), wait, bartender.getId(), wait)),
                        new Phase(UUID.randomUUID(), Map.of(watcher.getId(), wave))));
        when(repository.event(event.id())).thenReturn(event);
        ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            service.start();
            verify(scheduler).runTaskTimer(eq(plugin), tick.capture(), eq(1L), eq(5L));
            when(behaviours.canJoinRegionalEvent(bartender)).thenReturn(false);
            assertNotNull(service.play(event, null));
            verify(behaviours, never()).beginRegionalEvent(any());
            when(behaviours.canJoinRegionalEvent(bartender)).thenReturn(true);

            service.emit(event.reference(), trigger);
            assertTrue(service.isRunning(event.id()));
            assertNotNull(service.play(event, null));
            completions.remove(watcher.getId()).run();
            tick.getValue().run();
            verify(behaviours, never()).runRegionalActions(eq(wave), any(), eq(trigger), anyString(), any());
            completions.remove(bartender.getId()).run();
            tick.getValue().run();
            verify(behaviours).runRegionalActions(eq(wave), eq(watcher), eq(trigger), eq(event.name()), any());
            verify(behaviours, never()).endRegionalEvent(any(), anyBoolean());
            completions.remove(watcher.getId()).run();
            tick.getValue().run();
            verify(behaviours).endRegionalEvent(watcher, true);
            verify(behaviours).endRegionalEvent(bartender, true);
            assertFalse(service.isRunning(event.id()));
        }
    }
}
