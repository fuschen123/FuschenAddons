package com.FishHelper;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecastSequenceTest {
    static class Controls implements RecastSequence.Controls {
        final UUID original = UUID.randomUUID();
        UUID hook = original;
        boolean rod = true, settled = true;
        Set<UUID> reeled = new HashSet<>();
        int reels, casts;
        public boolean selectRod() { return rod; }
        public UUID currentHook() { return hook; }
        public boolean hookReferenceSettled() { return settled; }
        public boolean alreadyReeled(UUID id) { return reeled.contains(id); }
        public boolean reel(UUID id) { if (!rod) return false; reels++; reeled.add(id); return true; }
        public boolean cast() { assertNull(hook); assertTrue(settled); casts++; return rod; }
    }
    static void ticks(RecastSequence s, Controls c, int n) { for (int i = 0; i < n; i++) s.tick(c); }
    @Test void delayedRemovalWaitsWithoutSecondReelAndSpawnMustBeConfirmed() {
        var c = new Controls(); var s = new RecastSequence(c.original);
        ticks(s, c, 80); assertEquals(1, c.reels); assertEquals(0, c.casts); assertTrue(s.active());
        c.hook = null; ticks(s, c, 5); assertEquals(1, c.casts); assertTrue(s.active());
        c.hook = UUID.randomUUID(); s.tick(c); assertFalse(s.active()); assertEquals(RecastSequence.Problem.NONE, s.problem());
    }
    @Test void stalePlayerPointerBlocksCastUntilReconciled() {
        var c = new Controls(); c.hook = null; c.settled = false; var s = new RecastSequence(c.original);
        ticks(s, c, 60); assertEquals(0, c.casts);
        c.settled = true; ticks(s, c, 5); assertEquals(1, c.casts);
    }
    @Test void stubbornHookTimeoutCanBeRetriedWithoutAnAlternatingReelCastLoop() {
        var c = new Controls(); var s = new RecastSequence(c.original); ticks(s, c, 150);
        assertEquals(RecastSequence.Problem.HOOK_RELEASE, s.problem());
        var retry = new RecastSequence(c.original); ticks(retry, c, 70);
        assertEquals(1, c.reels); assertEquals(0, c.casts);
        c.hook = null; ticks(retry, c, 5); assertEquals(1, c.casts);
        c.hook = UUID.randomUUID(); retry.tick(c); assertFalse(retry.active());
    }
    @Test void alreadyReeledNormalCatchDoesNotReelTwice() {
        var c = new Controls(); c.reeled.add(c.original); var s = new RecastSequence(c.original);
        ticks(s,c,10); assertEquals(0,c.reels); c.hook = null; ticks(s,c,5); assertEquals(1,c.casts);
    }
    @Test void missingRodReportsSpecificProblemAndNextAttemptCanResume() {
        var c = new Controls(); c.rod = false; var s = new RecastSequence(c.original); s.tick(c);
        assertEquals(RecastSequence.Problem.MISSING_ROD,s.problem()); assertEquals(0,c.reels);
        c.rod = true; var retry = new RecastSequence(c.original); ticks(retry,c,4); assertEquals(1,c.reels);
    }
    @Test void aNewHookIsNeverReeledByAnOldAction() {
        var c = new Controls(); var s = new RecastSequence(c.original); c.hook = UUID.randomUUID(); s.tick(c);
        assertFalse(s.active()); assertEquals(0,c.reels); assertEquals(0,c.casts);
    }
    @Test void emptyHookGetsOneCastAndNeverRetriesWithinTheSameAttempt() {
        var c = new Controls(); c.hook = null; var s = new RecastSequence(null); ticks(s,c,500);
        assertEquals(0,c.reels); assertEquals(1,c.casts); assertEquals(RecastSequence.Problem.CAST_CONFIRMATION,s.problem());
    }
    @Test void cancellationSuppressesAllFurtherInputs() {
        var c = new Controls(); var s = new RecastSequence(c.original); s.tick(c); s.cancel(); ticks(s,c,200);
        assertEquals(0,c.reels); assertEquals(0,c.casts);
    }
    @Test void jawbusCancellationAfterReelingCannotCastWhenTheServerLaterReleasesTheHook() {
        var c = new Controls(); var s = new RecastSequence(c.original); ticks(s,c,4);
        assertEquals(1,c.reels); assertEquals(0,c.casts);
        s.cancel(); c.hook = null; ticks(s,c,500);
        assertEquals(RecastSequence.Problem.CANCELLED,s.problem());
        assertEquals(1,c.reels); assertEquals(0,c.casts);
    }
}
