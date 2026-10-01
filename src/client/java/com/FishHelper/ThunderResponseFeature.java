package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Owns camera and hotbar input only for the encounter started by Thunder's spawn message. */
final class ThunderResponseFeature implements ThunderSequence.Controls {
    static final String SPAWN_MESSAGE = "You hear a massive rumble as Thunder emerges.";
    private final Minecraft client;
    private final LocalPlayer player;
    private final ClientLevel level;
    private final int restoreSlot;
    private final float restoreYaw;
    private final float restorePitch;
    private final Vec3 spawnOrigin;
    private final ThunderSequence sequence = new ThunderSequence();
    private ElderGuardian target;

    ThunderResponseFeature(Minecraft client, int restoreSlot, Vec3 spawnOrigin) {
        this.client = client;
        this.player = client.player;
        this.level = client.level;
        this.restoreSlot = restoreSlot;
        this.restoreYaw = player.getYRot();
        this.restorePitch = player.getXRot();
        this.spawnOrigin = spawnOrigin;
    }

    boolean canContinue() {
        return Config.INSTANCE.thunderResponseEnabled && client.player == player && client.level == level
                && player.isAlive() && client.gameMode != null && client.screen == null;
    }

    boolean tick() {
        sequence.tick(this);
        return sequence.active();
    }

    void finish() {
        sequence.cancel();
        // Never apply an old world's camera/slot snapshot to a new player or level.
        if (client.player == player && client.level == level) {
            player.getInventory().setSelectedSlot(restoreSlot);
            player.setYRot(restoreYaw);
            player.setXRot(restorePitch);
        }
    }

    @Override
    public boolean findThunder() {
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ElderGuardian guardian) || !guardian.isAlive()
                    || guardian.distanceToSqr(player) > 32 * 32) continue;
            // Hypixel may put the name on a separate armor stand. Prefer a named mob,
            // otherwise correlate the spawn message with the guardian closest to the cast.
            double score = guardian.position().distanceToSqr(spawnOrigin);
            if (guardian.getName().getString().toLowerCase(Locale.ROOT).contains("thunder")) score -= 4096;
            if (score < bestScore) {
                target = guardian;
                bestScore = score;
            }
        }
        return target != null;
    }

    @Override
    public boolean aimAtThunder() {
        if (target == null || !target.isAlive() || target.isRemoved() || target.distanceToSqr(player) > 32 * 32) {
            return false;
        }
        Vec3 direction = target.getEyePosition().subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90f);
        player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y,
                Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
        return true;
    }

    @Override
    public boolean select(ThunderSequence.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (matches(player.getInventory().getItem(slot), item)) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean use(ThunderSequence.Item item) {
        if (!matches(player.getMainHandItem(), item) || client.gameMode == null) return false;
        // useItem synchronizes the selected slot and includes the player's current rotation.
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    @Override
    public boolean hasNearbyElderGuardian() {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ElderGuardian && entity.isAlive() && !entity.isRemoved()
                    && ThunderSequence.inAttackRange(entity.distanceToSqr(player))) return true;
        }
        return false;
    }

    @Override
    public void lookDown() {
        player.setXRot(90f);
    }

    private static boolean matches(ItemStack stack, ThunderSequence.Item item) {
        if (stack.isEmpty()) return false;
        String search = switch (item) {
            case ICE_SPRAY -> "ice spray wand";
            case INK_WAND -> "ink wand";
            case HYPERION -> "hyperion";
        };
        return stack.getHoverName().getString().replaceAll("(?i)§[0-9A-FK-OR]", "")
                .toLowerCase(Locale.ROOT).contains(search);
    }
}
