package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/** Handles attacking a hooked Grinch only while the player is aiming at it. */
final class GrinchAutoClickerFeature {
    private static java.util.UUID clickTargetUuid;
    private static long nextClickAt;

    private GrinchAutoClickerFeature() {
    }

    static void tick(Minecraft client, LocalPlayer player, Entity hookedEntity) {
        if (!Config.INSTANCE.grinchAutoClickerEnabled || !isGrinch(hookedEntity)) {
            reset();
            return;
        }
        if (client.crosshairPickEntity != hookedEntity) {
            reset();
            return;
        }
        if (client.screen != null || player.isUsingItem() || client.gameMode == null) {
            return;
        }

        if (!hookedEntity.getUUID().equals(clickTargetUuid)) {
            clickTargetUuid = hookedEntity.getUUID();
            nextClickAt = 0;
        }

        long now = System.currentTimeMillis();
        if (now < nextClickAt) {
            return;
        }

        client.gameMode.attack(player, hookedEntity);
        player.swing(InteractionHand.MAIN_HAND);

        double cps = Math.max(3.0, Math.min(15.0, Config.INSTANCE.grinchClickCps));
        double intervalMs = (1000.0 / cps) + ((Math.random() - 0.5) * 60.0);
        nextClickAt = now + Math.max(1L, Math.round(intervalMs));
    }

    private static void reset() {
        clickTargetUuid = null;
        nextClickAt = 0;
    }

    private static boolean isGrinch(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        String name = entity.getName().getString();
        if (entity.getCustomName() != null) {
            name += " " + entity.getCustomName().getString();
        }
        return name.replaceAll("(?i)§[0-9A-FK-OR]", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("grinch");
    }
}
