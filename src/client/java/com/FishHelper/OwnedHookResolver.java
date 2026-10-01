package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import java.util.UUID;

/** The world entity registry is authoritative; player.fishing can temporarily retain an old object. */
final class OwnedHookResolver {
    private static UUID stale;
    private static int staleTicks;
    static void reset() { stale = null; staleTicks = 0; }
    static FishingHook find(Minecraft client, LocalPlayer player) {
        if (client.level == null || player == null) return null;
        FishingHook reference = player.fishing;
        if (reference != null && client.level.getEntity(reference.getId()) instanceof FishingHook live
                && live.getUUID().equals(reference.getUUID()) && owned(live, player)) return live;
        FishingHook newest = null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof FishingHook hook && owned(hook, player)
                    && (newest == null || hook.getId() > newest.getId())) newest = hook;
        }
        return newest;
    }
    private static boolean owned(FishingHook hook, LocalPlayer player) {
        return hook.getPlayerOwner() == player && hook.isAlive() && !hook.isRemoved();
    }
    static void tick(Minecraft client) {
        if (client.level == null || client.player == null) { reset(); return; }
        var player = client.player;
        FishingHook live = find(client, player);
        if (live != null) {
            player.fishing = live; // Repair only the local pointer, never remove a live world entity.
            reset(); return;
        }
        if (player.fishing == null) { reset(); return; }
        UUID reference = player.fishing.getUUID();
        if (!reference.equals(stale)) { stale = reference; staleTicks = 0; }
        if (++staleTicks >= 4) { player.fishing = null; reset(); }
    }
}
