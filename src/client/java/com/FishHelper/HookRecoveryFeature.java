package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import java.util.UUID;

final class HookRecoveryFeature implements HookRecoverySequence.Controls {
    private final Minecraft client;
    private final LocalPlayer player;
    private final ClientLevel world;
    private final UUID originalHook;
    private final int restoreSlot;
    private final InteractionHand rodHand;
    private final HookRecoverySequence sequence = new HookRecoverySequence();

    HookRecoveryFeature(Minecraft client, FishingHook hook, int originalSlot, InteractionHand originalHand) {
        this.client = client; player = client.player; world = client.level;
        originalHook = hook.getUUID();
        restoreSlot = originalSlot >= 0 ? originalSlot : player.getInventory().getSelectedSlot();
        rodHand = originalHand != null ? originalHand
                : player.getInventory().getItem(restoreSlot).is(Items.FISHING_ROD) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : null;
    }

    static boolean isBlockingMob(FishingHook hook, LocalPlayer player) {
        if (hook == null || hook.getOwner() != player) return false;
        Entity mob = hook.getHookedIn();
        boolean recognized = mob != null && SeaCreatureTracker.INSTANCE.get(mob.getUUID()) != null;
        return mob != null && mob.isAlive() && !mob.isRemoved()
                && (mob instanceof LivingEntity && !(mob instanceof ArmorStand) && (!(mob instanceof Player) || recognized)
                    || recognized || WaterSnakeRecastFeature.isWaterSnake(mob));
    }

    boolean canContinue() {
        return client.player == player && client.level == world && player.isAlive()
                && client.gameMode != null && client.screen == null;
    }
    boolean tick() { sequence.tick(this); return sequence.active(); }
    boolean aborted() { return sequence.aborted(); }
    void finish() {
        sequence.cancel();
        if (client.player == player && client.level == world) player.getInventory().setSelectedSlot(restoreSlot);
    }
    public boolean restoreRod() {
        if (rodHand == null) return false;
        player.getInventory().setSelectedSlot(restoreSlot);
        return player.getItemInHand(rodHand).is(Items.FISHING_ROD);
    }
    public boolean selectHyperion() {
        // Validate both prerequisites before issuing any use action.
        if (!restoreRod()) return false;
        for (int slot = 0; slot < 9; slot++) {
            if (ThunderResponseFeature.matches(player.getInventory().getItem(slot), ThunderSequence.Item.HYPERION)) {
                player.getInventory().setSelectedSlot(slot); return true;
            }
        }
        return false;
    }
    public boolean useHyperion() {
        if (!ThunderResponseFeature.matches(player.getMainHandItem(), ThunderSequence.Item.HYPERION)) return false;
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }
    public HookRecoverySequence.Hook hook() {
        FishingHook hook = FishHelperClient.findOwnedBobber(client, player);
        return hook == null ? HookRecoverySequence.Hook.NONE : originalHook.equals(hook.getUUID())
                ? HookRecoverySequence.Hook.ORIGINAL : HookRecoverySequence.Hook.OTHER;
    }
    public void useRod() {
        client.gameMode.useItem(player, rodHand); player.swing(rodHand);
    }
}
