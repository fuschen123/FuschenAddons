package com.FishHelper;

import java.util.Objects;

/** Counts actionable idle time. Exempt waits freeze it; failed attempts only impose backoff. */
public final class FishingWatchdog {
    private int idle, retry;
    private boolean requested;
    private Object observed;
    public void reset() { idle = retry = 0; requested = false; observed = null; }
    public boolean observe(Object progress) {
        if (Objects.equals(observed, progress)) return false;
        observed = progress; completed(); return true;
    }
    public void completed() { idle = retry = 0; requested = false; }
    public void interrupted() { requested = false; }
    public void failed() { retry = 100; requested = false; }
    public boolean tick(boolean enabled, boolean expectedWait) {
        if (!enabled) return false;
        if (retry > 0) { retry--; return false; }
        if (expectedWait || requested) return false;
        if (++idle < 100) return false;
        requested = true;
        return true;
    }
}
