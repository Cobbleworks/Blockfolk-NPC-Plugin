package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.Test;

class AiMemoryStoreTest {

    @Test
    void conversationsAreScopedToOneSpawnedNpc() {
        AiMemoryStore memory = new AiMemoryStore();
        UUID firstNpc = UUID.randomUUID();
        UUID secondNpc = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        memory.rememberMessage(firstNpc, player, false, "Player: hello");

        assertEquals(1, memory.recentConversation(firstNpc, player, false).size());
        assertTrue(memory.recentConversation(secondNpc, player, false).isEmpty());
        memory.forget(firstNpc);
        assertTrue(memory.recentConversation(firstNpc, player, false).isEmpty());
    }

    @Test
    void sharedConversationIsVisibleToOtherPlayersOnTheSameNpc() {
        AiMemoryStore memory = new AiMemoryStore();
        UUID npc = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        memory.rememberMessage(npc, firstPlayer, true, "Alex: hello");

        assertEquals(List.of("Alex: hello"), memory.recentConversation(npc, secondPlayer, true));
        assertTrue(memory.recentConversation(npc, secondPlayer, false).isEmpty());
    }

    @Test
    void conversationLimitKeepsOnlyTheNewestMessages() {
        AiMemoryStore memory = new AiMemoryStore(2);
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        memory.rememberMessage(npc, player, false, "first");
        memory.rememberMessage(npc, player, false, "second");
        memory.rememberMessage(npc, player, false, "third");

        assertEquals(List.of("second", "third"), memory.recentConversation(npc, player, false));
    }

    @Test
    void zeroConversationLimitDisablesHistory() {
        AiMemoryStore memory = new AiMemoryStore(0);
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        memory.rememberMessage(npc, player, false, "not retained");

        assertTrue(memory.recentConversation(npc, player, false).isEmpty());
    }
}
