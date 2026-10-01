/* FishyAddons sound-filter adaptation (GPL-3.0-only), 2026-10-01. See THIRD_PARTY_NOTICES.md. */
package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.scores.DisplaySlot;
import java.util.Locale;

/** Independent of fishing's enabled state; reads recognition without changing it. */
public final class ThunderMuter {
    private static final ThunderSoundFilter FILTER = new ThunderSoundFilter();
    private static ClientLevel world;

    public static void reset() { FILTER.reset(); world = Minecraft.getInstance().level; }

    public static boolean shouldClean(SoundInstance sound) {
        var client = Minecraft.getInstance();
        if (world != client.level) reset();
        if (sound == null || !Config.INSTANCE.thunderMuterEnabled || !inSkyblock(client)) return false;
        boolean alive = SeaCreatureTracker.INSTANCE.creatures().stream().anyMatch(entry ->
                entry.nametag().name().equals("Thunder") && SeaCreatureTracker.INSTANCE.entity(entry) != null);
        return FILTER.shouldMute(true, true, sound.getIdentifier().getPath(), alive, System.currentTimeMillis());
    }

    private static boolean inSkyblock(Minecraft client) {
        if (client.level == null || client.player == null || client.getCurrentServer() == null) return false;
        String host = client.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":", 2)[0];
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return false;
        var objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return objective != null && objective.getDisplayName().getString().replaceAll("§.", "")
                .toLowerCase(Locale.ROOT).contains("skyblock");
    }
}
