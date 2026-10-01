package com.FishHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Only successful weapon/reel use consumes an input; failed prerequisites stay retryable. */
public final class HookEncounterGuard {
    private final Set<UUID> hooks = new HashSet<>(), mobs = new HashSet<>(), reeled = new HashSet<>();
    public boolean wasUsed(UUID hook, UUID mob) { return hooks.contains(hook) || mobs.contains(mob); }
    public void used(UUID hook, UUID mob) { hooks.add(hook); mobs.add(mob); }
    public boolean wasReeled(UUID hook) { return reeled.contains(hook); }
    public void reeled(UUID hook) { if (hook != null) reeled.add(hook); }
    public void clear() { hooks.clear(); mobs.clear(); reeled.clear(); }
}
