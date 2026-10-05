package com.FishHelper;

import java.util.Locale;

public final class FlareRules {
    private FlareRules() { }
    public static int tier(String text) {
        String plain = text.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        if (plain.contains("sos flare")) return 3;
        if (plain.contains("alert flare")) return 2;
        if (plain.contains("warning flare")) return 1;
        return 0;
    }
    public static boolean sufficient(int required, int actual) { return required > 0 && actual >= required; }
}
