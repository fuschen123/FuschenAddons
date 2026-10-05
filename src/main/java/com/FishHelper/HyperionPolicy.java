package com.FishHelper;

import java.util.Collection;

public final class HyperionPolicy {
    public enum Kind { ULTIMATE_WISE, CHIMERA }
    public static final double CHIMERA_HP = 3_000_000;
    private HyperionPolicy() { }
    public static boolean validSlots(int wise, int chimera) {
        return wise >= 1 && wise <= 9 && chimera >= 1 && chimera <= 9 && wise != chimera;
    }
    /** Unknown HP is never a reason to risk a lowering hit with Ultimate Wise. */
    public static Kind thunder(Collection<Double> health) {
        return health.isEmpty() || health.stream().anyMatch(hp -> hp == null || !Double.isFinite(hp) || hp <= CHIMERA_HP)
                ? Kind.CHIMERA : Kind.ULTIMATE_WISE;
    }
}
