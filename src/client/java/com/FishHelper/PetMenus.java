package com.FishHelper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;
import java.util.regex.Pattern;

/** Reads only actual server-menu stacks. Never scans or fabricates unopened pages. */
public final class PetMenus {
    public record Entry(int slot, PetIdentity pet, boolean active, boolean summon) { }
    private static final Pattern LEVEL = Pattern.compile("^\\[Lvl?\\s*(\\d+)]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE = Pattern.compile("\\((\\d+)\\s*/\\s*(\\d+)\\)");
    private static String previous = "";
    private static int stableTicks;
    private PetMenus() { }
    static String plain(String value) { return value.replaceAll("§.", "").strip(); }
    public static boolean title(String text) {
        return plain(text).matches("(?i)(?:\\(\\d+/\\d+\\)\\s*)?Pets(?:\\s*\\(\\d+/\\d+\\))?");
    }
    static AbstractContainerScreen<?> screen(Minecraft client) {
        return client.player != null && client.screen instanceof AbstractContainerScreen<?> screen
                && title(screen.getTitle().getString()) && screen.getMenu() == client.player.containerMenu
                && screen.getMenu().slots.size() >= 54 ? screen : null;
    }
    static List<Entry> entries(Minecraft client) {
        var screen = screen(client);
        if (screen == null) return List.of();
        List<Entry> result = new ArrayList<>();
        for (int slot = 10; slot <= 43; slot++) {
            if (slot % 9 == 0 || slot % 9 == 8) continue;
            var menuSlot = screen.getMenu().slots.get(slot);
            if (menuSlot.container == client.player.getInventory()) continue;
            ItemStack stack = menuSlot.getItem();
            PetIdentity pet = read(stack);
            if (pet == null) continue;
            String lore = lore(stack).toLowerCase(Locale.ROOT);
            boolean active = lore.contains("click to despawn");
            boolean summon = lore.contains("click to summon") || lore.contains("click to spawn");
            if (active || summon) result.add(new Entry(slot, pet, active, summon));
        }
        return result;
    }
    static String lore(ItemStack stack) {
        return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
                .map(line -> plain(line.getString())).collect(java.util.stream.Collectors.joining("\n"));
    }
    static CompoundTag attributes(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return root.getCompound("ExtraAttributes").orElse(root);
    }
    static PetIdentity read(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var name = LEVEL.matcher(plain(stack.getHoverName().getString()));
        if (!name.matches()) return null;
        try {
            CompoundTag attributes = attributes(stack);
            JsonObject info = new JsonObject();
            String raw = attributes.getStringOr("petInfo", "");
            if (!raw.isBlank()) try { info = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException ignored) { }
            String uuid = string(info, "uuid", attributes.getStringOr("uuid", ""));
            String rarity = string(info, "tier", "Unknown");
            String item = string(info, "heldItem", "Unknown");
            for (String line : lore(stack).split("\n")) {
                if (line.startsWith("Held Item: ")) item = line.substring(11).strip();
                if (rarity.equals("Unknown")) {
                    var r = Pattern.compile("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE)(?: .*PET| PET)?$").matcher(line);
                    if (r.matches()) rarity = r.group(1);
                }
            }
            if (info.has("heldItem") && info.get("heldItem").isJsonNull()) item = "None";
            String id = uuid.matches("[a-fA-F0-9-]{32,36}") ? "uuid:" + uuid.replace("-", "").toLowerCase(Locale.ROOT) : "";
            return new PetIdentity(id, name.group(2), rarity, Integer.parseInt(name.group(1)), item);
        } catch (RuntimeException ignored) { return null; }
    }
    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
    static void observe(Minecraft client) {
        var screen = screen(client);
        if (screen == null) { previous = ""; stableTicks = 0; return; }
        List<PetIdentity> pets = entries(client).stream().map(Entry::pet).toList();
        if (pets.isEmpty()) return; // Do not replace a cached page with an in-flight empty packet.
        var page = PAGE.matcher(plain(screen.getTitle().getString()));
        int number = 1, count = 1;
        if (page.find()) { number = Integer.parseInt(page.group(1)); count = Integer.parseInt(page.group(2)); }
        String snapshot = screen.getMenu().containerId + ":" + number + ":" + pets;
        if (!snapshot.equals(previous)) { previous = snapshot; stableTicks = 0; }
        if (++stableTicks < 3) return;
        if (!pets.equals(Config.INSTANCE.petPages.get(number)) || Config.INSTANCE.petPageCount != count) {
            Config.INSTANCE.petPages.put(number, pets);
            Config.INSTANCE.petPageCount = count;
            final int pages = count;
            Config.INSTANCE.petPages.keySet().removeIf(n -> n > pages);
            Config.save();
        }
    }
    public static List<PetIdentity> catalog() {
        Map<String, PetIdentity> pets = new LinkedHashMap<>();
        Config.INSTANCE.petPages.values().forEach(page -> page.forEach(p -> pets.put(p.stable() ? p.id() : p.fallback(), p)));
        return List.copyOf(pets.values());
    }
    public static boolean selectable(PetIdentity wanted) {
        if (wanted.stable()) return true;
        // Without an item UUID we require every known page and exactly one descriptive match.
        for (int page = 1; page <= Config.INSTANCE.petPageCount; page++)
            if (!Config.INSTANCE.petPages.containsKey(page)) return false;
        return Config.INSTANCE.petPages.values().stream().flatMap(List::stream).filter(wanted::matches).count() == 1;
    }
    static Entry resolve(Minecraft client) {
        PetIdentity selected = Config.INSTANCE.selectedPet;
        if (selected == null || !selectable(selected)) return null;
        List<Entry> current = entries(client);
        int index = PetIdentity.uniqueIndex(selected, current.stream().map(Entry::pet).toList());
        return index < 0 ? null : current.get(index);
    }
    static PetSwapSequence.Controls controls(Minecraft client) {
        return new PetSwapSequence.Controls() {
            public PetSwapSequence.Menu menu() {
                return screen(client) != null ? PetSwapSequence.Menu.PETS : client.screen == null
                        ? PetSwapSequence.Menu.NONE : PetSwapSequence.Menu.OTHER;
            }
            public boolean hookReady() { return OwnedHookResolver.find(client, client.player) != null; }
            public PetSwapSequence.Selection selection() {
                Entry found = resolve(client);
                return found == null ? PetSwapSequence.Selection.MISSING : found.active ? PetSwapSequence.Selection.ACTIVE
                        : found.summon ? PetSwapSequence.Selection.SUMMON : PetSwapSequence.Selection.MISSING;
            }
            public void open() { client.player.connection.sendCommand("pets"); }
            public void click() {
                // Slot is resolved from the live stack immediately before transmitting the click.
                Entry found = resolve(client);
                var current = screen(client);
                if (current != null && found != null && !found.active && found.summon && client.gameMode != null)
                    client.gameMode.handleContainerInput(current.getMenu().containerId, found.slot, 0, ContainerInput.PICKUP, client.player);
            }
            public void closeOwnedMenu() { if (screen(client) != null) client.player.closeContainer(); }
            public void navigateKnownPage() {
                var current = screen(client);
                var wanted = Config.INSTANCE.selectedPet;
                if (current == null || wanted == null || !selectable(wanted)) return;
                var titlePage = PAGE.matcher(plain(current.getTitle().getString()));
                int page = titlePage.find() ? Integer.parseInt(titlePage.group(1)) : 1;
                var knownPages = Config.INSTANCE.petPages.entrySet().stream()
                        .filter(entry -> entry.getValue().stream().anyMatch(wanted::matches)).map(Map.Entry::getKey).toList();
                if (knownPages.size() != 1 || knownPages.getFirst() == page) return;
                String arrow = knownPages.getFirst() > page ? "Next Page" : "Previous Page";
                int found = -1;
                for (int i = 0; i < 54; i++) {
                    var slot = current.getMenu().slots.get(i);
                    if (slot.container == client.player.getInventory()) continue;
                    if (plain(slot.getItem().getHoverName().getString()).equalsIgnoreCase(arrow)) {
                        if (found >= 0) return;
                        found = i;
                    }
                }
                if (found >= 0 && client.gameMode != null)
                    client.gameMode.handleContainerInput(current.getMenu().containerId, found, 0, ContainerInput.PICKUP, client.player);
            }
        };
    }
}
