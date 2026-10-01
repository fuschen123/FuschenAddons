package com.FishHelper;

import java.util.UUID;

/** Acknowledged reel -> release -> cast -> spawn; issuing a click is not success. */
public final class RecastSequence {
    public enum Problem { NONE, MISSING_ROD, MISSING_HYPERION, HOOK_RELEASE, CAST_CONFIRMATION, CANCELLED }
    public interface Controls {
        boolean selectRod();
        UUID currentHook();
        boolean hookReferenceSettled();
        boolean alreadyReeled(UUID hook);
        boolean reel(UUID hook);
        boolean cast();
    }
    private enum Stage { SELECT, REEL, RELEASE, CAST, SPAWN, DONE }
    private final UUID originalHook;
    private Stage stage = Stage.SELECT;
    private int delay, waiting, absent;
    private Problem problem = Problem.NONE;
    public RecastSequence(UUID originalHook) { this.originalHook = originalHook; }
    public boolean active() { return stage != Stage.DONE; }
    public Problem problem() { return problem; }
    public void cancel() { fail(Problem.CANCELLED); }
    private void fail(Problem reason) { problem = reason; stage = Stage.DONE; }

    public void tick(Controls c) {
        if (!active()) return;
        UUID hook = c.currentHook();
        if (hook != null && !hook.equals(originalHook)) { stage = Stage.DONE; return; }
        if (delay > 0 && --delay > 0) return;
        switch (stage) {
            case SELECT -> {
                if (!c.selectRod()) { fail(Problem.MISSING_ROD); return; }
                stage = Stage.REEL; delay = 2;
            }
            case REEL -> {
                if (!c.selectRod()) { fail(Problem.MISSING_ROD); return; }
                if (hook != null && !c.alreadyReeled(hook) && !c.reel(hook)) {
                    fail(Problem.MISSING_ROD); return;
                }
                stage = Stage.RELEASE;
            }
            case RELEASE -> {
                // A stale player.fishing pointer must be reconciled before rod use.
                if (hook == null && c.hookReferenceSettled()) absent++; else absent = 0;
                if (absent >= 4) { stage = Stage.CAST; return; }
                if (++waiting >= 100) fail(Problem.HOOK_RELEASE);
            }
            case CAST -> {
                if (hook != null || !c.hookReferenceSettled()) { stage = Stage.RELEASE; absent = 0; return; }
                if (!c.selectRod() || !c.cast()) { fail(Problem.MISSING_ROD); return; }
                stage = Stage.SPAWN; waiting = 0;
            }
            case SPAWN -> { if (++waiting >= 100) fail(Problem.CAST_CONFIRMATION); }
            case DONE -> { }
        }
    }
}
