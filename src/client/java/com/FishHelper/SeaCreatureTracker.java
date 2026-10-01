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
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Shared by the HUD and Thunder; display stands are never returned as combat targets. */
final class SeaCreatureTracker {
    static final SeaCreatureTracker INSTANCE = new SeaCreatureTracker();
    private final SeaCreatureMemory memory = new SeaCreatureMemory();
    private ClientLevel world;
    private long ticks;
    private UUID hudTarget;

    void reset() { memory.clear(); hudTarget = null; ticks = 0; world = null; }

    void tick(Minecraft client) {
        if (world != client.level) { memory.clear(); hudTarget = null; ticks = 0; world = client.level; }
        if (world == null) return;
        ticks++;
        Map<UUID, SeaCreatureMemory.Presence> presence = new HashMap<>();
        List<SeaCreatureMemory.Observation> observations = new ArrayList<>();
        for (Entity entity : world.entitiesForRendering()) {
            presence.put(entity.getUUID(), entity.isAlive() && !entity.isRemoved()
                    ? SeaCreatureMemory.Presence.ALIVE : SeaCreatureMemory.Presence.DEAD);
            if (ticks % 5 != 1 || !(entity instanceof ArmorStand) || entity.getCustomName() == null) continue;
            // Skip obfuscated glyphs at the component level instead of copying Feesh's formatter.
            StringBuilder text = new StringBuilder();
            entity.getCustomName().visit((style, part) -> {
                if (!style.isObfuscated()) text.append(part);
                return Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            SeaCreatureNametag tag = SeaCreatureNametag.parse(text.toString());
            if (tag == null) continue;
            Entity mob = world.getEntity(entity.getId() - SeaCreatureNametag.entityOffset(tag.name()));
            if (tag.name().equals("Jawbus Follower") && mob instanceof Slime && !(mob instanceof MagmaCube))
                mob = world.getEntity(entity.getId() - 11);
            if (!(mob instanceof LivingEntity || mob instanceof Display.ItemDisplay) || mob instanceof ArmorStand) continue;
            if (mob instanceof Player && (mob.getUUID().version() == 4 || mob.getUUID().version() == 1)) continue;
            if (tag.name().equals("Thunder") && !(mob instanceof ElderGuardian)) continue;
            if (!mob.isAlive() || mob.isRemoved()) continue;
            observations.add(new SeaCreatureMemory.Observation(mob.getUUID(), mob.getId(), tag));
        }
        memory.update(ticks, observations, presence);
    }

    Collection<SeaCreatureMemory.Entry> creatures() { return memory.entries(); }
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
