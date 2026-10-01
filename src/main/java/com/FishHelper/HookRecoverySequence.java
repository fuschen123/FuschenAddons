package com.FishHelper;

/** Single-shot input sequence; waits for the original hook to disappear before casting. */
public final class HookRecoverySequence {
    public enum Hook { ORIGINAL, NONE, OTHER }
    public interface Controls {
        boolean selectHyperion();
        boolean useHyperion();
        boolean restoreRod();
        Hook hook();
        void useRod();
    }
    private enum Stage { SELECT, USE, RESTORE, REEL, WAIT_GONE, CAST, DONE }
    private Stage stage = Stage.SELECT;
    private int delay, goneTicks;
    private boolean aborted;
    public boolean active() { return stage != Stage.DONE; }
    public boolean aborted() { return aborted; }
    public void cancel() { stage = Stage.DONE; aborted = true; }

    public void tick(Controls controls) {
        if (!active() || delay > 0 && --delay > 0) return;
        switch (stage) {
            case SELECT -> {
                if (!controls.selectHyperion()) { cancel(); return; }
                stage = Stage.USE; delay = 2;
            }
            case USE -> {
                if (!controls.useHyperion()) { cancel(); return; }
                stage = Stage.RESTORE; delay = 2;
            }
            case RESTORE -> {
                if (!controls.restoreRod()) { cancel(); return; }
                stage = Stage.REEL; delay = 2;
            }
            case REEL -> {
                if (!controls.restoreRod()) { cancel(); return; }
                switch (controls.hook()) {
                    case OTHER -> stage = Stage.DONE; // Somebody has already cast again; leave it alone.
                    case NONE -> { stage = Stage.CAST; delay = 2; }
                    case ORIGINAL -> { controls.useRod(); stage = Stage.WAIT_GONE; delay = 2; }
                }
            }
            case WAIT_GONE -> {
                switch (controls.hook()) {
                    case OTHER -> stage = Stage.DONE;
                    case NONE -> { stage = Stage.CAST; delay = 2; }
                    case ORIGINAL -> { if (++goneTicks >= 40) cancel(); }
                }
            }
            case CAST -> {
                if (controls.hook() == Hook.NONE) {
                    if (!controls.restoreRod()) { cancel(); return; }
                    controls.useRod();
                }
                stage = Stage.DONE;
            }
            case DONE -> { }
        }
    }
}
