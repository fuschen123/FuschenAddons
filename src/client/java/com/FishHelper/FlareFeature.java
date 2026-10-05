package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

final class FlareFeature implements FlareSequence.Controls {
    private final Minecraft client;
    private final LocalPlayer player;
    private final ClientLevel world;
    private final int restoreSlot, required;
    private int slot = -1;
    private ItemStack selected = ItemStack.EMPTY;
    private final FlareSequence sequence;
    FlareFeature(Minecraft client) {
        this.client = client; player = client.player; world = client.level;
        restoreSlot = player.getInventory().getSelectedSlot();
        required = Config.INSTANCE.flareTier.ordinal();
        sequence = new FlareSequence(Config.INSTANCE.flareSwapDelayTicks);
    }
    boolean tick() {
        if (client.player != player || client.level != world || !player.isAlive() || client.screen != null
                || Config.INSTANCE.flareTier.ordinal() != required) { finish(); return false; }
        sequence.tick(this); return sequence.active();
    }
    void finish() { sequence.cancel(this); }
    static boolean nearby(Minecraft client, int required) {
        if (required == 0 || client.player == null || client.level == null) return false;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.isAlive() || stand.isRemoved()
                    || stand.distanceToSqr(client.player) > 40 * 40) continue;
            // Real Hypixel flares are skull armor stands; a nametag is not required.
            if (FlareRules.sufficient(required, textureTier(stand.getItemBySlot(EquipmentSlot.HEAD)))) return true;
            if (!stand.hasCustomName()) continue;
            String name = PetMenus.plain(stand.getCustomName().getString());
            if (name.matches(".*(?:^|\\s)0(?:\\.0)?s(?:\\s|$).*")) continue;
            if (FlareRules.sufficient(required, FlareRules.tier(name))) return true;
        }
        return false;
    }
    static int textureTier(ItemStack helmet) {
        var profile = helmet.get(DataComponents.PROFILE);
        if (profile == null) return 0;
        for (var property : profile.partialProfile().properties().get("textures")) {
            try {
                String json = new String(java.util.Base64.getDecoder().decode(property.value()), java.nio.charset.StandardCharsets.UTF_8);
                String url = com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                        .getAsJsonObject("SKIN").get("url").getAsString();
                String hash = url.substring(url.lastIndexOf('/') + 1);
                // Texture identifiers from SkyHanni-REPO constants/Skulls.json (2026-10-05).
                return switch (hash) {
                    case "22e2bf6c1ec330247927ba63479e5872ac66b06903c86c82b52dac9f1c971458" -> 1;
                    case "9d2bf9864720d87fd06b84efa80b795c48ed539b16523c3b1f1990b40c003f6b" -> 2;
                    case "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b" -> 3;
                    default -> 0;
                };
            } catch (RuntimeException ignored) { }
        }
        return 0;
    }
    static int find(LocalPlayer player, int required) {
        int best = -1, tier = 4;
        for (int i = 0; i < 9; i++) {
            int actual = FlareRules.tier(player.getInventory().getItem(i).getHoverName().getString());
            if (FlareRules.sufficient(required, actual) && actual < tier) { best = i; tier = actual; }
        }
        return best;
    }
    public boolean satisfied() { return nearby(client, required); }
    public boolean select() {
        slot = find(player, required);
        if (slot < 0) return false;
        selected = player.getInventory().getItem(slot).copy();
        player.getInventory().setSelectedSlot(slot); return true;
    }
    public boolean stillSelected() {
        return slot >= 0 && slot == player.getInventory().getSelectedSlot()
                && ItemStack.isSameItemSameComponents(selected, player.getMainHandItem());
    }
    public void use() { if (client.gameMode != null) client.gameMode.useItem(player, InteractionHand.MAIN_HAND); }
    public void restore() { if (client.player == player && client.level == world) player.getInventory().setSelectedSlot(restoreSlot); }
}
