package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;

final class HyperionDetectionSmokeChecks {
    static void run(SmokeClient t, Minecraft c) {
        var inv = c.player.getInventory();
        var wise = inv.getItem(0); var chimera = inv.getItem(1);
        Config.INSTANCE.autoDetectHyperions = true;
        Config.INSTANCE.ultimateWiseSlot = 9; Config.INSTANCE.chimeraSlot = 9;
        t.check(HyperionAccess.find(c.player, HyperionPolicy.Kind.ULTIMATE_WISE) == 0
                && HyperionAccess.find(c.player, HyperionPolicy.Kind.CHIMERA) == 1, "auto detection ignores obsolete manual slot assignments");
        inv.setItem(0, chimera); inv.setItem(1, wise);
        t.check(HyperionAccess.find(c.player, HyperionPolicy.Kind.ULTIMATE_WISE) == 1
                && HyperionAccess.find(c.player, HyperionPolicy.Kind.CHIMERA) == 0, "moving both Hyperions updates their detected roles");
        inv.setSelectedSlot(1);
        t.check(!HyperionAccess.held(c.player, HyperionPolicy.Kind.CHIMERA), "delayed Chimera action refuses a slot now holding Ultimate Wise");
        inv.setItem(0, ItemStack.EMPTY);
        t.check(!HyperionAccess.validPair(c.player)
                && HyperionAccess.find(c.player, HyperionPolicy.Kind.CHIMERA) < 0, "missing Chimera never falls back to Ultimate Wise");
        inv.setItem(0, wise); inv.setItem(1, chimera);
        Config.INSTANCE.ultimateWiseSlot = 1; Config.INSTANCE.chimeraSlot = 2;

        var nested = wise.copy(); var root = new CompoundTag();
        root.put("ExtraAttributes", wise.get(DataComponents.CUSTOM_DATA).copyTag());
        nested.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        t.check(HyperionAccess.matches(nested, HyperionPolicy.Kind.ULTIMATE_WISE), "nested ExtraAttributes enchantments supported");
        var loreOnly = SequenceSmokeChecks.named("Heroic Hyperion");
        loreOnly.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§d§lUltimate Wise V§9, Sharpness VII"))));
        t.check(HyperionAccess.matches(loreOnly, HyperionPolicy.Kind.ULTIMATE_WISE), "colored multi-enchantment lore fallback identifies Ultimate Wise");
        loreOnly.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Sharpness VII,  §d§lChimera V "))));
        t.check(HyperionAccess.matches(loreOnly, HyperionPolicy.Kind.CHIMERA), "Chimera lore fallback supports comma lists and whitespace");
        loreOnly.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Chimera 5"))));
        t.check(HyperionAccess.matches(loreOnly, HyperionPolicy.Kind.CHIMERA), "numeric lore enchantment level supported");
        loreOnly.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Bonus when using Chimera V"))));
        t.check(HyperionAccess.detectedKind(loreOnly) == null, "descriptive lore is not an enchantment");
        var renamed = SequenceSmokeChecks.named("Chimera Hyperion");
        t.check(HyperionAccess.detectedKind(renamed) == null, "type is never inferred from renamed display name");
        loreOnly.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Chimera V, Ultimate Wise V"))));
        t.check(HyperionAccess.detectedKind(loreOnly) == null, "contradictory lore rejected");
        var conflicting = chimera.copy(); var attrs = PetMenus.attributes(conflicting);
        attrs.getCompoundOrEmpty("enchantments").putInt("ultimate_wise", 5);
        conflicting.set(DataComponents.CUSTOM_DATA, CustomData.of(attrs));
        t.check(HyperionAccess.detectedKind(conflicting) == null, "contradictory custom-data enchantments rejected");
        var authoritative = wise.copy();
        authoritative.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Chimera V"))));
        t.check(HyperionAccess.matches(authoritative, HyperionPolicy.Kind.ULTIMATE_WISE), "server item data takes precedence over misleading lore");
        attrs = PetMenus.attributes(authoritative); attrs.putString("id", "ASPECT_OF_THE_END");
        authoritative.set(DataComponents.CUSTOM_DATA, CustomData.of(attrs));
        t.check(HyperionAccess.detectedKind(authoritative) == null, "non-Hyperion item ID rejected even with Hyperion display name");
        Config.INSTANCE.twoHyperions = false;
        t.check(HyperionAccess.find(c.player, HyperionPolicy.Kind.CHIMERA) == 0, "explicit single-Hyperion mode retains its existing behavior");
        Config.INSTANCE.twoHyperions = true;
        System.out.println("SMOKE_HYPERION_DETECTION_OK: automatic moved slots, removal, root/nested NBT, lore, contradictions, manual and single modes");
    }
}
