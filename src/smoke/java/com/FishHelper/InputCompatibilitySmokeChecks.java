package com.FishHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;

/** Exercises saved config and real target input APIs, in the disposable dev instance only. */
final class InputCompatibilitySmokeChecks {
    static void run(SmokeClient t, Minecraft client) throws Exception {
        t.check(ClientCompat.legacyKeyCode(79) == InputConstants.KEY_O, "legacy O survives backend migration");
        t.check(ClientCompat.legacyKeyCode(70) == InputConstants.KEY_F, "custom legacy F survives backend migration");
        t.check(ClientCompat.legacyKeyCode(256) == InputConstants.KEY_ESCAPE, "legacy Escape translation");
        for (String key : new String[]{"key.keyboard.o", "key.keyboard.f12", "key.keyboard.left.shift",
                "key.mouse.left", "key.mouse.right", "key.mouse.middle", "key.mouse.4", "key.mouse.5"}) {
            var input = InputConstants.getKey(key);
            t.check(input.getName().equals(key), "stable binding name: " + key);
            ClientCompat.physicallyDown(client, input); // Also load the actual GLFW/SDL native path.
        }
        var path = FabricLoader.getInstance().getConfigDir().resolve("fuschenaddons.json");
        String before = Files.readString(path);
        try {
            Files.writeString(path, "{\"toggleKeyCode\":70,\"petCommandDelayTicks\":17,\"ultimateWiseSlot\":9,\"chimeraSlot\":4,\"petSwapEnabled\":false,\"thunderResponseEnabled\":true,\"configBinding\":\"key.mouse.5\"}");
            Config.load();
            t.check(Config.INSTANCE.toggleKeyCode == 70 && Config.INSTANCE.toggleBinding == null
                    && Config.INSTANCE.petCommandDelayTicks == 17 && Config.INSTANCE.ultimateWiseSlot == 9
                    && Config.INSTANCE.chimeraSlot == 4 && !Config.INSTANCE.petSwapEnabled
                    && Config.INSTANCE.thunderResponseEnabled && Config.INSTANCE.configBinding.equals("key.mouse.5"),
                    "legacy config values retained");
            ModBindings.register("toggle", ModBindings.get("toggle"));
            Config.INSTANCE.toggleBinding = "key.keyboard.unavailable.fuschen";
            ModBindings.register("toggle", ModBindings.get("toggle"));
            ModBindings.saveBindings();
            t.check(ModBindings.get("toggle").isUnbound()
                    && Config.INSTANCE.toggleBinding.equals("key.keyboard.unavailable.fuschen"),
                    "unsupported binding preserved without mapping a different key");
        } finally {
            Files.writeString(path, before); Config.load();
            ModBindings.register("toggle", ModBindings.get("toggle"));
        }
        t.check(java.util.Arrays.stream(net.minecraft.client.sounds.SoundEngine.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("fuschen$muteThunder")),
                "Thunder sound mixin injected into actual target SoundEngine");
        System.out.println("SMOKE_INPUT_COMPAT_OK: legacy/custom keys, keyboard and mouse names, native physical input, config retention, unsupported binding preservation");
    }
}
