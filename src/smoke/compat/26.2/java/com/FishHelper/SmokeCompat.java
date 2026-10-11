package com.FishHelper;
import net.minecraft.client.Minecraft;
final class SmokeCompat {
    static net.minecraft.world.entity.LivingEntity magma(Minecraft client) {
        return new net.minecraft.world.entity.monster.cubemob.MagmaCube(SmokeClient.type("magma_cube"), client.level);
    }
    static boolean overlay(Minecraft client) { return client.gui.overlay() != null; }
    static void screenshot(Minecraft client, String name) {
        net.minecraft.client.Screenshot.grab(new java.io.File(System.getProperty("fuschen.smoke.output")), name,
            client.gameRenderer.mainRenderTarget(), 1, m -> System.out.println("SMOKE_SCREENSHOT: " + m.getString()));
    }
}
