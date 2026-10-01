package com.FishHelper;

import java.util.*;

/** Encounter lifetime only: this class never enables fishing or issues an input. */
public final class JawbusPause {
    public static final int SPAWN_WAIT_TICKS = 200;
    public static final int ABSENCE_TICKS = 100;
    public enum Presence { ALIVE, DEAD, ABSENT }
    public enum Change { NONE, STARTED, ENDED }
    public record Observation(UUID uuid, int entityId, Presence presence, boolean nearby) { }
    public record Target(UUID uuid, int entityId) { }
    private static final class Tracked {
        Target target;
        int absentTicks;
        Tracked(Observation observation) { target = new Target(observation.uuid(), observation.entityId()); }
    }
    private final Map<UUID, Tracked> targets = new LinkedHashMap<>();
    private final List<Integer> pendingSpawns = new ArrayList<>();

    public boolean active() { return !targets.isEmpty() || !pendingSpawns.isEmpty(); }
    public List<Target> targets() { return targets.values().stream().map(value -> value.target).toList(); }
    public void reset() { targets.clear(); pendingSpawns.clear(); }

    public Change spawned() {
        boolean wasActive = active();
        pendingSpawns.add(SPAWN_WAIT_TICKS);
        return wasActive ? Change.NONE : Change.STARTED;
    }

    public Change tick(Collection<Observation> observations) {
        boolean wasActive = active();
        pendingSpawns.replaceAll(remaining -> remaining - 1);
        pendingSpawns.removeIf(remaining -> remaining <= 0);
        Map<UUID, Observation> current = new HashMap<>();
        for (Observation observation : observations) current.put(observation.uuid(), observation);

        for (Observation observation : current.values()) {
            if (observation.presence() == Presence.ALIVE && observation.nearby()
                    && !targets.containsKey(observation.uuid())) {
                targets.put(observation.uuid(), new Tracked(observation));
                // Each newly identified mob fulfils at most one own spawn notification.
                if (!pendingSpawns.isEmpty()) pendingSpawns.removeFirst();
            }
        }
        targets.entrySet().removeIf(entry -> {
            Observation observation = current.get(entry.getKey());
            if (observation != null && observation.presence() == Presence.DEAD) return true;
            if (observation != null && observation.presence() == Presence.ALIVE) {
                entry.getValue().target = new Target(observation.uuid(), observation.entityId());
                entry.getValue().absentTicks = 0;
                return false; // Once relevant, a live mob stays relevant beyond attack/acquisition range.
            }
            return ++entry.getValue().absentTicks >= ABSENCE_TICKS;
        });
        return wasActive == active() ? Change.NONE : active() ? Change.STARTED : Change.ENDED;
    }
}
