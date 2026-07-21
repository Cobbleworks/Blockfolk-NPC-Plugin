package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AiMemoryStoreTest {

    @Test
    void conversationsAreScopedToOneSpawnedNpc() {
        AiMemoryStore memory = new AiMemoryStore();
        UUID firstNpc = UUID.randomUUID();
        UUID secondNpc = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        memory.rememberMessage(firstNpc, player, "Player: hello");

        assertEquals(1, memory.recentConversation(firstNpc, player).size());
        assertTrue(memory.recentSharedConversation(firstNpc).isEmpty());
        assertTrue(memory.recentConversation(secondNpc, player).isEmpty());
        memory.forget(firstNpc);
        assertTrue(memory.recentConversation(firstNpc, player).isEmpty());
    }

    @Test
    void keepsTheTwentyLatestAiActivities() {
        AiMemoryStore memory = new AiMemoryStore();
        UUID npc = UUID.randomUUID();
        for (int index = 1; index <= 25; index++) {
            memory.rememberActivity(npc, "Task " + index);
        }

        assertEquals(20, memory.recentActivities(npc).size());
        assertEquals("Task 6", memory.recentActivities(npc).getFirst());
        assertEquals("Task 25", memory.recentActivities(npc).getLast());
    }

    @Test
    void sharedConversationCombinesPlayersAndKeepsTwentyMessages() {
        AiMemoryStore memory = new AiMemoryStore();
        UUID npc = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        for (int index = 1; index <= 21; index++) {
            memory.rememberMessage(npc, index % 2 == 0 ? firstPlayer : secondPlayer,
                    "Player message " + index, true);
        }

        assertEquals(20, memory.recentSharedConversation(npc).size());
        assertEquals("Player message 2", memory.recentSharedConversation(npc).getFirst());
        assertEquals("Player message 21", memory.recentSharedConversation(npc).getLast());
    }
}
