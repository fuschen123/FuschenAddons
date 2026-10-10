package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Shared, source-independent encounter lifetime for normal and cocoon-released mobs. */
class SeaCreatureEncounter {
    private final String creatureName;
    private static final double ACQUIRE_DISTANCE_SQUARED = 32 * 32;
    private final JawbusPause pause = new JawbusPause();
    private ClientLevel world;
    private Vec3 fishingOrigin;

    SeaCreatureEncounter(String creatureName) { this.creatureName = creatureName; }

    boolean active() { return pause.active(); }
    java.util.List<JawbusPause.Target> targets() { return pause.targets(); }
    void reset() { pause.reset(); world = null; fishingOrigin = null; }

    JawbusPause.Change spawned(Minecraft client) {
        bindWorld(client);
        if (client.level == null || client.player == null) return JawbusPause.Change.NONE;
        if (active()) return JawbusPause.Change.NONE;
        // Entity packets can precede chat. Scanning acquires every real UUID; duplicate
        // notifications must not leave phantom encounters after that entity dies.
        JawbusPause.Change observed = tick(client);
        if (active()) return observed;
        fishingOrigin = castPosition(client);
        return pause.spawned();
    }

    JawbusPause.Change tick(Minecraft client) {
        bindWorld(client);
        if (client.level == null || client.player == null) return JawbusPause.Change.NONE;
        SeaCreatureTracker tracker = SeaCreatureTracker.INSTANCE;
        Map<UUID, JawbusPause.Observation> observations = new LinkedHashMap<>();
        Vec3 currentCast = castPosition(client);
        for (SeaCreatureMemory.Entry entry : tracker.creatures()) {
            if (!entry.nametag().name().equals(creatureName)) continue;
            Entity entity = tracker.entity(entry);
            if (entity == null) continue;
            boolean nearby = entity.distanceToSqr(client.player) <= ACQUIRE_DISTANCE_SQUARED
                    || entity.position().distanceToSqr(currentCast) <= ACQUIRE_DISTANCE_SQUARED
                    || fishingOrigin != null && entity.position().distanceToSqr(fishingOrigin) <= ACQUIRE_DISTANCE_SQUARED;
            observations.put(entry.uuid(), new JawbusPause.Observation(entry.uuid(), entry.entityId(),
                    JawbusPause.Presence.ALIVE, nearby));
        }
        // Keep UUID/id pairs, not Entity references. Nametag gaps must not lose a live encounter.
        for (JawbusPause.Target target : pause.targets()) {
            Entity entity = resolve(client, target);
            JawbusPause.Presence presence = JawbusPause.Presence.ABSENT;
            if (tracker.confirmedDead(target.uuid())) presence = JawbusPause.Presence.DEAD;
            else if (entity != null && entity.getUUID().equals(target.uuid()))
                presence = entity.isAlive() && !entity.isRemoved() ? JawbusPause.Presence.ALIVE : JawbusPause.Presence.DEAD;
            observations.put(target.uuid(), new JawbusPause.Observation(target.uuid(),
                    entity == null ? target.entityId() : entity.getId(), presence, false));
        }
        JawbusPause.Change change = pause.tick(observations.values());
        if (change == JawbusPause.Change.STARTED) fishingOrigin = currentCast;
        if (change == JawbusPause.Change.ENDED) fishingOrigin = null;
        return change;
    }

    private void bindWorld(Minecraft client) {
        if (world != client.level) { reset(); world = client.level; }
    }
    Entity resolve(Minecraft client, JawbusPause.Target target) {
        Entity entity = client.level.getEntity(target.entityId());
        if (entity != null && entity.getUUID().equals(target.uuid())) return entity;
        for (Entity candidate : client.level.entitiesForRendering())
            if (candidate.getUUID().equals(target.uuid())) return candidate;
        return null;
    }
    private Vec3 castPosition(Minecraft client) {
        var hook = FishHelperClient.findOwnedBobber(client, client.player);
        return hook == null ? client.player.position() : hook.position();
    }
}
