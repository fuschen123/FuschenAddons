package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reels in and recasts the rod when the bobber is attached to a Water Snake. */
final class WaterSnakeRecastFeature {
    private static final int RECAST_DELAY_TICKS = 2;
    private static final String WATER_SNAKE_TEXTURE_HASH =
            "23a0d55ad300e4f8870e754fb06db58c7f15fc292026edc79ea7e4ef3cc808cb";
    private static final Pattern ENCODED_PROFILE_PROPERTY = Pattern.compile("[A-Za-z0-9+/]{80,}={0,2}");

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
        return normalized.contains("water snake") || hasWaterSnakeTexture(entity);
    }

    private static boolean hasWaterSnakeTexture(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) {
            return false;
        }

        String headComponents = stand.getItemBySlot(EquipmentSlot.HEAD).getComponents().toString();
        if (headComponents.contains(WATER_SNAKE_TEXTURE_HASH)) {
            return true;
        }

        // Player-head profiles store their texture URL as Base64 encoded JSON.
        Matcher matcher = ENCODED_PROFILE_PROPERTY.matcher(headComponents);
        while (matcher.find()) {
            try {
                String profileJson = new String(Base64.getDecoder().decode(matcher.group()), StandardCharsets.UTF_8);
                if (profileJson.contains(WATER_SNAKE_TEXTURE_HASH)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Other item components can also contain long Base64 strings.
            }
        }
        return false;
    }
}
