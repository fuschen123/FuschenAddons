package com.FishHelper;

/** Tick-driven encounter logic, independent of Minecraft so input ordering can be tested. */
public final class ThunderSequence {
    public enum Item { ICE_SPRAY, INK_WAND, HYPERION }

    public interface Controls {
        boolean findThunder();
        boolean aimAtThunder();
        boolean select(Item item);
        boolean use(Item item);
        boolean hasNearbyElderGuardian();
        void lookDown();
    }

    private enum Stage { FIND, ICE_SELECT, ICE_USE, INK_SELECT, INK_USE, HYPERION_SELECT, HYPERION_USE, DONE }
    private Stage stage = Stage.FIND;
    private int waitTicks;
    private int searchTicks;

    /** Five blocks in 3D, inclusive, rather than a ten-block-wide cube. */
    public static boolean inAttackRange(double distanceSquared) {
        return distanceSquared <= 25.0;
    }

    public boolean active() {
        return stage != Stage.DONE;
    }

    public void cancel() {
        stage = Stage.DONE;
    }

    public void tick(Controls controls) {
        if (!active()) return;
        // Check every tick, including the delay between clicks: no extra click after it leaves.
        if ((stage == Stage.HYPERION_SELECT || stage == Stage.HYPERION_USE)
                && !controls.hasNearbyElderGuardian()) {
            cancel();
            return;
        }
        if (waitTicks > 0 && --waitTicks > 0) return;

        switch (stage) {
            case FIND -> {
                if (controls.findThunder()) stage = Stage.ICE_SELECT;
                else if (++searchTicks >= 60) cancel(); // Spawn packets may arrive after chat.
            }
            case ICE_SELECT -> selectWand(controls, Item.ICE_SPRAY, Stage.ICE_USE, Stage.INK_SELECT);
            case ICE_USE -> useWand(controls, Item.ICE_SPRAY, Stage.INK_SELECT);
            case INK_SELECT -> selectWand(controls, Item.INK_WAND, Stage.INK_USE, Stage.HYPERION_SELECT);
            case INK_USE -> useWand(controls, Item.INK_WAND, Stage.HYPERION_SELECT);
            case HYPERION_SELECT -> {
                if (!controls.select(Item.HYPERION)) {
                    cancel();
                    return;
                }
                controls.lookDown();
                stage = Stage.HYPERION_USE;
                waitTicks = 2;
            }
            case HYPERION_USE -> {
                controls.lookDown();
                if (!controls.use(Item.HYPERION)) cancel();
                waitTicks = 4; // 5 right clicks per second at 20 TPS.
            }
            case DONE -> { }
        }
    }

    private void selectWand(Controls controls, Item item, Stage use, Stage next) {
        if (!controls.aimAtThunder()) {
            cancel();
            return;
        }
        stage = controls.select(item) ? use : next;
        waitTicks = 2;
    }

    private void useWand(Controls controls, Item item, Stage next) {
        if (!controls.aimAtThunder()) {
            cancel();
            return;
        }
        // Revalidate the selected stack: an item moved during the swap must not be used.
        controls.use(item);
        stage = next;
        waitTicks = 2;
    }
}
