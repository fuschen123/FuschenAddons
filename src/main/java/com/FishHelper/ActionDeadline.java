package com.FishHelper;

/** A stuck phase has a deadline even if its local retry counter keeps changing. */
public final class ActionDeadline {
    private int phase = -1, age;
    public void reset() { phase = -1; age = 0; }
    public boolean expired(int currentPhase, int limit) {
        if (currentPhase < 0) { reset(); return false; }
        if (phase != currentPhase) { phase = currentPhase; age = 0; }
        return ++age > limit;
    }
}
