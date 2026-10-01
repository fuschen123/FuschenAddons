package com.FishHelper;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static com.FishHelper.JawbusPause.*;
import static org.junit.jupiter.api.Assertions.*;

class JawbusPauseTest {
    private final JawbusPause pause = new JawbusPause();
    private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    private Observation alive(UUID id, boolean nearby) { return new Observation(id, id.hashCode(), Presence.ALIVE, nearby); }
    private Observation dead(UUID id) { return new Observation(id, id.hashCode(), Presence.DEAD, false); }
    private void absent(int count) { for (int i = 0; i < count; i++) pause.tick(List.of()); }

    @Test void ownSpawnPausesImmediatelyAndWaitsForDelayedEntity() {
        assertEquals(Change.STARTED, pause.spawned());
        absent(SPAWN_WAIT_TICKS - 1);
        assertTrue(pause.active());
        assertEquals(Change.NONE, pause.tick(List.of(alive(first, true))));
        for (int i = 0; i < 500; i++) pause.tick(List.of(alive(first, false)));
        assertTrue(pause.active());
        assertEquals(Change.ENDED, pause.tick(List.of(dead(first))));
    }

    @Test void noEntityAfterCompleteSpawnWindowEndsTheWaitOnce() {
        pause.spawned(); absent(SPAWN_WAIT_TICKS - 1);
        assertEquals(Change.ENDED, pause.tick(List.of()));
        assertEquals(Change.NONE, pause.tick(List.of()));
    }

    @Test void nearbyRecognitionPausesButUnrelatedDistantMobDoesNot() {
        assertEquals(Change.NONE, pause.tick(List.of(alive(first, false))));
        assertEquals(Change.STARTED, pause.tick(List.of(alive(first, true))));
        assertEquals(Change.NONE, pause.tick(List.of(alive(first, false))));
        assertTrue(pause.active());
    }

    @Test void briefEntityLossAndReturnDoesNotResumeOrAnnounceAgain() {
        pause.tick(List.of(alive(first, true)));
        absent(ABSENCE_TICKS - 1);
        assertTrue(pause.active());
        assertEquals(Change.NONE, pause.tick(List.of(alive(first, false))));
        absent(ABSENCE_TICKS - 1);
        assertTrue(pause.active());
        assertEquals(Change.ENDED, pause.tick(List.of()));
    }

    @Test void noLivingEntityIsExpiredByAnEncounterDurationOrRangeTimeout() {
        pause.tick(List.of(alive(first, true)));
        for (int i = 0; i < 10_000; i++) assertEquals(Change.NONE, pause.tick(List.of(alive(first, false))));
        assertTrue(pause.active());
    }

    @Test void multipleJawbusKeepPauseUntilLastDeathOrConfirmedAbsence() {
        pause.tick(List.of(alive(first, true), alive(second, true)));
        assertEquals(Change.NONE, pause.tick(List.of(dead(first), alive(second, false))));
        absent(ABSENCE_TICKS - 1);
        assertTrue(pause.active());
        assertEquals(Change.ENDED, pause.tick(List.of()));
    }

    @Test void anotherSpawnDuringCombatCannotBeSatisfiedByTheOldMob() {
        pause.tick(List.of(alive(first, true)));
        assertEquals(Change.NONE, pause.spawned());
        assertEquals(Change.NONE, pause.tick(List.of(dead(first))));
        absent(80);
        assertTrue(pause.active());
        pause.tick(List.of(alive(second, true)));
        assertEquals(Change.ENDED, pause.tick(List.of(dead(second))));
    }

    @Test void twoPendingSpawnsRequireTwoDistinctEntities() {
        assertEquals(Change.STARTED, pause.spawned());
        assertEquals(Change.NONE, pause.spawned());
        pause.tick(List.of(alive(first, true), alive(first, true)));
        pause.tick(List.of(dead(first)));
        assertTrue(pause.active());
        pause.tick(List.of(alive(second, true)));
        assertEquals(Change.ENDED, pause.tick(List.of(dead(second))));
    }

    @Test void resetDiscardsAllWorldReferencesAndProducesNoDelayedEnd() {
        pause.spawned(); pause.spawned(); pause.tick(List.of(alive(first, true)));
        pause.reset();
        assertFalse(pause.active()); assertTrue(pause.targets().isEmpty());
        for (int i = 0; i < 500; i++) assertEquals(Change.NONE, pause.tick(List.of()));
    }

    @Test void deadNearbyMobCannotStartAPause() {
        assertEquals(Change.NONE, pause.tick(List.of(new Observation(first, 1, Presence.DEAD, true))));
        assertFalse(pause.active());
    }

    @Test void uuidSurvivesReloadWithADifferentEntityId() {
        pause.tick(List.of(alive(first, true))); absent(80);
        assertEquals(Change.NONE, pause.tick(List.of(new Observation(first, 42, Presence.ALIVE, false))));
        assertEquals(42, pause.targets().getFirst().entityId());
        assertTrue(pause.active());
    }
}
