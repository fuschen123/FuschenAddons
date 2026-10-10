package com.FishHelper;

public final class HyperionPolicy {
    public enum Kind { ULTIMATE_WISE, CHIMERA }
    public static final double CHIMERA_HP = 6_000_000;
    private Kind selected;
    public static boolean validSlots(int wise, int chimera) {
        return wise >= 1 && wise <= 9 && chimera >= 1 && chimera <= 9 && wise != chimera;
    }
    /** One instance per mob UUID. Wait for initial HP; keep the choice across unknown samples. */
    public Kind thunder(Double currentHp) {
        if (currentHp != null && Double.isFinite(currentHp) && currentHp > 0 && selected != Kind.CHIMERA)
            selected = currentHp <= CHIMERA_HP ? Kind.CHIMERA : Kind.ULTIMATE_WISE;
        return selected;
    }
}
