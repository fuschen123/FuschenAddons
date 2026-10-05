package com.FishHelper;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Only the observed menu position and head appearance; never an actionable saved stack. */
public record PetMenuSlot(int slot, PetIdentity pet, String profile) {
    public static boolean validSlot(int slot) {
        return slot >= 10 && slot <= 43 && slot % 9 != 0 && slot % 9 != 8;
    }

    static PetMenuSlot capture(int slot, PetIdentity pet, ItemStack stack) {
        var head = stack.get(DataComponents.PROFILE);
        String profile = head == null ? "" : ResolvableProfile.CODEC.encodeStart(JsonOps.INSTANCE, head)
                .result().map(Object::toString).orElse("");
        return new PetMenuSlot(slot, pet, profile);
    }

    public ItemStack icon() {
        var stack = new ItemStack(Items.PLAYER_HEAD);
        if (profile != null && !profile.isBlank()) {
            try {
                ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(profile)).result()
                        .ifPresent(head -> stack.set(DataComponents.PROFILE, head));
            } catch (RuntimeException ignored) { /* Old/damaged icon data must not break selection. */ }
        }
        return stack;
    }
}
