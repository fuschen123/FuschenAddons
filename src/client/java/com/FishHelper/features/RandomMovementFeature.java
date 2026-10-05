package com.FishHelper.features;

import com.FishHelper.Config;
import com.FishHelper.KeyCategories;
import com.FishHelper.ModBindings;
import com.FishHelper.MovementPlanner;
import com.FishHelper.FishHelperClient;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/** Bounded movement around an explicitly selected world-local center. */
public final class RandomMovementFeature {
    private static final String STATUS_PREFIX = "§7[§5Fuschen§dAddons§7]";

    private static KeyMapping toggleKey;
    private static boolean active;
    private static Vec3 center;
    private static ClientLevel centerWorld;
    private static Object centerConnection;
    private static boolean centerHint;
    private static double targetX, targetZ;
    private static boolean ownsMovementKeys;
    private static boolean pauseOnLostFocusBeforeActivation;
    private static boolean pauseOnLostFocusOverridden;
    private static int strafeTicks;

    private RandomMovementFeature() {
    }

    public static void initialize() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.tsclient.random_movement",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyCategories.MAIN
        ));
        ModBindings.register("movement", toggleKey);
        ClientTickEvents.END_CLIENT_TICK.register(RandomMovementFeature::tick);
    }

    public static int setCenter(Minecraft client) {
        if (client.player == null || client.level == null) return 0;
        center = client.player.position(); centerWorld = client.level; centerConnection = client.getConnection();
        centerHint = false; strafeTicks = 0;
        client.player.sendSystemMessage(Component.literal(STATUS_PREFIX + " Movement center set (radius ~2 blocks)"));
        return 1;
    }
    public static void suspend(Minecraft client) { releaseMovementKeys(client); }
    public static void reset(Minecraft client) {
        active = false; center = null; centerWorld = null; centerConnection = null; centerHint = false;
        releaseMovementKeys(client); restorePauseOnLostFocus(client);
    }
    private static void tick(Minecraft client) {
        if (centerWorld != null && (centerWorld != client.level || centerConnection != client.getConnection())) reset(client);
        while (toggleKey.consumeClick()) {
            // Avoid toggling the macro from a GUI/keybind menu.
            if (Config.INSTANCE.randomMovementEnabled && client.screen == null
                    && client.player != null) {
                active = !active;
                if (active) {
                    strafeTicks = 0;
                    disablePauseOnLostFocus(client);
                } else {
                    releaseMovementKeys(client);
                    restorePauseOnLostFocus(client);
                }
                client.player.sendSystemMessage(Component.literal(STATUS_PREFIX
                        + " Random movement: " + (active ? "§aON" : "§cOFF")));
            }
        }

        if (!Config.INSTANCE.randomMovementEnabled) {
            active = false;
            releaseMovementKeys(client);
            restorePauseOnLostFocus(client);
            return;
        }

        if (!active) {
            releaseMovementKeys(client);
            restorePauseOnLostFocus(client);
            return;
        }

        // Minecraft otherwise pauses its client tick after focus is lost.
        disablePauseOnLostFocus(client);

        if (client.player == null || client.level == null) {
            releaseMovementKeys(client);
            return;
        }

        // Any open screen (inventory, chat, pause menu, or another GUI) suspends movement.
        if (client.screen != null || FishHelperClient.movementPaused() || !client.player.isAlive()) {
            releaseMovementKeys(client);
            return;
        }

        if (center == null) {
            releaseMovementKeys(client);
            if (!centerHint) {
                client.player.sendSystemMessage(Component.literal(STATUS_PREFIX + " Set a movement center first: /fa movement center"));
                centerHint = true;
            }
            return;
        }
        if (--strafeTicks <= 0 || Math.hypot(client.player.getX() - center.x - targetX, client.player.getZ() - center.z - targetZ) < .2) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(.4, 1.3);
            targetX = Math.cos(angle) * radius; targetZ = Math.sin(angle) * radius; strafeTicks = 30;
        }
        var input = MovementPlanner.choose(client.player.getX() - center.x, client.player.getZ() - center.z,
                client.player.getYRot(), targetX, targetZ, (dx, dz) -> safe(client, dx, dz));
        if (input.forward() == 0 && input.left() == 0) { releaseMovementKeys(client); return; }
        client.options.keyUp.setDown(input.forward() > 0);
        client.options.keyDown.setDown(input.forward() < 0);
        client.options.keyLeft.setDown(input.left() > 0);
        client.options.keyRight.setDown(input.left() < 0);
        client.options.keyShift.setDown(true);
        ownsMovementKeys = true;
    }

    private static boolean safe(Minecraft client, double dx, double dz) {
        var player = client.player;
        if (!player.onGround() || player.isInWater() || player.isInLava()) return false;
        for (double ahead : new double[]{.2, .4, .65}) {
            var box = player.getBoundingBox().deflate(.02, 0, .02).move(dx * ahead, 0, dz * ahead);
            if (!client.level.noCollision(player, box)) return false;
            for (double x : new double[]{box.minX, box.maxX}) for (double z : new double[]{box.minZ, box.maxZ}) {
                BlockPos feet = BlockPos.containing(x, box.minY - .1, z);
                var block = client.level.getBlockState(feet);
                if (!client.level.hasChunkAt(feet) || !client.level.getFluidState(feet).isEmpty()
                        || block.getCollisionShape(client.level, feet).isEmpty()
                        || block.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        || block.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                        || block.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                        || block.is(net.minecraft.world.level.block.Blocks.CACTUS)) return false;
            }
        }
        return true;
    }
    private static void releaseMovementKeys(Minecraft client) {
        // Do not modify vanilla controls while the feature has never taken control.
        // When it does release them, restore the actual physical state so held keys
        // and custom bindings continue to work normally.
        if (!ownsMovementKeys) {
            return;
        }
        restorePhysicalState(client, client.options.keyUp);
        restorePhysicalState(client, client.options.keyDown);
        restorePhysicalState(client, client.options.keyShift);
        restorePhysicalState(client, client.options.keyLeft);
        restorePhysicalState(client, client.options.keyRight);
        ownsMovementKeys = false;
    }

    private static void restorePhysicalState(Minecraft client, KeyMapping mapping) {
        if (mapping.isUnbound()) {
            mapping.setDown(false);
            return;
        }

        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        boolean physicallyDown = switch (key.getType()) {
            case KEYSYM, SCANCODE -> InputConstants.isKeyDown(client.getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        };
        mapping.setDown(physicallyDown);
    }

    private static void disablePauseOnLostFocus(Minecraft client) {
        if (!pauseOnLostFocusOverridden) {
            pauseOnLostFocusBeforeActivation = client.options.pauseOnLostFocus;
            pauseOnLostFocusOverridden = true;
        }
        client.options.pauseOnLostFocus = false;
    }

    private static void restorePauseOnLostFocus(Minecraft client) {
        if (pauseOnLostFocusOverridden) {
            client.options.pauseOnLostFocus = pauseOnLostFocusBeforeActivation;
            pauseOnLostFocusOverridden = false;
        }
    }
}
