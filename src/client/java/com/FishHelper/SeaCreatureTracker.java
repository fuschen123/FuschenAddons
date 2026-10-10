/* Entity association adapted from Feesh 1.14.0, Apache-2.0; see THIRD_PARTY_NOTICES.md. */
package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Shared by the HUD, Thunder and Jawbus pause; display stands are never returned as mob targets. */
final class SeaCreatureTracker {
    static final SeaCreatureTracker INSTANCE = new SeaCreatureTracker();
    private final SeaCreatureMemory memory = new SeaCreatureMemory();
    private ClientLevel world;
    private long ticks;
    private UUID hudTarget;
    private final Set<UUID> confirmedDeaths = new HashSet<>();
    private final Map<UUID, UUID> nametagLinks = new HashMap<>();
    private final Map<UUID, JawbusShurikenWarning.Status> shurikenObservations = new HashMap<>();

    void reset() { memory.clear(); nametagLinks.clear(); confirmedDeaths.clear(); shurikenObservations.clear(); hudTarget = null; ticks = 0; world = null; }
    boolean confirmedDead(UUID uuid) { return confirmedDeaths.contains(uuid); }
    Map<UUID, JawbusShurikenWarning.Status> shurikenObservations() { return Map.copyOf(shurikenObservations); }

    void tick(Minecraft client) {
        confirmedDeaths.clear();
        shurikenObservations.clear();
        if (world != client.level) { memory.clear(); nametagLinks.clear(); hudTarget = null; ticks = 0; world = client.level; }
        if (world == null) return;
        ticks++;
        Map<UUID, SeaCreatureMemory.Presence> presence = new HashMap<>();
        List<SeaCreatureMemory.Observation> observations = new ArrayList<>();
        for (Entity entity : world.entitiesForRendering()) {
            presence.put(entity.getUUID(), entity.isAlive() && !entity.isRemoved()
                    ? SeaCreatureMemory.Presence.ALIVE : SeaCreatureMemory.Presence.DEAD);
            if (!(entity instanceof ArmorStand) || entity.getCustomName() == null) continue;
            // Skip obfuscated glyphs at the component level instead of copying Feesh's formatter.
            StringBuilder text = new StringBuilder();
            entity.getCustomName().visit((style, part) -> {
                if (!style.isObfuscated()) text.append(part);
                return Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            SeaCreatureNametag tag = SeaCreatureNametag.parse(text.toString());
            if (tag == null) continue;
            boolean encounter = tag.name().equals("Thunder") || tag.name().equals("Lord Jawbus");
            if (!encounter && ticks % 5 != 1) continue;
            Entity mob = world.getEntity(entity.getId() - SeaCreatureNametag.entityOffset(tag.name()));
            if (encounter) mob = encounterMob(entity, mob, tag.name());
            if (tag.name().equals("Jawbus Follower") && mob instanceof Slime && !(mob instanceof MagmaCube))
                mob = world.getEntity(entity.getId() - 11);
            if (!(mob instanceof LivingEntity || mob instanceof Display.ItemDisplay) || mob instanceof ArmorStand) continue;
            if (mob instanceof Player && (mob.getUUID().version() == 4 || mob.getUUID().version() == 1)) continue;
            if (tag.name().equals("Thunder") && !(mob instanceof ElderGuardian)) continue;
            if (!mob.isAlive() || mob.isRemoved()) continue;
            observations.add(new SeaCreatureMemory.Observation(mob.getUUID(), mob.getId(), tag));
            if (tag.name().equals("Lord Jawbus")) {
                var status = JawbusShurikenWarning.parse(text.toString());
                shurikenObservations.merge(mob.getUUID(), status,
                        (a, b) -> a == b ? a : JawbusShurikenWarning.Status.UNKNOWN);
            }
        }
        memory.update(ticks, observations, presence);
        nametagLinks.keySet().removeIf(uuid -> !presence.containsKey(uuid));
        for (SeaCreatureMemory.Observation observation : observations)
            if (observation.nametag().dead()) confirmedDeaths.add(observation.uuid());
        presence.forEach((uuid, state) -> { if (state == SeaCreatureMemory.Presence.DEAD) confirmedDeaths.add(uuid); });
    }

    Collection<SeaCreatureMemory.Entry> creatures() { return memory.entries(); }

    /** Cocoon releases can use a new model/nametag pair. Prefer the established ID link;
     * if it is unavailable, accept only a unique matching living model underneath the tag.
     * RFU MobManager documents the same narrow column (-4..+0.5 Y, +/-0.5 X/Z).
     * Ambiguous stacked mobs are never assigned another creature's HP. */
    private Entity encounterMob(Entity label, Entity adjacent, String name) {
        UUID linked = nametagLinks.get(label.getUUID());
        if (linked != null) {
            for (Entity mob : world.entitiesForRendering())
                if (mob.getUUID().equals(linked)) return encounterModel(mob, name) ? mob : null;
            return null; // An orphaned tag must never migrate to another living mob.
        }
        if (encounterModel(adjacent, name) && underTag(label, adjacent)) {
            nametagLinks.put(label.getUUID(), adjacent.getUUID());
            return adjacent;
        }
        Entity only = null;
        for (Entity candidate : world.entitiesForRendering()) {
            if (!encounterModel(candidate, name) || !underTag(label, candidate)) continue;
            if (only != null) return null;
            only = candidate;
        }
        if (only != null) nametagLinks.put(label.getUUID(), only.getUUID());
        return only;
    }
    private static boolean encounterModel(Entity mob, String name) {
        return mob != null && mob.isAlive() && !mob.isRemoved()
                && (name.equals("Thunder") ? mob instanceof ElderGuardian : mob instanceof IronGolem);
    }
    private static boolean underTag(Entity label, Entity mob) {
        double dy = label.getY() - mob.getY();
        return Math.abs(label.getX() - mob.getX()) <= .5 && Math.abs(label.getZ() - mob.getZ()) <= .5
                && dy >= -.5 && dy <= 4;
    }
    SeaCreatureMemory.Entry get(UUID id) { return memory.get(id); }
    Entity entity(SeaCreatureMemory.Entry entry) {
        Entity entity = world == null ? null : world.getEntity(entry.entityId());
        return entity != null && entity.getUUID().equals(entry.uuid()) && entity.isAlive() && !entity.isRemoved() ? entity : null;
    }

    SeaCreatureMemory.Entry hudTarget(Minecraft client) {
        if (client.player == null) return null;
        // Sticky nearest: once selected, keep it until it dies/disappears or leaves 30 blocks.
        SeaCreatureMemory.Entry selected = memory.get(hudTarget);
        if (selected != null) {
            Entity entity = entity(selected);
            if (entity == null || entity.distanceToSqr(client.player) <= 900) return selected;
        }
        selected = memory.entries().stream().filter(entry -> entity(entry) != null
                        && entity(entry).distanceToSqr(client.player) <= 900)
                .min(Comparator.<SeaCreatureMemory.Entry>comparingDouble(entry -> entity(entry).distanceToSqr(client.player))
                        .thenComparing(entry -> entry.uuid().toString())).orElse(null);
        hudTarget = selected == null ? null : selected.uuid();
        return selected;
    }
}
