package com.FishHelper;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import static com.FishHelper.ThunderSequence.Item.*;
import static org.junit.jupiter.api.Assertions.*;

class ThunderSequenceTest {
    private static final class Controls implements ThunderSequence.Controls {
        final EnumSet<ThunderSequence.Item> items = EnumSet.allOf(ThunderSequence.Item.class);
        final List<ThunderSequence.Item> used = new ArrayList<>();
        boolean found = true, alive = true, nearby = true;
        ThunderSequence.Item selected;
        int targetChecks;
        public boolean findThunder() { return found; }
        public boolean hasThunderTarget() { targetChecks++; return alive; }
        public boolean select(ThunderSequence.Item item) {
            if (!items.contains(item)) return false;
            selected = item;
            return true;
        }
        public boolean use(ThunderSequence.Item item) {
            assertEquals(selected, item, "Select the correct slot before using an item");
            if (!items.contains(item)) return false;
            used.add(item);
            return true;
        }
        public boolean hasLivingThunder() { return alive; }
        public boolean hasThunderInAttackRange() { return nearby; }
    }

    private static void ticks(ThunderSequence sequence, Controls controls, int count) {
        for (int i = 0; i < count; i++) sequence.tick(controls);
    }

    private static void untilHyperion(ThunderSequence sequence, Controls controls) {
        for (int i = 0; i < 100 && !controls.used.contains(HYPERION); i++) sequence.tick(controls);
        assertTrue(controls.used.contains(HYPERION), "Hyperion should start within 100 ticks");
    }

    @Test void wandsFireOnceInOrderBeforeRepeatedHyperion() {
        var s = new ThunderSequence(); var c = new Controls();
        ticks(s, c, 40);
        assertEquals(List.of(ICE_SPRAY, INK_WAND), c.used.subList(0, 2));
        assertTrue(c.used.subList(2, c.used.size()).stream().allMatch(i -> i == HYPERION));
        assertTrue(c.used.size() > 4);
        assertEquals(4, c.targetChecks);
    }

    @Test void eachMissingWandIsSkippedIndependently() {
        for (var missing : List.of(ICE_SPRAY, INK_WAND)) {
            var s = new ThunderSequence(); var c = new Controls();
            c.items.remove(missing);
            ticks(s, c, 30);
            assertFalse(c.used.contains(missing));
            assertEquals(missing == ICE_SPRAY ? INK_WAND : ICE_SPRAY, c.used.getFirst());
            assertTrue(c.used.contains(HYPERION));
        }
    }

    @Test void noWandsStillUsesHyperion() {
        var s = new ThunderSequence(); var c = new Controls();
        c.items.retainAll(EnumSet.of(HYPERION));
        ticks(s, c, 30);
        assertFalse(c.used.isEmpty());
        assertTrue(c.used.stream().allMatch(i -> i == HYPERION));
    }

    @Test void missingHyperionWaitsAfterWandsAndResumesWhenAvailable() {
        var s = new ThunderSequence(); var c = new Controls();
        c.items.remove(HYPERION);
        ticks(s, c, 30);
        assertEquals(List.of(ICE_SPRAY, INK_WAND), c.used);
        assertTrue(s.active());
        assertFalse(s.aborted());
        c.items.add(HYPERION); ticks(s, c, 20);
        assertEquals(1, c.used.stream().filter(i -> i == ICE_SPRAY).count());
        assertTrue(c.used.contains(HYPERION));
    }

    @Test void missingSpawnTimesOutWithoutUsingAnything() {
        var s = new ThunderSequence(); var c = new Controls(); c.found = false;
        ticks(s, c, 59); assertTrue(s.active());
        s.tick(c); assertFalse(s.active()); assertTrue(c.used.isEmpty());
    }

    @Test void delayedEntitySpawnIsAccepted() {
        var s = new ThunderSequence(); var c = new Controls(); c.found = false;
        ticks(s, c, 40); c.found = true; ticks(s, c, 30);
        assertEquals(ICE_SPRAY, c.used.getFirst());
    }

    @Test void targetLossBeforeWandUseStopsSequence() {
        var s = new ThunderSequence(); var c = new Controls();
        ticks(s, c, 2); c.alive = false; ticks(s, c, 20);
        assertTrue(c.used.isEmpty()); assertFalse(s.active());
    }

    @Test void leavingRangePausesAndReentryResumesAtFiveCps() {
        var s = new ThunderSequence(); var c = new Controls();
        untilHyperion(s, c);
        int count = c.used.size(); c.nearby = false; s.tick(c);
        assertTrue(s.active()); ticks(s, c, 200); assertEquals(count, c.used.size());
        c.nearby = true; s.tick(c); assertEquals(count + 1, c.used.size());
        ticks(s, c, 3); assertEquals(count + 1, c.used.size());
        s.tick(c); assertEquals(count + 2, c.used.size());
        c.alive = false; s.tick(c); assertFalse(s.active()); assertFalse(s.aborted());
    }

    @Test void guardiansOutsideRadiusPreventHyperion() {
        var s = new ThunderSequence(); var c = new Controls(); c.nearby = false;
        ticks(s, c, 30); assertEquals(List.of(ICE_SPRAY, INK_WAND), c.used);
        assertTrue(ThunderSequence.inAttackRange(5.99 * 5.99));
        assertTrue(ThunderSequence.inAttackRange(36.0));
        assertFalse(ThunderSequence.inAttackRange(6.01 * 6.01));
        assertTrue(ThunderSequence.inAttackRange(4 * 4 + 4 * 4));
        assertFalse(ThunderSequence.inAttackRange(4 * 4 + 4 * 4 + 4 * 4));
        assertFalse(ThunderSequence.inAttackRange(Double.NaN));
        assertFalse(ThunderSequence.inAttackRange(-1));
    }

    @Test void movingAnItemAfterSelectingItDoesNotUseAnotherStack() {
        var s = new ThunderSequence(); var c = new Controls();
        ticks(s, c, 2); c.items.remove(ICE_SPRAY); ticks(s, c, 20);
        assertFalse(c.used.contains(ICE_SPRAY)); assertTrue(c.used.contains(INK_WAND));
    }

    @Test void cancelPreventsAllFurtherInput() {
        var s = new ThunderSequence(); var c = new Controls();
        ticks(s, c, 2); s.cancel(); ticks(s, c, 100);
        assertTrue(c.used.isEmpty()); assertFalse(s.active());
    }

    @Test void interruptedWandUseReselectsBeforeProceeding() {
        var sequence = new ThunderSequence();
        List<ThunderSequence.Item> used = new ArrayList<>();
        var controls = new ThunderSequence.Controls() {
            ThunderSequence.Item selected;
            boolean interrupted;
            public boolean findThunder() { return true; }
            public boolean hasThunderTarget() { return true; }
            public boolean hasLivingThunder() { return true; }
            public boolean hasThunderInAttackRange() { return true; }
            public boolean select(ThunderSequence.Item item) { selected = item; return true; }
            public boolean use(ThunderSequence.Item item) {
                if (!interrupted) { interrupted = true; selected = null; return false; }
                assertEquals(selected, item); used.add(item); return true;
            }
        };
        for (int i=0;i<40;i++) sequence.tick(controls);
        assertEquals(List.of(ICE_SPRAY, INK_WAND), used.subList(0, 2));
    }

    @Test void hyperionClicksAreFourTicksApart() {
        var s = new ThunderSequence(); var c = new Controls();
        untilHyperion(s, c);
        int count = c.used.size(); ticks(s, c, 3); assertEquals(count, c.used.size());
        s.tick(c); assertEquals(count + 1, c.used.size());
    }
}
