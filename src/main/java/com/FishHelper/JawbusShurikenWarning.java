package com.FishHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Read-only status memory. Never controls fishing, item use or title animations. */
public final class JawbusShurikenWarning {
    public enum Status { UNKNOWN, MISSING, PRESENT }
    public static final int GAP_TICKS = 40;
    private static final int CONFIRM_WINDOW_TICKS = 10;
    // Protocol evidence: RFU SkyblockEntity.parseNameTag and Feesh's actual Jawbus fixture.
    // ✯ follows the HP heart and optional boss-closing bracket; see README for pinned sources.
    private static final Pattern SUFFIX = Pattern.compile("❤\\s*(?:﴿\\s*)?(✯)?\\s*$");

    public static Status parse(String formatted) {
        SeaCreatureNametag tag = SeaCreatureNametag.parse(formatted);
        if (tag == null || !tag.name().equals("Lord Jawbus") || tag.dead()) return Status.UNKNOWN;
        String plain = formatted.replaceAll("§.", "").strip();
        var suffix = SUFFIX.matcher(plain);
        if (!suffix.find()) return Status.UNKNOWN;
        if (suffix.group(1) != null) return Status.PRESENT;
        // An explicit marker is positive evidence even while HP is loading. Its absence
        // only counts after the complete health/status line has been received.
        if (tag.currentHp() == null || tag.maxHp() == null || tag.currentHp() <= 0 || tag.maxHp() <= 0)
            return Status.UNKNOWN;
        return plain.contains("✯") ? Status.UNKNOWN : Status.MISSING;
    }

    private static final class Sample {
        Status known = Status.UNKNOWN;
        long lastKnown;
        long pendingMissing = -CONFIRM_WINDOW_TICKS - 1;
    }
    private final Map<UUID, Sample> samples = new HashMap<>();
    private long tick;

    /** Fresh observations only: reusing a cached nametag must not refresh its lifetime. */
    public void tick(Set<UUID> relevantAlive, Map<UUID, Status> fresh) {
        tick++;
        samples.keySet().retainAll(relevantAlive);
        for (var observation : fresh.entrySet()) {
            if (!relevantAlive.contains(observation.getKey())) continue;
            Sample sample = samples.computeIfAbsent(observation.getKey(), ignored -> new Sample());
            switch (observation.getValue()) {
                case PRESENT -> {
                    sample.known = Status.PRESENT;
                    sample.lastKnown = tick;
                    sample.pendingMissing = -CONFIRM_WINDOW_TICKS - 1;
                }
                case MISSING -> {
                    // Require two complete reads before asserting that the effect is absent.
                    if (tick - sample.pendingMissing <= CONFIRM_WINDOW_TICKS) {
                        sample.known = Status.MISSING;
                        sample.lastKnown = tick;
                    }
                    sample.pendingMissing = tick;
                }
                case UNKNOWN -> sample.pendingMissing = -CONFIRM_WINDOW_TICKS - 1;
            }
        }
        for (Sample sample : samples.values()) {
            if (tick - sample.lastKnown > GAP_TICKS) sample.known = Status.UNKNOWN;
        }
    }

    public boolean visible() { return samples.values().stream().anyMatch(s -> s.known == Status.MISSING); }
    public void reset() { samples.clear(); tick = 0; }
}
