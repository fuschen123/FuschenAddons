package com.FishHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import java.util.LinkedHashMap;
import java.util.Map;

/** Vanilla key names preserve type (key.keyboard.*, key.mouse.*) and remain Controls-compatible. */
public final class ModBindings {
    private static final Map<String, KeyMapping> MAPPINGS = new LinkedHashMap<>();
    private ModBindings() { }
    public static void register(String id, KeyMapping mapping) {
        MAPPINGS.put(id, mapping);
        String saved = switch (id) {
            case "toggle" -> Config.INSTANCE.toggleBinding;
            case "config" -> Config.INSTANCE.configBinding;
            default -> Config.INSTANCE.movementBinding;
        };
        if (saved != null) try { mapping.setKey(InputConstants.getKey(saved)); } catch (IllegalArgumentException ignored) { }
        KeyMapping.resetMapping();
    }
    public static KeyMapping get(String id) { return MAPPINGS.get(id); }
    public static void observe() {
        if (get("toggle") == null || get("config") == null || get("movement") == null) return;
        if (!java.util.Objects.equals(Config.INSTANCE.toggleBinding, get("toggle").saveString())
                || !java.util.Objects.equals(Config.INSTANCE.configBinding, get("config").saveString())
                || !java.util.Objects.equals(Config.INSTANCE.movementBinding, get("movement").saveString())) saveBindings();
    }
    public static void bind(String id, InputConstants.Key key) {
        KeyMapping mapping = get(id);
        if (mapping == null) return;
        mapping.setKey(key);
        mapping.setDown(false);
        while (mapping.consumeClick()) { }
        KeyMapping.resetMapping();
        saveBindings();
        Minecraft.getInstance().options.save();
    }
    public static void saveBindings() {
        if (get("toggle") != null) Config.INSTANCE.toggleBinding = get("toggle").saveString();
        if (get("config") != null) Config.INSTANCE.configBinding = get("config").saveString();
        if (get("movement") != null) Config.INSTANCE.movementBinding = get("movement").saveString();
        Config.save();
    }
}
