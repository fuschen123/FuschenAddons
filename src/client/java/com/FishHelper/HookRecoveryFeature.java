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
import java.util.UUID;

/** Exclusive input owner for normal, watchdog and mob recovery recasts. */
final class HookRecoveryFeature implements HookRecoverySequence.Controls {
    private final Minecraft client;
    private final LocalPlayer player;
    private final ClientLevel world;
    private final UUID originalHook, mob;
    private final RodAccess rod;
    private final HookEncounterGuard guard;
    private final HookRecoverySequence sequence;
    private boolean castIssued;

    HookRecoveryFeature(Minecraft client, UUID hook, UUID mob, int slot, InteractionHand hand, boolean attack, HookEncounterGuard guard) {
        this.client = client; player = client.player; world = client.level;
        originalHook = hook; this.mob = mob; this.guard = guard;
        rod = new RodAccess(player, slot, hand);
        // The setup tick is followed by a four-tick delay, so Hyperion fires
        // on the fifth client tick after detecting a mob on the bobber.
        sequence = new HookRecoverySequence(attack && !guard.wasUsed(hook, mob), hook, 4);
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
    RecastSequence.Problem problem() { return sequence.problem(); }
    boolean castIssued() { return castIssued; }
    void finish() { sequence.cancel(); if (client.player == player && client.level == world) rod.restore(); }
    public boolean selectRod() { return rod.select(); }
    public boolean rodAvailable() { return rod.available(); }
    public boolean selectHyperion() {
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
        guard.used(originalHook, mob);
        return true;
    }
    public UUID currentHook() {
        FishingHook hook = OwnedHookResolver.find(client, player);
        return hook == null ? null : hook.getUUID();
    }
    public boolean hookReferenceSettled() { return player.fishing == null; }
    public boolean alreadyReeled(UUID hook) { return guard.wasReeled(hook); }
    public boolean reel(UUID hook) {
        if (!hook.equals(currentHook()) || !rod.held()) return false;
        client.gameMode.useItem(player, rod.hand()); player.swing(rod.hand());
        guard.reeled(hook);
        return true;
    }
    public boolean cast() {
        if (currentHook() != null || !hookReferenceSettled() || !rod.held()) return false;
        client.gameMode.useItem(player, rod.hand()); player.swing(rod.hand());
        castIssued = true;
        return true;
    }
}
