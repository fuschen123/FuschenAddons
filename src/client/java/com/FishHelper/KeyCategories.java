package com.FishHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Shared Controls category for FuschenAddons keybindings. */
public final class KeyCategories {
    public static final KeyMapping.Category MAIN = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("tsclient", "main")
    );

    private KeyCategories() {
    }
}
