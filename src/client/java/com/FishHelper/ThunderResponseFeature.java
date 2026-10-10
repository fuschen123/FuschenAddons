package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Exclusive input owner for a named, living Thunder encounter, regardless of spawn source. */
final class ThunderResponseFeature implements ThunderSequence.Controls {
    static final String SPAWN_MESSAGE = "You hear a massive rumble as Thunder emerges.";
    private final Minecraft client;
    private final LocalPlayer player;
    private final ClientLevel level;
    private final int restoreSlot;
    private final Vec3 spawnOrigin;
    private final SeaCreatureEncounter encounter;
    private final boolean ownsEncounter;
    private final Map<UUID, HyperionPolicy> weaponChoices = new HashMap<>();
    private ThunderSequence sequence = new ThunderSequence();
    private JawbusPause.Target target;
    private boolean missingItemReported;
    private int singleHyperionSlot = -1;

    ThunderResponseFeature(Minecraft client, int restoreSlot, Vec3 spawnOrigin) {
        this(client, restoreSlot, spawnOrigin, new SeaCreatureEncounter("Thunder"), true);
    }
    ThunderResponseFeature(Minecraft client, int restoreSlot, Vec3 spawnOrigin, SeaCreatureEncounter encounter) {
        this(client, restoreSlot, spawnOrigin, encounter, false);
    }
    private ThunderResponseFeature(Minecraft client, int restoreSlot, Vec3 spawnOrigin,
                                   SeaCreatureEncounter encounter, boolean ownsEncounter) {
        this.client = client; this.player = client.player; this.level = client.level;
        this.restoreSlot = restoreSlot; this.spawnOrigin = spawnOrigin;
        this.encounter = encounter; this.ownsEncounter = ownsEncounter;
    }

    boolean canContinue() {
        return client.player == player && client.level == level
                && player.isAlive() && client.gameMode != null;
    }
    boolean tick() { return tick(true); }
    boolean tick(boolean allowInput) {
        if (ownsEncounter) encounter.tick(client);
        // Keep the same UUID and its latched weapon choice through gaps/range changes.
        if (target != null) {
            var retained = encounter.targets().stream().filter(t -> t.uuid().equals(target.uuid())).findFirst();
            if (retained.isPresent()) target = retained.get();
            else { target = null; sequence = new ThunderSequence(); }
        }
        if (target == null && !findThunder()) return encounter.active();
        var entry = SeaCreatureTracker.INSTANCE.get(target.uuid());
        weaponChoices.computeIfAbsent(target.uuid(), id -> new HyperionPolicy())
                .thunder(entry == null ? null : entry.nametag().currentHp());
        if (allowInput && client.screen == null) sequence.tick(this);
        return encounter.active();
    }
    boolean aborted() { return sequence.aborted(); }

    void finish() {
        sequence.cancel();
        restoreSlot();
    }
    void restoreSlot() {
        if (client.player == player && client.level == level) player.getInventory().setSelectedSlot(restoreSlot);
    }

    private ElderGuardian resolvedTarget() {
        if (target == null) return null;
        var mob = encounter.resolve(client, target);
        return mob instanceof ElderGuardian guardian && guardian.isAlive() && !guardian.isRemoved()
                && !SeaCreatureTracker.INSTANCE.confirmedDead(target.uuid()) ? guardian : null;
    }
    @Override public boolean findThunder() {
        if (target != null) return resolvedTarget() != null;
        target = encounter.targets().stream().filter(t -> encounter.resolve(client, t) instanceof ElderGuardian)
                .min(Comparator.<JawbusPause.Target>comparingDouble(t -> encounter.resolve(client, t).position().distanceToSqr(spawnOrigin))
                        .thenComparing(t -> t.uuid().toString())).orElse(null);
        return resolvedTarget() != null;
    }
    @Override public boolean hasThunderTarget() { return resolvedTarget() != null; }
    @Override public boolean hasLivingThunder() { return encounter.active(); }
    @Override public boolean hasThunderInAttackRange() {
        var mob = resolvedTarget();
        return mob != null && ThunderSequence.inAttackRange(mob.distanceToSqr(player));
    }

    private int hyperionSlot() {
        if (!Config.INSTANCE.twoHyperions) {
            if (singleHyperionSlot >= 0 && HyperionAccess.hyperion(player.getInventory().getItem(singleHyperionSlot)))
                return singleHyperionSlot;
            singleHyperionSlot = HyperionAccess.find(player, HyperionPolicy.Kind.CHIMERA);
            return singleHyperionSlot;
        }
        if (target == null) return -1;
        var entry = SeaCreatureTracker.INSTANCE.get(target.uuid());
        var kind = weaponChoices.computeIfAbsent(target.uuid(), id -> new HyperionPolicy())
                .thunder(entry == null ? null : entry.nametag().currentHp());
        if (kind == null) return -1; // No initial HP: finish the wands, then wait without guessing a weapon.
        // Use the existing configured/manual or auto-detected slots, validating each enchantment independently.
        int desired = HyperionAccess.slot(player, kind);
        if (desired >= 0) return desired;
        int fallback = kind == HyperionPolicy.Kind.CHIMERA
                ? HyperionAccess.slot(player, HyperionPolicy.Kind.ULTIMATE_WISE) : -1;
        if (!missingItemReported) {
            missingItemReported = true;
            player.sendSystemMessage(Component.literal("[FuschenAddons] " + (fallback >= 0
                    ? "Chimera Hyperion missing; continuing with Ultimate Wise."
                    : "Required Hyperion missing; Thunder attacks paused until it returns.")));
        }
        return fallback;
    }

    @Override public boolean select(ThunderSequence.Item item) {
        if (item == ThunderSequence.Item.HYPERION) {
            int slot = hyperionSlot();
            if (slot < 0) return false;
            player.getInventory().setSelectedSlot(slot); return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (matches(player.getInventory().getItem(slot), item)) {
                player.getInventory().setSelectedSlot(slot); return true;
            }
        }
        return false;
    }
    @Override public boolean use(ThunderSequence.Item item) {
        if (item == ThunderSequence.Item.HYPERION) {
            int slot = hyperionSlot();
            if (slot < 0) return false;
            if (player.getInventory().getSelectedSlot() != slot) {
                player.getInventory().setSelectedSlot(slot);
                return true; // Synchronize the new slot before the next scheduled four-tick use.
            }
        }
        if (!matches(player.getMainHandItem(), item) || client.gameMode == null) return false;
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        return true;
    }
    static boolean matches(ItemStack stack, ThunderSequence.Item item) {
        if (item == ThunderSequence.Item.HYPERION) return HyperionAccess.hyperion(stack);
        if (stack.isEmpty()) return false;
        String search = item == ThunderSequence.Item.ICE_SPRAY ? "ice spray wand" : "ink wand";
        return PetMenus.plain(stack.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(search);
    }
}
