package com.FishHelper;

/** Bounded menu transaction. Resolve the current page again before every click. */
public final class PetSwapSequence {
    public enum Menu { NONE, PETS, OTHER }
    public enum Selection { ACTIVE, SUMMON, MISSING, AMBIGUOUS }
    public interface Controls {
        Menu menu();
        boolean hookReady();
        Selection selection();
        void open();
        void click();
        void closeOwnedMenu();
        default void navigateKnownPage() { }
    }
    private int delay, age, sinceClick = 20, waiting;
    private final int deadline;
    private boolean requested, seen, done, confirmed;
    public PetSwapSequence(int delayTicks) { delay = Math.max(0, delayTicks); deadline = delay + 600; }
    public boolean active() { return !done; }
    public boolean confirmed() { return confirmed; }
    public void cancel() { done = true; }
    public void tick(Controls c) {
        if (done) return;
        Menu menu = c.menu();
        if (menu == Menu.OTHER) { done = true; return; }
        if (++age > deadline) { c.closeOwnedMenu(); done = true; return; }
        if (menu == Menu.PETS) {
            if (!c.hookReady()) { c.closeOwnedMenu(); done = true; return; }
            seen = true;
            Selection selection = c.selection();
            if (selection == Selection.ACTIVE) {
                confirmed = true; done = true; c.closeOwnedMenu(); return;
            }
            // Neither the old slot nor an unconfirmed click is evidence of identity/equip.
            if (++sinceClick >= 20 && selection == Selection.SUMMON) {
                c.click(); sinceClick = 0;
            } else if (sinceClick >= 20 && selection == Selection.MISSING) {
                c.navigateKnownPage(); sinceClick = 0;
            }
            return;
        }
        if (seen) { done = true; return; } // Closed by server/user; never reopen in this transaction.
        if (!requested) {
            if (delay > 0) { delay--; return; }
            if (!c.hookReady()) { if (++waiting >= 40) done = true; return; }
            c.open(); requested = true; waiting = 0;
        } else if (++waiting >= 100) done = true;
    }
}
