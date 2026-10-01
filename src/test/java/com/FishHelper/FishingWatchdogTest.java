package com.FishHelper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FishingWatchdogTest {
    @Test void aRecastPreemptedByThunderDoesNotLeaveAnUnserviceableRequest() {
        var w = new FishingWatchdog(); for (int i=0;i<100;i++) w.tick(true,false);
        w.interrupted();
        // Cancelling ownership is not progress, so the already-stalled flow can recover.
        assertTrue(w.tick(true,false));
    }
    @Test void trueIdleTriggersOnceAtFiveSeconds() {
        var w = new FishingWatchdog(); for (int i=0;i<99;i++) assertFalse(w.tick(true,false));
        assertTrue(w.tick(true,false)); for (int i=0;i<500;i++) assertFalse(w.tick(true,false));
    }
    @Test void normalBiteCountdownSlugfishAndTemporaryWaitsDoNotCountAsIdle() {
        var w = new FishingWatchdog(); for (int i=0;i<1000;i++) assertFalse(w.tick(true,true));
        for (int i=0;i<99;i++) assertFalse(w.tick(true,false)); assertTrue(w.tick(true,false));
    }
    @Test void repeatedFailedAttemptsAreBackedOffButDoNotClaimProgress() {
        var w = new FishingWatchdog(); for(int i=0;i<100;i++) w.tick(true,false);
        w.failed(); for(int i=0;i<100;i++) assertFalse(w.tick(true,false));
        assertTrue(w.tick(true,false));
    }
    @Test void actualProgressAndCompletedRecastsStartANewFullWindow() {
        var w = new FishingWatchdog(); for(int i=0;i<99;i++) w.tick(true,false);
        assertTrue(w.observe("new hook")); assertFalse(w.observe("new hook"));
        for(int i=0;i<99;i++) assertFalse(w.tick(true,false)); assertTrue(w.tick(true,false));
        w.completed(); for(int i=0;i<99;i++) assertFalse(w.tick(true,false)); assertTrue(w.tick(true,false));
    }
    @Test void manualOffAndWorldResetCannotReactivateOrRecast() {
        var w = new FishingWatchdog(); for(int i=0;i<100;i++) w.tick(true,false);
        w.reset(); for(int i=0;i<1000;i++) assertFalse(w.tick(false,false));
    }
    @Test void stateTimeoutIgnoresRetryCountersButRespectsRealPhaseTransitions() {
        var d = new ActionDeadline(); for(int i=0;i<100;i++) assertFalse(d.expired(6,100));
        assertTrue(d.expired(6,100)); assertFalse(d.expired(3,100));
        d.reset(); assertFalse(d.expired(6,100)); assertFalse(d.expired(-1,100));
    }
}
