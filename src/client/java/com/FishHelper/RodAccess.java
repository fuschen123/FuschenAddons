package com.FishHelper;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Re-resolves a moved rod, preferring the captured stack/hand over a hotbar fallback. */
final class RodAccess {
    private final LocalPlayer player;
    private final int originalSlot;
    private final InteractionHand originalHand;
    private final ItemStack original;
    private int slot;
    private InteractionHand hand;
    RodAccess(LocalPlayer player, int preferredSlot, InteractionHand preferredHand) {
        this.player = player;
        originalSlot = preferredSlot >= 0 && preferredSlot < 9 ? preferredSlot : player.getInventory().getSelectedSlot();
        originalHand = preferredHand != null ? preferredHand : player.getOffhandItem().is(Items.FISHING_ROD)
                && !player.getInventory().getItem(originalSlot).is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        original = (originalHand == InteractionHand.OFF_HAND ? player.getOffhandItem() : player.getInventory().getItem(originalSlot)).copy();
    }
    boolean available() {
        if (originalHand == InteractionHand.OFF_HAND && rod(player.getOffhandItem())) return bind(InteractionHand.OFF_HAND, originalSlot);
        if (rod(original)) {
            for (int i = 0; i < 9; i++) if (ItemStack.isSameItemSameComponents(original, player.getInventory().getItem(i))) return bind(InteractionHand.MAIN_HAND, i);
            if (ItemStack.isSameItemSameComponents(original, player.getOffhandItem())) return bind(InteractionHand.OFF_HAND, originalSlot);
        }
        if (rod(player.getInventory().getItem(originalSlot))) return bind(InteractionHand.MAIN_HAND, originalSlot);
        if (rod(player.getOffhandItem())) return bind(InteractionHand.OFF_HAND, originalSlot);
        for (int i = 0; i < 9; i++) if (rod(player.getInventory().getItem(i))) return bind(InteractionHand.MAIN_HAND, i);
        hand = null; return false;
    }
    private boolean bind(InteractionHand value, int selected) { hand = value; slot = selected; return true; }
    private boolean rod(ItemStack stack) { return stack.is(Items.FISHING_ROD); }
    boolean select() { if (!available()) return false; player.getInventory().setSelectedSlot(slot); return true; }
    InteractionHand hand() { return hand; }
    boolean held() { return hand != null && rod(player.getItemInHand(hand)); }
    void restore() { if (!select()) player.getInventory().setSelectedSlot(originalSlot); }
}
