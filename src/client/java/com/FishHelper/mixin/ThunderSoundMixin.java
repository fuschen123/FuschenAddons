/* Adapted from FishyAddons MixinSoundSystem (GPL-3.0-only), 2026-10-01. */
package com.FishHelper.mixin;

import com.FishHelper.ThunderMuter;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class ThunderSoundMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void fuschen$muteThunder(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (ThunderMuter.shouldClean(sound)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}
