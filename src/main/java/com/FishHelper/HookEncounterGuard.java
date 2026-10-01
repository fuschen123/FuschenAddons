package com.FishHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** World-scoped: even a failed attempt consumes the encounter, and a rehooked mob cannot loop. */
public final class HookEncounterGuard {
    private final Set<UUID> hooks = new HashSet<>(), mobs = new HashSet<>();
    public boolean claim(UUID hook, UUID mob) {
        boolean fresh = !hooks.contains(hook) && !mobs.contains(mob);
        hooks.add(hook); mobs.add(mob);
        return fresh;
    }
    public void clear() { hooks.clear(); mobs.clear(); }
}
