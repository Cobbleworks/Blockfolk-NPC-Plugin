package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NpcConversationTrackerTest {

    private static final UUID COWBOY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BANDIT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BARKEEP = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static NpcConversationTracker tracker(int maxReplies) {
        return new NpcConversationTracker(new NpcConversationSettings(maxReplies, 120, 60, 2.0, 12.0, 32.0));
    }

    @Test
    void backAndForthStopsAtTheReplyCap() {
        NpcConversationTracker tracker = tracker(3);
        long now = 1_000L;

        assertEquals(1, tracker.claimReply(COWBOY, List.of(BANDIT), now).orElseThrow().reply());
        assertEquals(2, tracker.claimReply(BANDIT, List.of(COWBOY), now + 1).orElseThrow().reply());
        NpcConversationTracker.Claim last = tracker.claimReply(COWBOY, List.of(BANDIT), now + 2).orElseThrow();
        assertEquals(3, last.reply());
        assertTrue(last.lastReply());

        assertTrue(tracker.claimReply(BANDIT, List.of(COWBOY), now + 3).isEmpty());
        assertFalse(tracker.active(COWBOY));
        assertFalse(tracker.active(BANDIT));
    }

    @Test
    void endedConversationPutsThePairOnCooldown() {
        NpcConversationTracker tracker = tracker(1);
        long now = 1_000L;
        tracker.claimReply(COWBOY, List.of(BANDIT), now);
        tracker.claimReply(BANDIT, List.of(COWBOY), now + 1);

        assertTrue(tracker.claimReply(COWBOY, List.of(BANDIT), now + 2).isEmpty());
        assertTrue(tracker.onCooldown(COWBOY, BANDIT, now + 2));
        assertFalse(tracker.claimEncounter(BANDIT, COWBOY, now + 2));
        assertTrue(tracker.claimReply(COWBOY, List.of(BANDIT), now + 61_000L).isPresent());
    }

    @Test
    void speakerNeverRespondsToItself() {
        NpcConversationTracker tracker = tracker(6);

        assertTrue(tracker.claimReply(COWBOY, List.of(COWBOY), 0L).isEmpty());
    }

    @Test
    void listenersJoinAndAllRespondAsAGroup() {
        NpcConversationTracker tracker = tracker(6);
        NpcConversationTracker.Claim first = tracker.claimReply(COWBOY, List.of(BANDIT, BARKEEP), 0L).orElseThrow();

        assertEquals(List.of(BANDIT, BARKEEP), first.listeners());
        assertEquals(Set.of(COWBOY, BANDIT, BARKEEP), tracker.participants(first.conversationKey()));
    }

    @Test
    void npcBusyInAnotherConversationDoesNotRespond() {
        NpcConversationTracker tracker = tracker(6);
        UUID stranger = UUID.randomUUID();
        tracker.claimReply(COWBOY, List.of(BANDIT), 0L);

        assertTrue(tracker.claimReply(BARKEEP, List.of(stranger), 1L).isPresent());
        // The barkeep's conversation with the stranger keeps it out of the cowboy's.
        NpcConversationTracker.Claim next = tracker.claimReply(BANDIT, List.of(COWBOY, BARKEEP), 2L).orElseThrow();
        assertEquals(List.of(COWBOY), next.listeners());
    }

    @Test
    void newcomerSpeakingToBusyNpcsJoinsTheirConversation() {
        NpcConversationTracker tracker = tracker(6);
        UUID key = tracker.claimReply(COWBOY, List.of(BANDIT), 0L).orElseThrow().conversationKey();

        NpcConversationTracker.Claim joined = tracker.claimReply(BARKEEP, List.of(COWBOY, BANDIT), 1L).orElseThrow();
        assertEquals(key, joined.conversationKey());
        assertEquals(2, joined.reply());
        assertTrue(tracker.participants(key).contains(BARKEEP));
    }

    @Test
    void quietOrLongConversationsExpire() {
        NpcConversationTracker tracker = tracker(100);
        UUID key = tracker.claimReply(COWBOY, List.of(BANDIT), 0L).orElseThrow().conversationKey();

        assertEquals(List.of(key), tracker.expire(NpcConversationTracker.IDLE_TIMEOUT_MILLIS));
        assertFalse(tracker.active(COWBOY));

        NpcConversationTracker longTalk = new NpcConversationTracker(
                new NpcConversationSettings(100, 10, 0, 2.0, 12.0, 32.0));
        longTalk.claimReply(COWBOY, List.of(BANDIT), 0L);
        assertEquals(2, longTalk.claimReply(BANDIT, List.of(COWBOY), 5_000L).orElseThrow().reply());
        // The 10-second limit ends it even though lines keep coming; with no
        // cooldown configured, the next line starts a fresh conversation.
        assertEquals(1, longTalk.claimReply(COWBOY, List.of(BANDIT), 10_000L).orElseThrow().reply());
    }

    @Test
    void encounterLocksThePairInBothDirections() {
        NpcConversationTracker tracker = tracker(6);

        assertTrue(tracker.claimEncounter(COWBOY, BANDIT, 0L));
        assertFalse(tracker.claimEncounter(BANDIT, COWBOY, 0L));
        assertTrue(tracker.claimEncounter(COWBOY, BARKEEP, 0L));
        assertTrue(tracker.claimEncounter(BANDIT, COWBOY, 60_000L));
    }

    @Test
    void interruptEndsTheConversationWithCooldown() {
        NpcConversationTracker tracker = tracker(6);
        UUID key = tracker.claimReply(COWBOY, List.of(BANDIT), 0L).orElseThrow().conversationKey();

        assertEquals(Optional.of(key), tracker.interrupt(BANDIT, 1L));
        assertFalse(tracker.active(COWBOY));
        assertTrue(tracker.claimReply(COWBOY, List.of(BANDIT), 2L).isEmpty());
    }

    @Test
    void despawnedNpcLeavesAndAConversationOfOneEnds() {
        NpcConversationTracker tracker = tracker(6);
        UUID key = tracker.claimReply(COWBOY, List.of(BANDIT), 0L).orElseThrow().conversationKey();

        assertEquals(Optional.of(key), tracker.forget(BANDIT, 1L));
        assertFalse(tracker.active(COWBOY));
    }

    @Test
    void zeroRepliesDisablesNpcResponses() {
        assertTrue(tracker(0).claimReply(COWBOY, List.of(BANDIT), 0L).isEmpty());
    }

    @Test
    void sameGroupGetsTheSameConversationKey() {
        assertEquals(NpcConversationTracker.conversationKey(List.of(COWBOY, BANDIT)),
                NpcConversationTracker.conversationKey(List.of(BANDIT, COWBOY)));
        assertFalse(NpcConversationTracker.conversationKey(List.of(COWBOY, BANDIT))
                .equals(NpcConversationTracker.conversationKey(List.of(COWBOY, BARKEEP))));
    }

    @Test
    void settingsClampInvalidValues() {
        NpcConversationSettings settings = new NpcConversationSettings(-1, 0, -5, Double.NaN, 0.0, -1.0);

        assertFalse(settings.enabled());
        assertEquals(1, settings.maxDurationSeconds());
        assertEquals(0, settings.cooldownSeconds());
        assertEquals(NpcConversationSettings.DEFAULT_REPLY_DELAY_SECONDS, settings.replyDelaySeconds());
        assertEquals(1.0, settings.hearingRange());
        assertEquals(1.0, settings.playerRange());
    }
}
