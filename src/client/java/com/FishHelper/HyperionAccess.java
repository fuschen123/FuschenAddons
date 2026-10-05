package com.FishHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

final class HyperionAccess {
    private HyperionAccess() { }
    static boolean hyperion(ItemStack stack) {
        String id = PetMenus.attributes(stack).getStringOr("id", "");
        return !stack.isEmpty() && (id.isBlank()
                ? PetMenus.plain(stack.getHoverName().getString()).toLowerCase(java.util.Locale.ROOT).matches(".*\\bhyperion\\b.*")
                : id.equals("HYPERION"));
    }
    static boolean matches(ItemStack stack, HyperionPolicy.Kind kind) {
        if (!hyperion(stack)) return false;
        var enchantments = PetMenus.attributes(stack).getCompoundOrEmpty("enchantments");
        String enchantment = kind == HyperionPolicy.Kind.ULTIMATE_WISE ? "ultimate_wise" : "ultimate_chimera";
        if (enchantments.contains(enchantment)) return enchantments.getIntOr(enchantment, 0) > 0;
        if (!enchantments.isEmpty()) return false;
        String label = kind == HyperionPolicy.Kind.ULTIMATE_WISE ? "Ultimate Wise" : "Chimera";
        return java.util.regex.Pattern.compile("(?i)(?:^|[,\\n]\\s*)" + label + " [IVX]+(?:,|$)", java.util.regex.Pattern.MULTILINE)
                .matcher(PetMenus.lore(stack)).find();
    }
    static boolean validPair(LocalPlayer player) {
        var c = Config.INSTANCE;
        return HyperionPolicy.validSlots(c.ultimateWiseSlot, c.chimeraSlot)
                && matches(player.getInventory().getItem(c.ultimateWiseSlot - 1), HyperionPolicy.Kind.ULTIMATE_WISE)
                && matches(player.getInventory().getItem(c.chimeraSlot - 1), HyperionPolicy.Kind.CHIMERA);
    }
    static int find(LocalPlayer player, HyperionPolicy.Kind kind) {
        if (Config.INSTANCE.twoHyperions) return validPair(player)
                ? (kind == HyperionPolicy.Kind.ULTIMATE_WISE ? Config.INSTANCE.ultimateWiseSlot : Config.INSTANCE.chimeraSlot) - 1 : -1;
        for (int slot = 0; slot < 9; slot++) if (hyperion(player.getInventory().getItem(slot))) return slot;
        return -1;
    }
    static boolean held(LocalPlayer player, HyperionPolicy.Kind kind) {
        int slot = find(player, kind);
        return slot >= 0 && player.getInventory().getSelectedSlot() == slot;
    }
}
