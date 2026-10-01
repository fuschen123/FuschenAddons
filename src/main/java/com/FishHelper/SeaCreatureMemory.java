package com.FishHelper;

import java.util.*;

/** World-local identity memory. Gaps retain identity for 2 s; stale HP is never presented as current. */
public final class SeaCreatureMemory {
    public static final int GAP_TICKS = 40;
    public record Observation(UUID uuid, int entityId, SeaCreatureNametag nametag) { }
    public record Entry(UUID uuid, int entityId, SeaCreatureNametag nametag, long lastSeen, long lastHealth) { }
    public enum Presence { ALIVE, DEAD }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public void update(long tick, Collection<Observation> observations, Map<UUID, Presence> presence) {
        Set<UUID> dead = new HashSet<>();
        for (Observation observation : observations) if (observation.nametag.dead()) dead.add(observation.uuid);
        // One entry per real mob, regardless of the number of display armor stands.
        for (Observation observation : observations) {
            if (dead.contains(observation.uuid) || presence.get(observation.uuid) == Presence.DEAD) {
                entries.remove(observation.uuid);
                continue;
            }
            entries.put(observation.uuid, new Entry(observation.uuid, observation.entityId,
                    observation.nametag, tick, tick));
        }
        entries.replaceAll((uuid, entry) -> new Entry(uuid, entry.entityId,
                tick - entry.lastHealth > GAP_TICKS ? entry.nametag.unknownHealth() : entry.nametag,
                presence.get(uuid) == Presence.ALIVE ? tick : entry.lastSeen, entry.lastHealth));
        entries.values().removeIf(entry -> presence.get(entry.uuid) == Presence.DEAD
                || tick - entry.lastSeen > GAP_TICKS);
    }

    public Collection<Entry> entries() { return List.copyOf(entries.values()); }
    public Entry get(UUID uuid) { return entries.get(uuid); }
    public void clear() { entries.clear(); }
}
