package com.FishHelper;

/** Optional one-shot Hyperion action followed by the shared acknowledged recast. */
public final class HookRecoverySequence {
    public interface Controls extends RecastSequence.Controls {
        boolean rodAvailable();
        boolean selectHyperion();
        boolean useHyperion();
    }
    private int stage, delay;
    private final RecastSequence recast;
    private final int hyperionDelay;
    private RecastSequence.Problem problem = RecastSequence.Problem.NONE;
    public HookRecoverySequence(boolean hyperion, java.util.UUID hook, int hyperionDelay) {
        stage = hyperion ? 0 : 2;
        this.hyperionDelay = Math.max(3, Math.min(5, hyperionDelay));
        recast = new RecastSequence(hook);
    }
    public boolean active() { return stage < 3; }
    public boolean aborted() { return problem != RecastSequence.Problem.NONE; }
    public RecastSequence.Problem problem() { return problem; }
    public void cancel() { problem = RecastSequence.Problem.CANCELLED; stage = 3; }
    public void tick(Controls c) {
        if (!active() || delay > 0 && --delay > 0) return;
        if (stage == 0) {
            if (!c.selectRod()) { problem = RecastSequence.Problem.MISSING_ROD; stage = 3; return; }
            if (!c.selectHyperion()) { problem = RecastSequence.Problem.MISSING_HYPERION; stage = 3; return; }
            // The setup tick plus this delay yields a use exactly 4–6 ticks
            // after the hook is first handled (hyperionDelay is 3–5).
            stage = 1; delay = hyperionDelay;
        } else if (stage == 1) {
            if (!c.rodAvailable()) { problem = RecastSequence.Problem.MISSING_ROD; stage = 3; return; }
            if (!c.useHyperion()) { problem = RecastSequence.Problem.MISSING_HYPERION; stage = 3; return; }
            stage = 2; delay = 2;
        } else {
            recast.tick(c);
            if (!recast.active()) { problem = recast.problem(); stage = 3; }
        }
    }
}
