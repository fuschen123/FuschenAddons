package com.FishHelper;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;

/** Compile-time bridge for the Minecraft 26.2 GUI API. */
public final class ClientCompat {
    private ClientCompat() {}
    public static boolean isPlainSlime(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.monster.cubemob.Slime && !(entity instanceof net.minecraft.world.entity.monster.cubemob.MagmaCube);
    }
    public static Screen screen(Minecraft client) { return client.gui.screen(); }
    public static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    public static boolean hideGui(Minecraft client) { return client.gui.hud.isHidden(); }
    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYSYM; }
    /** Legacy persisted toggleKeyCode is GLFW; named bindings remain preferred. */
    public static int legacyKeyCode(int code) { return code; }
    public static boolean physicallyDown(Minecraft client, InputConstants.Key key) { return key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(client.getWindow(), key.getValue()); }
    public static void swing(net.minecraft.client.player.LocalPlayer player, net.minecraft.world.InteractionHand hand, boolean attack) { player.swing(hand); }
}
