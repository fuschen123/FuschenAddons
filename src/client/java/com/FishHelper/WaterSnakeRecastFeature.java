package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Reels in and recasts the rod when the bobber is attached to a Water Snake. */
final class WaterSnakeRecastFeature {
    private static final int RECAST_DELAY_TICKS = 2;

    private static int stage;
    private static int timer;
    private static InteractionHand rodHand;
    private static int handledSnakeId = -1;

    private WaterSnakeRecastFeature() {
    }

    /**
     * @return true while this feature is handling the tick, so other fishing actions
     *         do not issue rod or hotbar actions at the same time.
     */
    static boolean tick(Minecraft client, LocalPlayer player, FishingHook bobber, boolean anotherActionActive) {
        if (stage > 0) {
            if (client.screen != null) {
                return true;
            }
            if (stage == 1) {
                if (client.gameMode == null) {
                    return true;
                }
                client.gameMode.useItem(player, rodHand);
                player.swing(rodHand);
                stage = 2;
                timer = RECAST_DELAY_TICKS;
                return true;
            }

            if (timer > 0 && --timer > 0) {
                return true;
            }
            if (client.gameMode == null) {
                return true;
            }

            client.gameMode.useItem(player, rodHand);
            player.swing(rodHand);
            stage = 0;
            rodHand = null;
            return true;
        }

        Entity hookedEntity = bobber == null ? null : bobber.getHookedIn();
        if (!isWaterSnake(hookedEntity)) {
            handledSnakeId = -1;
            return false;
        }
        if (anotherActionActive || handledSnakeId == hookedEntity.getId() || client.screen != null) {
            return false;
        }

        if (player.getMainHandItem().is(Items.FISHING_ROD)) {
            rodHand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().is(Items.FISHING_ROD)) {
            rodHand = InteractionHand.OFF_HAND;
        } else {
            return false;
        }

        handledSnakeId = hookedEntity.getId();
        stage = 1;
        timer = 0;
        return tick(client, player, bobber, false);
    }

    private static boolean isWaterSnake(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        String name = entity.getName().getString();
        if (entity.getCustomName() != null) {
            name += " " + entity.getCustomName().getString();
        }
        String normalized = name.replaceAll("(?i)§[0-9A-FK-OR]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.contains("water snake");
    }
}
