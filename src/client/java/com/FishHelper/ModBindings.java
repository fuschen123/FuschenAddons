package com.FishHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import java.util.LinkedHashMap;
import java.util.Map;

/** Vanilla key names preserve type (key.keyboard.*, key.mouse.*) and remain Controls-compatible. */
public final class ModBindings {
    private static final Map<String, KeyMapping> MAPPINGS = new LinkedHashMap<>();
    private static final java.util.Set<String> UNSUPPORTED = new java.util.HashSet<>();
    private ModBindings() { }
    public static void register(String id, KeyMapping mapping) {
        MAPPINGS.put(id, mapping);
        String saved = switch (id) {
            case "toggle" -> Config.INSTANCE.toggleBinding;
            case "config" -> Config.INSTANCE.configBinding;
            default -> Config.INSTANCE.movementBinding;
        };
        UNSUPPORTED.remove(id);
        if (saved != null) try { mapping.setKey(InputConstants.getKey(saved)); }
        catch (IllegalArgumentException unsupported) {
            // SDL has no GLFW F25/raw-scancode equivalent. Retain the saved binding
            // for returning to that version, without activating a different key.
            mapping.setKey(InputConstants.UNKNOWN);
            UNSUPPORTED.add(id);
        }
        if (saved == null && id.equals("toggle") && Config.INSTANCE.toggleKeyCode != -1
                && ClientCompat.legacyKeyCode(Config.INSTANCE.toggleKeyCode) == InputConstants.UNKNOWN.getValue()) {
            mapping.setKey(InputConstants.UNKNOWN);
            UNSUPPORTED.add(id);
        }
        KeyMapping.resetMapping();
    }
    public static KeyMapping get(String id) { return MAPPINGS.get(id); }
    public static void observe() {
        UNSUPPORTED.removeIf(id -> !get(id).isUnbound());
        if (get("toggle") == null || get("config") == null || get("movement") == null) return;
        if (bindingChanged("toggle", Config.INSTANCE.toggleBinding)
                || bindingChanged("config", Config.INSTANCE.configBinding)
                || bindingChanged("movement", Config.INSTANCE.movementBinding)) saveBindings();
    }
    private static boolean bindingChanged(String id, String saved) {
        return !UNSUPPORTED.contains(id) && !java.util.Objects.equals(saved, get(id).saveString());
    }
    public static void bind(String id, InputConstants.Key key) {
        KeyMapping mapping = get(id);
        if (mapping == null) return;
        UNSUPPORTED.remove(id);
        mapping.setKey(key);
        mapping.setDown(false);
        while (mapping.consumeClick()) { }
        KeyMapping.resetMapping();
        saveBindings();
        Minecraft.getInstance().options.save();
    }
    public static void saveBindings() {
        if (get("toggle") != null && !UNSUPPORTED.contains("toggle")) Config.INSTANCE.toggleBinding = get("toggle").saveString();
        if (get("config") != null && !UNSUPPORTED.contains("config")) Config.INSTANCE.configBinding = get("config").saveString();
        if (get("movement") != null && !UNSUPPORTED.contains("movement")) Config.INSTANCE.movementBinding = get("movement").saveString();
        Config.save();
    }
}
