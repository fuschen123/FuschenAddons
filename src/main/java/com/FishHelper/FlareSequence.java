package com.FishHelper;

/** Each transition has its own tick; revalidate item and nearby effects before use. */
public final class FlareSequence {
    public interface Controls {
        boolean satisfied();
        boolean select();
        boolean stillSelected();
        void use();
        void restore();
    }
    private final int delay;
    private int phase, remaining;
    public FlareSequence(int delay) { this.delay = Math.max(1, Math.min(20, delay)); }
    public boolean active() { return phase < 3; }
    public void cancel(Controls c) { if (phase > 0 && active()) c.restore(); phase = 3; }
    public void tick(Controls c) {
        if (!active() || remaining > 0 && --remaining > 0) return;
        if (phase == 0) {
            if (c.satisfied() || !c.select()) { phase = 3; return; }
            phase = 1; remaining = delay;
        } else if (phase == 1) {
            if (!c.satisfied() && c.stillSelected()) c.use();
            phase = 2; remaining = delay;
        } else { c.restore(); phase = 3; }
    }
}
