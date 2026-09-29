package com.FishHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FishHelperClient implements ClientModInitializer {
    private static final String STATUS_PREFIX = "§7[§5Fuschen§dAddons§7]";
    private static final int BOBBER_TIMER_TICKS = 10 * 20;
    private static final Pattern HOOK_TIMER_SECONDS = Pattern.compile("(?:^|\\s)(\\d+(?:[.,]\\d+)?)\\s*(?:s|sec|seconds)?(?:\\s|$)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> STOP_MESSAGES = Set.of(
            "A Fiery Scuttler inconspicuously waddles up to you, friends in tow.",
            "You hear a massive rumble as Thunder emerges.",
            "You have angered a legendary creature... Lord Jawbus has arrived.",
            "WOAH! A Plhlegblast appeared.",
            "The sky darkens and the air thickens. The end times are upon us: Ragnarok is here."
    );

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("tsclient", "main")
    );

    private static KeyMapping TOGGLE_KEY;
    private static KeyMapping CONFIG_KEY;

    private static boolean enabled = false;
    private static boolean paused = false;
    private static boolean biteAlertHandled = false;
    private static boolean bobberTrackingInitialized = false;
    private static boolean bobberWasActive = false;
    private static int bobberActiveTicks = 0;
    private static UUID trackedBobberUuid;
    private static int recastCheckTimer = 8 * 20;
    private static int flarePlacementCooldown = 0;
    private static boolean sosFlarePending = false;
    private static int fishingAction = -1; // -1 idle; 0-6 bite/pet sequence; 7-9 magma-cube recovery
    private static int actionTimer = 0;
    private static int petMenuWaitTicks = 0;
    private static int petMenuStableTicks = 0;
    private static int handledMagmaCubeId = -1;
    private static InteractionHand rodHandForAction;
    private static int rodSelectedSlotForAction = -1;
    private static int hyperionSlotForAction = -1;
    private static UUID trackedHotspotUuid;
    private static ClientLevel trackedHotspotLevel;
    private static ClientLevel observedWorldLevel;
    private static boolean worldLevelObservationInitialized = false;
    private static UUID pendingHotspotUnloadUuid;
    private static int pendingHotspotUnloadTicks;
    private static int hotspotRadarStage;
    private static int hotspotRadarTimer;
    private static int hotspotRadarSlot = -1;
    private static int hotspotRadarRestoreSlot = -1;
    private static UUID grinchClickTargetUuid;
    private static long nextGrinchClickAt;

    @Override
    public void onInitializeClient() {
        Config.load();

        TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.tsclient.toggle",
                        InputConstants.Type.KEYSYM,
                        Config.INSTANCE.toggleKeyCode,
                        CATEGORY
                )
        );
        CONFIG_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.tsclient.config",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_P,
                        CATEGORY
                )
        );

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("fuschen")
                    .executes(context -> {
                        openConfigMenu();
                        return 1;
                    }));
            dispatcher.register(ClientCommands.literal("fa")
                    .executes(context -> {
                        openConfigMenu();
                        return 1;
                    }));
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (enabled && entity instanceof ArmorStand stand
                    && trackedHotspotUuid != null
                    && trackedHotspotUuid.equals(stand.getUUID())
                    && trackedHotspotLevel == level
                    && player != null
                    && stand.distanceTo(player) <= 30.0) {
                // Delay the stop briefly so dimension changes can clear the tracked world first.
                pendingHotspotUnloadUuid = stand.getUUID();
                pendingHotspotUnloadTicks = 3;
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (enabled && !paused && STOP_MESSAGES.contains(message.getString().strip())) {
                switchToHyperionForRareCreature(Minecraft.getInstance().player);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!worldLevelObservationInitialized) {
                observedWorldLevel = client.level;
                worldLevelObservationInitialized = true;
            } else if (observedWorldLevel != client.level) {
                observedWorldLevel = client.level;
                if (enabled) {
                    pauseForWorldChange(client.player);
                }
            }

            if (trackedHotspotLevel != null && trackedHotspotLevel != client.level) {
                clearTrackedHotspot();
                if (hotspotRadarStage > 0 && client.player != null) {
                    restoreHotspotRadarSlot(client.player);
                }
            }

            while (TOGGLE_KEY.consumeClick()) {
                if (hotspotRadarStage > 0 && client.player != null && hotspotRadarRestoreSlot >= 0) {
                    client.player.getInventory().setSelectedSlot(hotspotRadarRestoreSlot);
                }
                hotspotRadarStage = 0;
                hotspotRadarTimer = 0;
                hotspotRadarSlot = -1;
                hotspotRadarRestoreSlot = -1;
                if (enabled && paused) {
                    paused = false;
                } else {
                    enabled = !enabled;
                    paused = false;
                }
                biteAlertHandled = false;
                bobberTrackingInitialized = false;
                bobberWasActive = false;
                bobberActiveTicks = 0;
                trackedBobberUuid = null;
                fishingAction = -1;
                actionTimer = 0;
                petMenuWaitTicks = 0;
                handledMagmaCubeId = -1;
                rodHandForAction = null;
                rodSelectedSlotForAction = -1;
                hyperionSlotForAction = -1;
                recastCheckTimer = 8 * 20;
                flarePlacementCooldown = 0;
                sosFlarePending = false;
                clearTrackedHotspot();

                if (client.player != null) {
                    client.player.sendSystemMessage(
                            Component.literal(STATUS_PREFIX + " " + (enabled ? "§aON" : "§cOFF")));
                }
            }

            while (CONFIG_KEY.consumeClick()) {
                openConfigMenu();
            }

            if (!enabled || paused || client.player == null || client.level == null || client.gameMode == null) {
                return;
            }

            var player = client.player;
            if (pendingHotspotUnloadUuid != null) {
                if (client.level != trackedHotspotLevel
                        || !pendingHotspotUnloadUuid.equals(trackedHotspotUuid)) {
                    pendingHotspotUnloadUuid = null;
                    pendingHotspotUnloadTicks = 0;
                } else if (--pendingHotspotUnloadTicks <= 0) {
                    pendingHotspotUnloadUuid = null;
                    pendingHotspotUnloadTicks = 0;
                    beginHotspotRadarAction(player);
                    return;
                }
            }
            if (hotspotRadarStage > 0) {
                if (hotspotRadarTimer > 0 && --hotspotRadarTimer > 0) {
                    return;
                }
                if (hotspotRadarStage == 1) {
                    player.getInventory().setSelectedSlot(hotspotRadarSlot);
                    hotspotRadarStage = 2;
                    hotspotRadarTimer = randomSwapDelayTicks();
                } else if (hotspotRadarStage == 2) {
                    if (hotspotRadarSlot < 0 || !isHotspotRadar(player.getInventory().getItem(hotspotRadarSlot))) {
                        restoreHotspotRadarSlot(player);
                        stopForHotspotGone(player);
                    } else {
                        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                        hotspotRadarStage = 3;
                        hotspotRadarTimer = 1;
                    }
                } else {
                    restoreHotspotRadarSlot(player);
                    stopForHotspotGone(player);
                }
                return;
            }
            if (flarePlacementCooldown > 0) {
                flarePlacementCooldown--;
            }
            FishingHook activeBobber = findOwnedBobber(client, player);
            trackHotspot(client, player, activeBobber);
            boolean bobberPresent = activeBobber != null;
            boolean newBobber = bobberPresent && !activeBobber.getUUID().equals(trackedBobberUuid);
            if (newBobber) {
                trackedBobberUuid = activeBobber.getUUID();
                bobberActiveTicks = 1;
                biteAlertHandled = false;
            } else if (bobberPresent) {
                if (bobberActiveTicks < BOBBER_TIMER_TICKS) {
                    bobberActiveTicks++;
                }
            } else {
                trackedBobberUuid = null;
                bobberActiveTicks = 0;
            }
            if (!bobberTrackingInitialized) {
                bobberWasActive = bobberPresent;
                bobberTrackingInitialized = true;
            } else if (!bobberPresent) {
                bobberWasActive = false;
            } else if (newBobber || !bobberWasActive) {
                bobberWasActive = true;
                if (fishingAction < 0) {
                    fishingAction = 5;
                    actionTimer = ThreadLocalRandom.current().nextInt(2, 5);
                    petMenuWaitTicks = 0;
                }
            }

            Entity hookedEntity = activeBobber == null ? null : activeBobber.getHookedIn();
            tickGrinchAutoClicker(client, player, hookedEntity);
            if (!(hookedEntity instanceof MagmaCube)) {
                handledMagmaCubeId = -1;
            } else if (fishingAction < 0 && activeBobber != null
                    && handledMagmaCubeId != hookedEntity.getId()) {
                handledMagmaCubeId = hookedEntity.getId();
                InteractionHand heldRodHand = player.getMainHandItem().is(Items.FISHING_ROD)
                        ? InteractionHand.MAIN_HAND
                        : player.getOffhandItem().is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : null;
                int hypSlot = Config.INSTANCE.useHyperion ? findHyperionHotbarSlot(player) : -1;
                if (heldRodHand != null && hypSlot >= 0) {
                    rodHandForAction = heldRodHand;
                    rodSelectedSlotForAction = player.getInventory().getSelectedSlot();
                    hyperionSlotForAction = hypSlot;
                    fishingAction = 7;
                    actionTimer = 4; // the action timer is decremented later in this same tick
                } else if (Config.INSTANCE.useHyperion) {
                    player.sendSystemMessage(Component.literal(
                            STATUS_PREFIX + " magma-cube recovery skipped (rod or Hyperion not found)"));
                }
            }

            // Finish an active bite sequence before checking the currently held item.
            // During this sequence the main hand intentionally holds the Hyperion.
            if (fishingAction >= 0) {
                if (actionTimer > 0) {
                    actionTimer--;
                    if (actionTimer > 0) {
                        return;
                    }
                }

                if (fishingAction == 0) {
                    client.gameMode.useItem(player, rodHandForAction);
                    player.swing(rodHandForAction);

                    fishingAction = hyperionSlotForAction >= 0 ? 1 : 3;
                    actionTimer = hyperionSlotForAction >= 0
                            ? randomSwapDelayTicks()
                            : ThreadLocalRandom.current().nextInt(4, 6);
                } else if (fishingAction == 1) {
                    player.getInventory().setSelectedSlot(hyperionSlotForAction);
                    fishingAction = 4;
                    actionTimer = randomSwapDelayTicks();
                } else if (fishingAction == 4) {
                    client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    sosFlarePending = true;

                    fishingAction = 2;
                    actionTimer = randomSwapDelayTicks();
                } else if (fishingAction == 2) {
                    if (rodSelectedSlotForAction >= 0) {
                        player.getInventory().setSelectedSlot(rodSelectedSlotForAction);
                    }
                    fishingAction = 3;
                    // Let the catch-triggered AutoPet rule settle for 4–5 ticks before casting.
                    actionTimer = ThreadLocalRandom.current().nextInt(4, 6);
                } else if (fishingAction == 3) {
                    if (rodHandForAction == InteractionHand.MAIN_HAND && rodSelectedSlotForAction >= 0) {
                        player.getInventory().setSelectedSlot(rodSelectedSlotForAction);
                    }
                    client.gameMode.useItem(player, rodHandForAction);
                    player.swing(rodHandForAction);
                    bobberWasActive = true;
                    bobberTrackingInitialized = true;
                    fishingAction = 5;
                    actionTimer = ThreadLocalRandom.current().nextInt(2, 5);
                    petMenuWaitTicks = 0;
                } else if (fishingAction == 5) {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen
                            && isPetsMenu(screen.getTitle().getString()))) {
                        player.connection.sendCommand("pets");
                        player.sendSystemMessage(Component.literal(STATUS_PREFIX + " sending /pets"));
                    }
                    fishingAction = 6;
                    actionTimer = ThreadLocalRandom.current().nextInt(2, 4);
                    petMenuStableTicks = 0;
                } else if (fishingAction == 7) {
                    if (activeBobber == null || !(activeBobber.getHookedIn() instanceof MagmaCube)) {
                        finishFishingAction();
                    } else {
                        player.getInventory().setSelectedSlot(hyperionSlotForAction);
                        fishingAction = 8;
                        actionTimer = randomSwapDelayTicks();
                    }
                } else if (fishingAction == 8) {
                    client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    sosFlarePending = true;
                    fishingAction = 9;
                    actionTimer = randomSwapDelayTicks();
                } else if (fishingAction == 9) {
                    if (rodSelectedSlotForAction >= 0) {
                        player.getInventory().setSelectedSlot(rodSelectedSlotForAction);
                    }
                    fishingAction = 10;
                    actionTimer = randomSwapDelayTicks();
                } else if (fishingAction == 10) {
                    // Switching to Hyperion removes the old bobber; cast directly again.
                    client.gameMode.useItem(player, rodHandForAction);
                    player.swing(rodHandForAction);
                    bobberWasActive = true;
                    bobberTrackingInitialized = true;
                    finishFishingAction();
                } else if (fishingAction == 11) {
                    player.getInventory().setSelectedSlot(hyperionSlotForAction);
                    fishingAction = 12;
                    actionTimer = randomSwapDelayTicks();
                } else if (fishingAction == 12) {
                    if (hasNearbyFlareOrPlasmaflux(client, player)) {
                        if (rodSelectedSlotForAction >= 0) {
                            player.getInventory().setSelectedSlot(rodSelectedSlotForAction);
                        }
                        finishFishingAction();
                    } else {
                        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                        flarePlacementCooldown = 3 * 60 * 20;
                        fishingAction = 13;
                        actionTimer = 2;
                    }
                } else if (fishingAction == 13) {
                    if (rodSelectedSlotForAction >= 0) {
                        player.getInventory().setSelectedSlot(rodSelectedSlotForAction);
                    }
                    client.gameMode.useItem(player, rodHandForAction);
                    player.swing(rodHandForAction);
                    bobberWasActive = true;
                    bobberTrackingInitialized = true;
                    finishFishingAction();
                } else {
                    int petMenuSlot = 9 + Math.max(1, Math.min(7, Config.INSTANCE.petNumber));
                    if (client.screen instanceof AbstractContainerScreen<?> screen
                            && isPetsMenu(screen.getTitle().getString())
                            && screen.getMenu().slots.size() > petMenuSlot) {
                        if (++petMenuStableTicks < 3) {
                            actionTimer = 1;
                        } else {
                            var petStack = screen.getMenu().slots.get(petMenuSlot).getItem();
                            if (!hasClickToDespawnTooltip(petStack, client, player)) {
                                client.gameMode.handleContainerInput(
                                        screen.getMenu().containerId,
                                        petMenuSlot,
                                        0,
                                        ContainerInput.PICKUP,
                                        player
                                );
                            }
                            client.setScreen(null);
                            finishFishingAction();
                        }
                    } else if (++petMenuWaitTicks >= 20) {
                        if (client.screen instanceof AbstractContainerScreen<?> screen
                                && isPetsMenu(screen.getTitle().getString())) {
                            client.setScreen(null);
                        }
                        finishFishingAction();
                    } else {
                        petMenuStableTicks = 0;
                        actionTimer = 1;
                    }
                }
                return;
            }

            InteractionHand rodHand;
            if (player.getMainHandItem().is(Items.FISHING_ROD)) {
                rodHand = InteractionHand.MAIN_HAND;
            } else if (player.getOffhandItem().is(Items.FISHING_ROD)) {
                rodHand = InteractionHand.OFF_HAND;
            } else {
                return;
            }

            FishingHook bobber = activeBobber;

            // The all-fish ping option follows Hypixel's visible timer ArmorStand near the bobber.
            // Slugfish keeps its separate bobber-age based timing option.
            boolean biteAlertVisible = false;
            if (bobber != null) {
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (entity instanceof ArmorStand stand
                            && stand.hasCustomName()
                            && stand.distanceTo(bobber) < 2.0
                            && stand.getCustomName().getString().contains("!!!")) {
                        biteAlertVisible = true;
                        break;
                    }
                }
            }

            int pingMs = Math.max(0, Math.min(5000, Config.INSTANCE.reelPingMs));
            int slugfishMinimumAgeTicks = Math.max(0, (10_000 - pingMs + 49) / 50);
            boolean slugfishWaitComplete = bobber != null
                    && bobberActiveTicks >= slugfishMinimumAgeTicks;
            boolean pingTimedReel = Config.INSTANCE.reelInUsingPing
                    && isHookTimerWithinPing(client, bobber, pingMs);
            boolean reelSignal = biteAlertVisible || pingTimedReel;
            // When Slugfish is enabled, either reel signal must wait until 10s minus configured ping.
            boolean shouldReel = reelSignal
                    && (!Config.INSTANCE.slugfishReelEnabled || slugfishWaitComplete);
            if (bobber == null) {
                biteAlertHandled = false;
            } else if (!biteAlertHandled && shouldReel) {
                biteAlertHandled = true;
                rodHandForAction = rodHand;
                rodSelectedSlotForAction = player.getInventory().getSelectedSlot();
                hyperionSlotForAction = Config.INSTANCE.useHyperion ? findHyperionHotbarSlot(player) : -1;
                fishingAction = 0;
                actionTimer = 1;
                return;
            }

            // Wait until the current rod/Hyperion/pet sequence is finished. A flare is
            // queued only after a Hyperion use and placed on a later idle tick.
            if (sosFlarePending && flarePlacementCooldown == 0
                    && Config.INSTANCE.flareTier != Config.FlareTier.NONE
                    && !hasNearbyFlareOrPlasmaflux(client, player)) {
                int flareSlot = findFlareHotbarSlot(player);
                if (flareSlot >= 0) {
                    rodHandForAction = rodHand;
                    rodSelectedSlotForAction = player.getInventory().getSelectedSlot();
                    hyperionSlotForAction = flareSlot;
                    fishingAction = 11;
                    actionTimer = randomSwapDelayTicks();
                    sosFlarePending = false;
                    return;
                }
            }

            // Every 8 seconds, recast if the bobber is gone.
            recastCheckTimer--;
            if (recastCheckTimer <= 0) {
                recastCheckTimer = 8 * 20;
                if (bobber == null) {
                    client.gameMode.useItem(player, rodHand);
                    player.swing(rodHand);
                }
            }
        });
    }

    private static int findHyperionHotbarSlot(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                String name = stack.getHoverName().getString()
                        .replaceAll("(?i)§[0-9A-FK-OR]", "")
                        .toLowerCase(java.util.Locale.ROOT);
                if (name.contains("hyperion")) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private static int findFlareHotbarSlot(LocalPlayer player) {
        String search = Config.INSTANCE.flareTier.searchName;
        if (search.isEmpty()) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                String name = stack.getHoverName().getString()
                        .replaceAll("(?i)§[0-9A-FK-OR]", "")
                        .toLowerCase(java.util.Locale.ROOT);
                if (name.contains(search)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private static int findHotspotRadarHotbarSlot(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (isHotspotRadar(player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isHotspotRadar(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = stack.getHoverName().getString()
                .replaceAll("(?i)§[0-9A-FK-OR]", "")
                .toLowerCase(java.util.Locale.ROOT);
        return name.contains("hotspot radar");
    }

    private static void beginHotspotRadarAction(LocalPlayer player) {
        int radarSlot = findHotspotRadarHotbarSlot(player);
        if (radarSlot < 0) {
            stopForHotspotGone(player);
            return;
        }

        hotspotRadarRestoreSlot = fishingAction >= 0 && rodSelectedSlotForAction >= 0
                ? rodSelectedSlotForAction
                : player.getInventory().getSelectedSlot();
        hotspotRadarSlot = radarSlot;
        hotspotRadarStage = 1;
        hotspotRadarTimer = 0;

        // The hotspot has ended, so discard any partially completed fishing action.
        fishingAction = -1;
        actionTimer = 0;
        petMenuWaitTicks = 0;
        rodHandForAction = null;
        rodSelectedSlotForAction = -1;
        hyperionSlotForAction = -1;
        sosFlarePending = false;
    }

    private static void restoreHotspotRadarSlot(LocalPlayer player) {
        if (hotspotRadarRestoreSlot >= 0) {
            player.getInventory().setSelectedSlot(hotspotRadarRestoreSlot);
        }
        hotspotRadarStage = 0;
        hotspotRadarTimer = 0;
        hotspotRadarSlot = -1;
        hotspotRadarRestoreSlot = -1;
    }

    private static boolean hasNearbyFlareOrPlasmaflux(
            net.minecraft.client.Minecraft client,
            LocalPlayer player
    ) {
        String flareSearch = Config.INSTANCE.flareTier.searchName;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand
                    && stand.hasCustomName()
                    && stand.distanceTo(player) <= 40.0) {
                String name = stand.getCustomName().getString()
                        .replaceAll("(?i)§[0-9A-FK-OR]", "")
                        .toLowerCase(java.util.Locale.ROOT);
                if ((!flareSearch.isEmpty() && name.contains(flareSearch))
                        || (name.contains("plasmaflux") && name.contains("power orb"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void trackHotspot(
            Minecraft client,
            LocalPlayer player,
            FishingHook bobber
    ) {
        if (bobber == null || !bobber.isInWater()) {
            return;
        }

        ArmorStand closestHotspot = null;
        double closestDistance = 5.0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)
                    || !stand.hasCustomName()
                    || stand.distanceTo(player) > 30.0) {
                continue;
            }

            String name = stand.getCustomName().getString()
                    .replaceAll("(?i)§[0-9A-FK-OR]", "")
                    .strip();
            if (!name.equalsIgnoreCase("HOTSPOT")) {
                continue;
            }

            double distanceToBobber = stand.distanceTo(bobber);
            if (distanceToBobber <= closestDistance) {
                closestHotspot = stand;
                closestDistance = distanceToBobber;
            }
        }

        // Keep the last matched hotspot if the hook is briefly reeled in or unloaded,
        // just as Feesh keeps its last closest hotspot until it despawns or the world changes.
        if (closestHotspot != null) {
            trackedHotspotUuid = closestHotspot.getUUID();
            trackedHotspotLevel = client.level;
        }
    }

    private static FishingHook findOwnedBobber(net.minecraft.client.Minecraft client, LocalPlayer player) {
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof FishingHook hook
                    && hook.getPlayerOwner() == player
                    && hook.isAlive()) {
                return hook;
            }
        }
        return null;
    }

    private static int randomSwapDelayTicks() {
        return ThreadLocalRandom.current().nextInt(1, 4);
    }

    private static void switchToHyperionForRareCreature(LocalPlayer player) {
        if (player == null || !Config.INSTANCE.useHyperion) {
            return;
        }
        int hyperionSlot = findHyperionHotbarSlot(player);
        if (hyperionSlot >= 0) {
            player.getInventory().setSelectedSlot(hyperionSlot);
        }
    }

    private static void tickGrinchAutoClicker(Minecraft client, LocalPlayer player, Entity hookedEntity) {
        if (!Config.INSTANCE.grinchAutoClickerEnabled || !isGrinch(hookedEntity)) {
            grinchClickTargetUuid = null;
            nextGrinchClickAt = 0;
            return;
        }
        if (client.crosshairPickEntity != hookedEntity) {
            grinchClickTargetUuid = null;
            nextGrinchClickAt = 0;
            return;
        }
        if (client.screen != null || player.isUsingItem() || client.gameMode == null) {
            return;
        }

        if (!hookedEntity.getUUID().equals(grinchClickTargetUuid)) {
            grinchClickTargetUuid = hookedEntity.getUUID();
            nextGrinchClickAt = 0;
        }

        long now = System.currentTimeMillis();
        if (now < nextGrinchClickAt) {
            return;
        }

        client.gameMode.attack(player, hookedEntity);
        player.swing(InteractionHand.MAIN_HAND);

        double cps = Math.max(3.0, Math.min(15.0, Config.INSTANCE.grinchClickCps));
        double intervalMs = (1000.0 / cps) + ((Math.random() - 0.5) * 60.0);
        nextGrinchClickAt = now + Math.max(1L, Math.round(intervalMs));
    }

    private static boolean isGrinch(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        String name = entity.getName().getString();
        if (entity.getCustomName() != null) {
            name += " " + entity.getCustomName().getString();
        }
        return name.replaceAll("(?i)§[0-9A-FK-OR]", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("grinch");
    }

    private static void stopForHotspotGone(LocalPlayer player) {
        if (player != null && fishingAction >= 0 && rodSelectedSlotForAction >= 0) {
            player.getInventory().setSelectedSlot(rodSelectedSlotForAction);
        }
        paused = true;
        biteAlertHandled = false;
        bobberTrackingInitialized = false;
        bobberWasActive = false;
        bobberActiveTicks = 0;
        trackedBobberUuid = null;
        fishingAction = -1;
        actionTimer = 0;
        petMenuWaitTicks = 0;
        handledMagmaCubeId = -1;
        rodHandForAction = null;
        rodSelectedSlotForAction = -1;
        hyperionSlotForAction = -1;
        sosFlarePending = false;
        clearTrackedHotspot();
        hotspotRadarStage = 0;
        hotspotRadarTimer = 0;
        hotspotRadarSlot = -1;
        hotspotRadarRestoreSlot = -1;
        if (player != null) {
            player.sendSystemMessage(Component.literal(STATUS_PREFIX + " §ePAUSED (hotspot expired)"));
        }
    }

    private static void clearTrackedHotspot() {
        trackedHotspotUuid = null;
        trackedHotspotLevel = null;
        pendingHotspotUnloadUuid = null;
        pendingHotspotUnloadTicks = 0;
    }

    private static boolean isPetsMenu(String title) {
        return title.matches("(?i)(?:\\(\\d+/\\d+\\)\\s*)?Pets");
    }

    private static void openConfigMenu() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new ConfigScreen(client.screen, TOGGLE_KEY));
    }

    private static void pauseForWorldChange(LocalPlayer player) {
        boolean newlyPaused = !paused;
        if (hotspotRadarStage > 0 && player != null && hotspotRadarRestoreSlot >= 0) {
            player.getInventory().setSelectedSlot(hotspotRadarRestoreSlot);
        } else if (fishingAction >= 0 && player != null && rodSelectedSlotForAction >= 0) {
            player.getInventory().setSelectedSlot(rodSelectedSlotForAction);
        }

        paused = true;
        biteAlertHandled = false;
        bobberTrackingInitialized = false;
        bobberWasActive = false;
        bobberActiveTicks = 0;
        trackedBobberUuid = null;
        fishingAction = -1;
        actionTimer = 0;
        petMenuWaitTicks = 0;
        petMenuStableTicks = 0;
        handledMagmaCubeId = -1;
        rodHandForAction = null;
        rodSelectedSlotForAction = -1;
        hyperionSlotForAction = -1;
        sosFlarePending = false;
        pendingHotspotUnloadUuid = null;
        pendingHotspotUnloadTicks = 0;
        hotspotRadarStage = 0;
        hotspotRadarTimer = 0;
        hotspotRadarSlot = -1;
        hotspotRadarRestoreSlot = -1;
        clearTrackedHotspot();

        if (newlyPaused && player != null) {
            player.sendSystemMessage(Component.literal(STATUS_PREFIX + " §ePAUSED (world/server changed)"));
        }
    }

    private static boolean isHookTimerWithinPing(Minecraft client, FishingHook bobber, int configuredPingMs) {
        if (bobber == null || client.level == null) {
            return false;
        }

        double thresholdSeconds = Math.max(0, Math.min(5000, configuredPingMs)) / 1000.0;
        ArmorStand closestTimer = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName()) {
                continue;
            }
            double distance = stand.distanceTo(bobber);
            if (distance <= 0.5 && distance < closestDistance) {
                closestTimer = stand;
                closestDistance = distance;
            }
        }
        if (closestTimer == null) {
            return false;
        }

        String formattedName = closestTimer.getCustomName().getString();
        if (formattedName.contains("!!!")) {
            return true;
        }

        String plainName = formattedName.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
        Matcher matcher = HOOK_TIMER_SECONDS.matcher(plainName);
        if (!matcher.find()) {
            return false;
        }
        try {
            double secondsLeft = Double.parseDouble(matcher.group(1).replace(',', '.'));
            return secondsLeft <= thresholdSeconds;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean hasClickToDespawnTooltip(
            net.minecraft.world.item.ItemStack stack,
            Minecraft client,
            LocalPlayer player
    ) {
        String target = "click to despawn";
        String storedText = stack.getComponents().toString() + " " + stack.getHoverName().getString();
        if (storedText.toLowerCase(java.util.Locale.ROOT).contains(target)) {
            return true;
        }

        Item.TooltipContext context = Item.TooltipContext.of(client.level);
        return java.util.List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED).stream()
                .flatMap(flag -> stack.getTooltipLines(context, player, flag).stream())
                .map(Component::getString)
                .map(line -> line.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(line -> line.contains(target));
    }

    private static void finishFishingAction() {
        fishingAction = -1;
        actionTimer = 0;
        petMenuWaitTicks = 0;
        petMenuStableTicks = 0;
        rodHandForAction = null;
        rodSelectedSlotForAction = -1;
        hyperionSlotForAction = -1;
        recastCheckTimer = 8 * 20;
    }
}
