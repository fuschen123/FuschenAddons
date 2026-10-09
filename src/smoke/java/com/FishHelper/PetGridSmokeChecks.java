package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.List;
import java.util.UUID;

/** Real menu observation -> saved config -> grid selection, with deliberately nonalphabetic fixtures. */
final class PetGridSmokeChecks {
    static ItemStack pet(String id, String name) {
        var stack = SequenceSmokeChecks.pet(id, false);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("[Lvl 100] " + name));
        return stack;
    }
    static ItemStack favorite(ItemStack pet, String marker) {
        var copy = pet.copy();
        copy.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + marker + " §7" + PetMenus.plain(pet.getHoverName().getString())));
        return copy;
    }
    static Button button(PetSelectionScreen screen, String text) {
        return screen.children().stream().filter(e -> e instanceof Button b && b.getMessage().getString().contains(text))
                .map(e -> (Button)e).findFirst().orElseThrow();
    }
    static void click(PetSelectionScreen screen, Button button) {
        var event = SmokeClient.mouse(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
        screen.mouseClicked(event, false); screen.mouseReleased(event);
    }
    static void prepare(SmokeClient t, Minecraft c) throws Exception {
        Config.INSTANCE.petPages.clear(); Config.INSTANCE.petMenuPages.clear();
        var menu = SequenceSmokeChecks.pets(c, "Pets (2/3)");
        menu.slots.get(10).set(pet("cccccccccccccccccccccccccccccccc", "Zebra"));
        menu.slots.get(12).set(pet("dddddddddddddddddddddddddddddddd", "Ammonite"));
        for (int i = 0; i < 3; i++) PetMenus.observe(c);
        menu = SequenceSmokeChecks.pets(c, "(1/3) Pets");
        var head = pet("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Flying Fish");
        var originalIdentity = PetMenus.read(head);
        for (String marker : List.of("⭐", "★", "⭐\uFE0F"))
            t.check(originalIdentity.equals(PetMenus.read(favorite(head, marker))), "favorite marker preserves UUID, name and metadata: " + marker);
        var noUuid = head.copy(); noUuid.remove(DataComponents.CUSTOM_DATA);
        t.check(PetMenus.read(noUuid).matches(PetMenus.read(favorite(noUuid, "⭐"))), "favorite toggle preserves descriptive identity without UUID");
        var unrelated = head.copy(); unrelated.set(DataComponents.CUSTOM_NAME, Component.literal("Not a pet [Lvl 100] Flying Fish"));
        t.check(PetMenus.read(unrelated) == null, "arbitrary name prefixes are still rejected");
        head = favorite(head, "⭐");
        var properties = com.google.common.collect.ArrayListMultimap.<String, com.mojang.authlib.properties.Property>create();
        String texture = java.util.Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        properties.put("textures", new com.mojang.authlib.properties.Property("textures", texture));
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "fixture",
                new com.mojang.authlib.properties.PropertyMap(properties))));
        menu.slots.get(10).set(head);
        menu.slots.get(12).set(pet("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "Flying Fish"));
        menu.slots.get(19).set(pet("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "Dolphin"));
        menu.slots.get(43).set(pet("ffffffffffffffffffffffffffffffff", "Squid"));
        Config.INSTANCE.selectedPet = PetMenus.read(menu.slots.get(12).getItem());
        for (int i = 0; i < 3; i++) PetMenus.observe(c);
        Config.load();
        t.check(PetMenus.page(1).stream().map(PetMenuSlot::slot).toList().equals(List.of(10, 12, 19, 43)), "persisted grid preserves empty slots and final row");
        t.check(PetMenus.page(2).stream().map(e -> e.pet().name()).toList().equals(List.of("Zebra", "Ammonite")), "page order is menu order, not alphabetical");
        t.check(PetMenus.catalog().getFirst().id().endsWith("aaaaaaaa"), "pages ordered numerically even when observed in reverse");
        t.check(PetMenus.catalog().stream().filter(p -> p.matches(originalIdentity)).count() == 1, "favorite appears once in saved catalog under its clean name");
        var profile = PetMenus.page(1).getFirst().icon().get(DataComponents.PROFILE);
        t.check(profile != null && profile.partialProfile().properties().get("textures").iterator().next().value().equals(texture), "head texture survives menu capture and config reload");
        t.check(!new PetMenuSlot(10, Config.INSTANCE.selectedPet, "invalid json").icon().isEmpty(), "invalid icon safely uses a fallback head");
        c.player.closeContainer();
        c.setScreen(new PetSelectionScreen(null));
    }
    static void verify(SmokeClient t, Minecraft c) throws Exception {
        var screen = (PetSelectionScreen)c.screen;
        Button a = button(screen, "…aaaaaa"), b = button(screen, "…bbbbbb"), dolphin = button(screen, "Dolphin"), squid = button(screen, "Squid");
        t.check(b.getX() - a.getX() == 2 * (a.getWidth() + 4) && b.getY() == a.getY(), "gap retained between duplicate-name heads");
        t.check(dolphin.getX() == a.getX() && dolphin.getY() - a.getY() == a.getWidth() + 4, "next menu row displayed below the first");
        t.check(squid.getX() > b.getX() && squid.getY() > dolphin.getY(), "slot 43 stays at bottom right");
        click(screen, a);
        Config.load(); t.check(Config.INSTANCE.selectedPet.id().endsWith("aaaaaaaa"), "visual selection distinguishes duplicate names and persists");
        click(screen, button(screen, "›"));
        t.check(button(screen, "Zebra").getX() < button(screen, "Ammonite").getX(), "page navigation retains nonalphabetical menu order");
        screen.mouseScrolled(0, 0, 0, -1);
        t.check(screen.children().stream().noneMatch(e -> e instanceof Button b2 && b2.getMessage().getString().contains("Held item:")), "unread page creates no invented pet buttons");
        screen.mouseScrolled(0, 0, 0, 1); screen.mouseScrolled(0, 0, 0, 1);
        // Compact layout must retain all controls, icon targets and selection.
        screen.init(320, 240);
        for (var widget : screen.children()) if (widget instanceof Button b3)
            t.check(b3.getX() >= 0 && b3.getRight() <= 320 && b3.getY() >= 0 && b3.getBottom() <= 240, "compact grid keeps all controls on screen");
        t.check(Config.INSTANCE.selectedPet.id().endsWith("aaaaaaaa"), "page changes and resize preserve selected pet");
        System.out.println("SMOKE_FAVORITE_PETS_OK: colored star prefixes, UUID/fallback identity, observed grid positions, saved catalog and visual selection");
        screen.init(c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight());
    }
}
