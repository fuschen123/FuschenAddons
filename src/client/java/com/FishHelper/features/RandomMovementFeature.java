package com.FishHelper.features;

import com.FishHelper.Config;
import com.FishHelper.KeyCategories;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/** Holds S and Shift while randomly strafing left or right until stopped with the keybind. */
public final class RandomMovementFeature {
    private static final String STATUS_PREFIX = "§7[§5Fuschen§dAddons§7]";

    private static KeyMapping toggleKey;
    private static boolean active;
    private static boolean strafeLeft;
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
        ClientTickEvents.END_CLIENT_TICK.register(RandomMovementFeature::tick);
    }

    private static void tick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            // Avoid toggling the macro from a GUI/keybind menu.
            if (Config.INSTANCE.randomMovementEnabled && client.screen == null
                    && client.player != null) {
                active = !active;
                if (active) {
                    strafeLeft = ThreadLocalRandom.current().nextBoolean();
                    strafeTicks = ThreadLocalRandom.current().nextInt(3, 8);
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
        if (client.screen != null) {
            releaseMovementKeys(client);
            return;
        }

        if (--strafeTicks <= 0) {
            strafeLeft = ThreadLocalRandom.current().nextBoolean();
            // Change strafing direction every 3–7 game ticks.
            strafeTicks = ThreadLocalRandom.current().nextInt(3, 8);
        }

        client.options.keyDown.setDown(true);
        client.options.keyShift.setDown(true);
        client.options.keyLeft.setDown(strafeLeft);
        client.options.keyRight.setDown(!strafeLeft);
        ownsMovementKeys = true;
    }

    private static void releaseMovementKeys(Minecraft client) {
        // Do not modify vanilla controls while the feature has never taken control.
        // When it does release them, restore the actual physical state so held keys
        // and custom bindings continue to work normally.
        if (!ownsMovementKeys) {
            return;
        }
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
            pauseOnLostFocusBeforeActivation = client.pauseOnLostFocus;
            pauseOnLostFocusOverridden = true;
        }
        client.pauseOnLostFocus = false;
    }

    private static void restorePauseOnLostFocus(Minecraft client) {
        if (pauseOnLostFocusOverridden) {
            client.pauseOnLostFocus = pauseOnLostFocusBeforeActivation;
            pauseOnLostFocusOverridden = false;
        }
    }
}
