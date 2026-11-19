package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import dev.blockfolk.ai.AiControlService;
import dev.blockfolk.ai.AiControlSettings;
import dev.blockfolk.ai.NpcConversationSettings;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.NpcDefinitionRepository;

class NpcConversationServiceTest {

    private static final UUID TAVERN_KEEPER = UUID.randomUUID();
    private static final UUID GUARD = UUID.randomUUID();

    @Test
    void npcNeverRespondsToItsOwnLines() {
        // For example a repeated Send Dialog line on an idle routine.
        assertFalse(NpcConversationService.heardOthers(TAVERN_KEEPER, List.of(TAVERN_KEEPER, TAVERN_KEEPER)));
    }

    @Test
    void npcRespondsWhenAnotherNpcSpokeInTheRound() {
        assertTrue(NpcConversationService.heardOthers(TAVERN_KEEPER, List.of(GUARD, TAVERN_KEEPER)));
        assertTrue(NpcConversationService.heardOthers(GUARD, List.of(TAVERN_KEEPER)));
    }

    @Test
    void quietSpeakersStartNoRepliesButAiListenersRememberTheLine() {
        World world = mock(World.class);
        NpcDefinition cranky = aiPreset("Cranky");
        NpcDefinition donkey = aiPreset("Donkey");
        NpcInstance crankyNpc = new NpcInstance(UUID.randomUUID(), cranky.getKey(), new Location(null, 0, 64, 0));
        NpcInstance donkeyNpc = new NpcInstance(UUID.randomUUID(), donkey.getKey(), new Location(null, 2, 64, 0));
        Map<NpcInstance, Location> where = Map.of(crankyNpc, new Location(world, 0, 64, 0), donkeyNpc,
                new Location(world, 2, 64, 0));
        Map<String, NpcDefinition> presets = Map.of(cranky.getKey(), cranky, donkey.getKey(), donkey);
        NpcDefinitionRepository definitions = mock(NpcDefinitionRepository.class);
        when(definitions.find(anyString())).thenAnswer(call -> Optional.ofNullable(presets.get(call.getArgument(0))));
        NpcInstanceRegistry instances = mock(NpcInstanceRegistry.class);
        when(instances.findById(any())).thenAnswer(call -> List.of(crankyNpc, donkeyNpc).stream()
                .filter(npc -> npc.getId().equals(call.getArgument(0))).findFirst());
        when(instances.findActive()).thenReturn(List.of(crankyNpc, donkeyNpc));
        when(instances.currentLocation(any())).thenAnswer(call -> where.get(call.getArgument(0)).clone());
        AiControlService ai = mock(AiControlService.class);
        when(ai.configured()).thenReturn(true);
        NpcConversationService service = new NpcConversationService(mock(Plugin.class), definitions, instances,
                NpcConversationSettings.defaults());
        service.setAiControlService(ai);
        service.setQuietFilter(npc -> true);

        service.npcSpoke(crankyNpc, cranky, "Back in my day...", true);

        verify(ai).noteAction(crankyNpc, "You said: \"Back in my day...\"");
        verify(ai).noteAction(donkeyNpc, "Cranky said: \"Back in my day...\"");
        verify(ai, never()).invokeNpcConversation(any(), any(), any(), any(), any());
    }

    private static NpcDefinition aiPreset(String name) {
        NpcDefinition definition = NpcDefinition.create(name);
        definition.setAiControlSettings(
                AiControlSettings.defaults().withIdentity("A " + name).withEnabled(true).withRespondToNpc(true));
        return definition;
    }

    @Test
    void formattingCodesAreRemovedFromHeardLines() {
        assertEquals("Fresh bread!", NpcConversationService.stripFormatting(" §6Fresh §lbread! "));
    }
}
