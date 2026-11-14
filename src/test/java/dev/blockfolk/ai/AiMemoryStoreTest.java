package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void idleReviewClaimsShortConversationAndOnlyNotifiesNewParticipants() {
        AiMemoryStore memory = new AiMemoryStore(2);
        UUID npc = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        UUID sam = UUID.randomUUID();
        for (int index = 1; index <= 9; index++)
            memory.rememberMessage(npc, index % 2 == 0 ? sam : alex, true, "line " + index);

        assertNull(memory.claimDreamBatch(npc, alex, true));
        AiMemoryStore.DreamBatch first = memory.claimDreamBatch(npc, alex, true, true);
        assertEquals(9, first.lines().size());
        assertEquals(java.util.Set.of(alex, sam), first.participants());
        memory.completeDreamBatch(first);

        memory.rememberMessage(npc, alex, true, "line 10");
        AiMemoryStore.DreamBatch second = memory.claimDreamBatch(npc, alex, true, true);
        assertEquals(List.of("line 8", "line 9", "line 10"), second.lines());
        assertEquals(java.util.Set.of(alex), second.participants());
    }

    @Test
    void eventsCarryTheirAgeAndExpireAfterFiveMinutes() {
        MutableClock clock = new MutableClock();
        AiMemoryStore memory = new AiMemoryStore(AiMemoryStore.DEFAULT_MAX_MESSAGES, clock);
        UUID npc = UUID.randomUUID();

        memory.rememberEvent(npc, "You killed a zombie.");
        clock.advance(java.time.Duration.ofSeconds(40));
        memory.rememberEvent(npc, "You picked up 2 rotten flesh from the ground.");

        assertEquals(
                List.of("(40s ago) You killed a zombie.", "(just now) You picked up 2 rotten flesh from the ground."),
                memory.recentEvents(npc));

        clock.advance(java.time.Duration.ofMinutes(4).plusSeconds(30));
        assertEquals(List.of("(4m 30s ago) You picked up 2 rotten flesh from the ground."), memory.recentEvents(npc));
    }

    @Test
    void eventAgeIsReadable() {
        assertEquals("just now", AiMemoryStore.age(java.time.Duration.ofSeconds(4)));
        assertEquals("12s ago", AiMemoryStore.age(java.time.Duration.ofSeconds(12)));
        assertEquals("2m ago", AiMemoryStore.age(java.time.Duration.ofMinutes(2)));
        assertEquals("1m 5s ago", AiMemoryStore.age(java.time.Duration.ofSeconds(65)));
    }

    @Test
    void repeatedEventsFromOneSourceMergeIntoTheLatest() {
        MutableClock clock = new MutableClock();
        AiMemoryStore memory = new AiMemoryStore(AiMemoryStore.DEFAULT_MAX_MESSAGES, clock);
        UUID npc = UUID.randomUUID();

        memory.rememberEvent(npc, "DAMAGE_TAKEN:zombie", "Took 2 damage. Health: 18.");
        clock.advance(java.time.Duration.ofSeconds(2));
        memory.rememberEvent(npc, null, "You attacked a zombie.");
        clock.advance(java.time.Duration.ofSeconds(2));
        memory.rememberEvent(npc, "DAMAGE_TAKEN:zombie", "Took 3 damage. Health: 15.");

        assertEquals(List.of("(just now) You attacked a zombie.",
                "(just now) Took 3 damage. Health: 15. [2 times; latest shown]"), memory.recentEvents(npc));
    }

    @Test
    void eventsOutsideTheMergeWindowStaySeparate() {
        MutableClock clock = new MutableClock();
        AiMemoryStore memory = new AiMemoryStore(AiMemoryStore.DEFAULT_MAX_MESSAGES, clock);
        UUID npc = UUID.randomUUID();

        memory.rememberEvent(npc, "PLAYER_APPROACH:alex", "Alex approached.");
        clock.advance(java.time.Duration.ofSeconds(31));
        memory.rememberEvent(npc, "PLAYER_APPROACH:alex", "Alex approached.");

        assertEquals(List.of("(31s ago) Alex approached.", "(just now) Alex approached."), memory.recentEvents(npc));
    }

    @Test
    void unkeyedEventsNeverMerge() {
        AiMemoryStore memory = new AiMemoryStore();
        UUID npc = UUID.randomUUID();

        memory.rememberEvent(npc, "You killed a zombie.");
        memory.rememberEvent(npc, "You killed a zombie.");

        assertEquals(2, memory.recentEvents(npc).size());
    }

    private static final class MutableClock extends java.time.Clock {
        private java.time.Instant now = java.time.Instant.parse("2026-01-01T12:00:00Z");

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }
}
