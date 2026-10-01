package com.FishHelper;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Water Snake identification shared with the single hook-recovery sequence. */
final class WaterSnakeRecastFeature {

    private static final String WATER_SNAKE_TEXTURE_HASH =
            "23a0d55ad300e4f8870e754fb06db58c7f15fc292026edc79ea7e4ef3cc808cb";
    private static final Pattern ENCODED_PROFILE_PROPERTY = Pattern.compile("[A-Za-z0-9+/]{80,}={0,2}");

    static boolean isWaterSnake(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        // An armor-stand label is not the snake model: verify its actual head texture.
        if (entity instanceof ArmorStand) return hasWaterSnakeTexture(entity);
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
