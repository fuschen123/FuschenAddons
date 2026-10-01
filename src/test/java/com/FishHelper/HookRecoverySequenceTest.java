package com.FishHelper;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HookRecoverySequenceTest {
    static class Controls implements HookRecoverySequence.Controls {
        List<String> actions = new ArrayList<>();
        boolean hyperion = true, rod = true, removeOnReel = true;
        HookRecoverySequence.Hook hook = HookRecoverySequence.Hook.ORIGINAL;
        public boolean selectHyperion() { if (!hyperion || !rod) return false; actions.add("select"); return true; }
        public boolean useHyperion() { if (!hyperion) return false; actions.add("hyperion"); return true; }
        public boolean restoreRod() { if (!rod) return false; actions.add("rod"); return true; }
        public HookRecoverySequence.Hook hook() { return hook; }
        public void useRod() {
            actions.add(hook == HookRecoverySequence.Hook.ORIGINAL ? "reel" : "cast");
            if (removeOnReel) hook = HookRecoverySequence.Hook.NONE;
        }
    }
    void run(HookRecoverySequence sequence, Controls controls) { for (int i = 0; i < 150; i++) sequence.tick(controls); }
    @Test void oneHyperionThenReelThenOneCast() {
        var s = new HookRecoverySequence(); var c = new Controls(); run(s, c);
        assertEquals(List.of("select", "hyperion", "rod", "rod", "reel", "rod", "cast"), c.actions);
        assertFalse(s.active()); assertFalse(s.aborted());
    }
    @Test void weaponSwapRemovedHookSoOnlyCastIsNecessary() {
        var s = new HookRecoverySequence(); var c = new Controls(); c.hook = HookRecoverySequence.Hook.NONE;
        run(s, c); assertFalse(c.actions.contains("reel")); assertEquals(1, Collections.frequency(c.actions, "cast"));
    }
    @Test void stubbornHookTimesOutWithoutRepeatedReelOrCast() {
        var s = new HookRecoverySequence(); var c = new Controls(); c.removeOnReel = false; run(s, c);
        assertTrue(s.aborted()); assertEquals(1, Collections.frequency(c.actions, "hyperion"));
        assertEquals(1, Collections.frequency(c.actions, "reel")); assertFalse(c.actions.contains("cast"));
    }
    @Test void newHookIsLeftAlone() {
        var s = new HookRecoverySequence(); var c = new Controls(); c.hook = HookRecoverySequence.Hook.OTHER;
        run(s, c); assertFalse(c.actions.contains("reel")); assertFalse(c.actions.contains("cast"));
    }
    @Test void missingPrerequisitesNeverProduceInputsAndConsumeGuard() {
        for (boolean missingRod : List.of(false, true)) {
            var s = new HookRecoverySequence(); var c = new Controls(); c.rod = !missingRod; c.hyperion = missingRod;
            run(s, c); assertTrue(s.aborted()); assertTrue(c.actions.isEmpty());
        }
        var guard = new HookEncounterGuard(); var h = UUID.randomUUID(); var m = UUID.randomUUID();
        assertTrue(guard.claim(h, m)); assertFalse(guard.claim(h, m));
        assertFalse(guard.claim(UUID.randomUUID(), m)); assertFalse(guard.claim(h, UUID.randomUUID()));
        guard.clear(); assertTrue(guard.claim(h, m));
    }
    @Test void cancelledSequenceCannotClickOrRecast() {
        var s = new HookRecoverySequence(); var c = new Controls(); s.tick(c); s.cancel(); run(s, c);
        assertEquals(List.of("select"), c.actions);
    }
    @Test void rodDisappearingAfterHyperionAbortsWithoutUse() {
        var s = new HookRecoverySequence(); var c = new Controls();
        for (int i = 0; i < 3; i++) s.tick(c);
        c.rod = false; run(s, c); assertTrue(s.aborted());
        assertEquals(List.of("select", "hyperion"), c.actions);
    }
}
