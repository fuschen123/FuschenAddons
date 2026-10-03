package com.FishHelper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HookRecoverySequenceTest {
    static class Controls extends RecastSequenceTest.Controls implements HookRecoverySequence.Controls {
        boolean hyperion=true; int weaponClicks; boolean selected;
        public boolean rodAvailable() {return rod;}
        public boolean selectHyperion() {selected=hyperion; return hyperion;}
        public boolean useHyperion() {assertTrue(selected); if(!hyperion)return false; weaponClicks++; return true;}
    }
    void ticks(HookRecoverySequence s,Controls c,int count) {for(int i=0;i<count;i++)s.tick(c);}
    @Test void hyperionRunsOnceThenUsesTheSharedAcknowledgedRecast() {
        var c=new Controls(); var s=new HookRecoverySequence(true,c.original,3); ticks(s,c,70);
        assertEquals(1,c.weaponClicks); assertEquals(1,c.reels); assertEquals(0,c.casts);
        c.hook=null; ticks(s,c,5); assertEquals(1,c.casts); c.hook=UUID.randomUUID(); s.tick(c); assertFalse(s.active());
    }
    @Test void missingRodAndMissingHyperionHaveDifferentRetryableProblems() {
        var c=new Controls(); c.rod=false; var s=new HookRecoverySequence(true,c.original,3); s.tick(c);
        assertEquals(RecastSequence.Problem.MISSING_ROD,s.problem());
        c.rod=true; c.hyperion=false; s=new HookRecoverySequence(true,c.original,3); s.tick(c);
        assertEquals(RecastSequence.Problem.MISSING_HYPERION,s.problem()); assertEquals(0,c.weaponClicks);
        c.hyperion=true; s=new HookRecoverySequence(true,c.original,3); ticks(s,c,10); assertEquals(1,c.weaponClicks);
    }
    @Test void consumedMobRetriesOnlyRodRecoveryAndNeverAnotherHyperion() {
        var guard=new HookEncounterGuard(); var c=new Controls(); var mob=UUID.randomUUID();
        assertFalse(guard.wasUsed(c.original,mob)); guard.used(c.original,mob);
        assertTrue(guard.wasUsed(c.original,mob)); assertTrue(guard.wasUsed(UUID.randomUUID(),mob));
        var s=new HookRecoverySequence(!guard.wasUsed(c.original,mob),c.original,3); ticks(s,c,10);
        assertEquals(0,c.weaponClicks); assertEquals(1,c.reels);
    }
    @Test void missingOrMovedItemsAfterSelectionNeverUseAnotherStack() {
        var c=new Controls(); var s=new HookRecoverySequence(true,c.original,3); s.tick(c); c.hyperion=false; ticks(s,c,10);
        assertEquals(0,c.weaponClicks); assertEquals(RecastSequence.Problem.MISSING_HYPERION,s.problem());
    }
    @Test void cancelCannotClickOrRecastLater() {
        var c=new Controls(); var s=new HookRecoverySequence(true,c.original,3); s.tick(c); s.cancel(); ticks(s,c,200);
        assertEquals(0,c.weaponClicks); assertEquals(0,c.reels); assertEquals(0,c.casts);
    }
    @Test void rodRemovedDuringWeaponDelayIsReportedAsMissingRod() {
        var c=new Controls(); var s=new HookRecoverySequence(true,c.original,3); s.tick(c); c.rod=false; ticks(s,c,10);
        assertEquals(RecastSequence.Problem.MISSING_ROD,s.problem()); assertEquals(0,c.weaponClicks);
    }

    @Test void hyperionIsUsedExactlyFourToSixTicksAfterHookHandling() {
        for (int delay = 3; delay <= 5; delay++) {
            var c = new Controls();
            var s = new HookRecoverySequence(true, c.original, delay);
            for (int tick = 1; tick <= delay; tick++) {
                s.tick(c);
                assertEquals(0, c.weaponClicks, "Hyperion must not fire before tick " + (delay + 1));
            }
            s.tick(c);
            assertEquals(1, c.weaponClicks, "Hyperion should fire at tick " + (delay + 1));
        }
    }
}
