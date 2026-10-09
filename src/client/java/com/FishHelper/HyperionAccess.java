package com.FishHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

final class HyperionAccess {
    private static final java.util.regex.Pattern WISE_LORE = enchantmentLine("Ultimate Wise");
    private static final java.util.regex.Pattern CHIMERA_LORE = enchantmentLine("Chimera");
    private HyperionAccess() { }
    private static java.util.regex.Pattern enchantmentLine(String label) {
        return java.util.regex.Pattern.compile("(?im)(?:^|,)\\h*" + label + "\\h+(?:[IVX]+|[1-9][0-9]*)\\h*(?=,|$)");
    }
    static boolean hyperion(ItemStack stack) {
        String id = PetMenus.attributes(stack).getStringOr("id", "");
        return !stack.isEmpty() && (id.isBlank()
                ? PetMenus.plain(stack.getHoverName().getString()).toLowerCase(java.util.Locale.ROOT).matches(".*\\bhyperion\\b.*")
                : id.equals("HYPERION"));
    }
    static boolean matches(ItemStack stack, HyperionPolicy.Kind kind) {
        return detectedKind(stack) == kind;
    }
    static HyperionPolicy.Kind detectedKind(ItemStack stack) {
        if (!hyperion(stack)) return null;
        var enchantments = PetMenus.attributes(stack).getCompoundOrEmpty("enchantments");
        // Server item data takes precedence over cosmetic names and lore.
        boolean wise, chimera;
        if (!enchantments.isEmpty()) {
            wise = enchantments.getIntOr("ultimate_wise", 0) > 0;
            chimera = enchantments.getIntOr("ultimate_chimera", 0) > 0;
        } else {
            String lore = PetMenus.lore(stack);
            wise = WISE_LORE.matcher(lore).find();
            chimera = CHIMERA_LORE.matcher(lore).find();
        }
        // Unknown or contradictory enchantments must never become a guessed weapon type.
        return wise == chimera ? null : wise ? HyperionPolicy.Kind.ULTIMATE_WISE : HyperionPolicy.Kind.CHIMERA;
    }
    static int slot(LocalPlayer player, HyperionPolicy.Kind kind) {
        if (Config.INSTANCE.autoDetectHyperions) {
            for (int slot = 0; slot < 9; slot++) if (matches(player.getInventory().getItem(slot), kind)) return slot;
            return -1;
        }
        int slot = (kind == HyperionPolicy.Kind.ULTIMATE_WISE ? Config.INSTANCE.ultimateWiseSlot : Config.INSTANCE.chimeraSlot) - 1;
        return slot >= 0 && slot < 9 && matches(player.getInventory().getItem(slot), kind) ? slot : -1;
    }
    static boolean validPair(LocalPlayer player) {
        int wise = slot(player, HyperionPolicy.Kind.ULTIMATE_WISE), chimera = slot(player, HyperionPolicy.Kind.CHIMERA);
        return wise >= 0 && chimera >= 0 && wise != chimera;
    }
    static int find(LocalPlayer player, HyperionPolicy.Kind kind) {
        if (Config.INSTANCE.twoHyperions) return validPair(player) ? slot(player, kind) : -1;
        for (int slot = 0; slot < 9; slot++) if (hyperion(player.getInventory().getItem(slot))) return slot;
        return -1;
    }
    static boolean held(LocalPlayer player, HyperionPolicy.Kind kind) {
        int slot = find(player, kind);
        return slot >= 0 && player.getInventory().getSelectedSlot() == slot;
    }
}
