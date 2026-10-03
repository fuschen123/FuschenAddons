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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import com.FishHelper.features.RandomMovementFeature;
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
            "WOAH! A Plhlegblast appeared.",
            "The sky darkens and the air thickens. The end times are upon us: Ragnarok is here."
    );

    private static KeyMapping TOGGLE_KEY;
    private static KeyMapping CONFIG_KEY;

    private static boolean enabled = false;
    private static boolean hoppityAwaitingYes = false;
    private static boolean hoppityAwaitingWindow = false;
    private static boolean hoppityWindowDetected = false;
    private static Screen hoppityScreenBeforeAccept;
    private static int hoppityPauseTicks = 0;
    private static boolean biteAlertHandled = false;
    private static boolean bobberTrackingInitialized = false;
    private static boolean bobberWasActive = false;
    private static int bobberActiveTicks = 0;
    private static UUID trackedBobberUuid;
    private static final FishingWatchdog WATCHDOG = new FishingWatchdog();
    private static final ActionDeadline ACTION_DEADLINE = new ActionDeadline();
    private static final JawbusPauseFeature JAWBUS_PAUSE = new JawbusPauseFeature();
    private static int recoveryRetryTicks, rareCreatureTicks;
    private static boolean petAfterRecovery, pendingRecast;
    private static RodAccess normalRod;
    private static UUID actionHook;
    private static String lastProblem = "";
    private static int flarePlacementCooldown = 0;
    private static boolean sosFlarePending = false;
    private static int fishingAction = -1; // -1 idle; 0-6 bite/pet sequence; 11-13 flare placement
    private static int actionTimer = 0;
    private static int petMenuWaitTicks = 0;
    private static int petMenuRetryTicks = 0;
    private static boolean petEquipAttempted = false;
    private static boolean petMenuSeen = false;

    private static InteractionHand rodHandForAction;
    private static int rodSelectedSlotForAction = -1;
    private static int actionWeaponSlotForAction = -1;
    private static UUID trackedHotspotUuid;
    private static ClientLevel trackedHotspotLevel;
    private static ClientLevel observedWorldLevel;
    private static net.minecraft.client.multiplayer.ClientPacketListener observedConnection;
    private static LocalPlayer observedPlayer;
    private static boolean worldLevelObservationInitialized = false;
    private static UUID pendingHotspotUnloadUuid;
    private static int pendingHotspotUnloadTicks;
    private static int hotspotRadarStage;
    private static int hotspotRadarTimer;
    private static int hotspotRadarSlot = -1;
    private static int hotspotRadarRestoreSlot = -1;
    private static ThunderResponseFeature thunderResponse;
    private static HookRecoveryFeature hookRecovery;
    private static final HookEncounterGuard HOOK_ENCOUNTERS = new HookEncounterGuard();

    @Override
    public void onInitializeClient() {
        Config.load();
        SeaCreatureHud.initialize();
        RandomMovementFeature.initialize();

        TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.tsclient.toggle",
                        InputConstants.Type.KEYSYM,
                        Config.INSTANCE.toggleKeyCode,
                        KeyCategories.MAIN
                )
        );
        CONFIG_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.tsclient.config",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_P,
                        KeyCategories.MAIN
                )
        );

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));

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

        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                onGameMessage(Minecraft.getInstance(), message, overlay));
        ClientTickEvents.END_CLIENT_TICK.register(FishHelperClient::tick);
    }

    static void onGameMessage(Minecraft client, Component message, boolean overlay) {
            // A late old-world packet must not start or restore actions before the next tick disables us.
            if (worldLevelObservationInitialized && (observedWorldLevel != client.level
                    || observedConnection != client.getConnection())) return;
            String plainMessage = message.getString().replaceAll("(?i)§[0-9A-FK-OR]", "").strip();

            if (enabled && !overlay && JawbusPauseFeature.SPAWN_MESSAGE.equals(plainMessage)) {
                if (JAWBUS_PAUSE.spawned(client) == JawbusPause.Change.STARTED) beginJawbusPause(client);
                return;
            }
            // Chat-triggered Thunder, Hoppity or rare-mob actions cannot bypass this input owner.
            if (JAWBUS_PAUSE.active()) return;

            if (enabled && client.player != null) {
                if (thunderResponse == null && hookRecovery == null && !hoppityAwaitingYes && !hoppityAwaitingWindow
                        && isHoppityRingMessage(plainMessage)) {
                    String pickupCommand = findClickableCommand(message, "PICK UP");
                    if (pickupCommand != null) {
                        beginHoppityCall(client, pickupCommand);
                    }
                } else if (hoppityAwaitingYes && isHoppityYesPrompt(plainMessage)) {
                    String yesCommand = findClickableCommand(message, "[Yes]");
                    if (yesCommand != null) {
                        hoppityScreenBeforeAccept = client.screen;
                        sendChatClickCommand(client.player, yesCommand);
                        hoppityAwaitingYes = false;
                        hoppityAwaitingWindow = true;
                        client.player.sendSystemMessage(Component.literal(
                                STATUS_PREFIX + " §eHoppity offer accepted; checking the window"));
                    }
                }
            }

            if (enabled && hoppityPauseTicks == 0 && !overlay && Config.INSTANCE.thunderResponseEnabled
                    && ThunderResponseFeature.SPAWN_MESSAGE.equals(plainMessage)) {
                beginThunderResponse(client);
                return;
            }

            if (enabled && hoppityPauseTicks == 0 && thunderResponse == null && STOP_MESSAGES.contains(plainMessage)) {
                finishHookRecovery();
                restoreActionRod(client.player);
                finishFishingAction();
                if (normalRod == null && client.player != null) normalRod = new RodAccess(client.player, -1, null);
                switchToActionWeaponForRareCreature(client.player);
                rareCreatureTicks = 40;
            }
    }

    static void tick(Minecraft client) {
            SeaCreatureTracker.INSTANCE.tick(client);
            if (!worldLevelObservationInitialized) {
                observedWorldLevel = client.level;
                observedConnection = client.getConnection();
                worldLevelObservationInitialized = true;
            } else if (observedWorldLevel != client.level || observedConnection != client.getConnection()) {
                observedWorldLevel = client.level;
                observedConnection = client.getConnection();
                stopForWorldChange(client.player);
            }
            if (observedPlayer != client.player) {
                resetActions(client.player, false);
                observedPlayer = client.player;
            }
            OwnedHookResolver.tick(client);

            if (trackedHotspotLevel != null && trackedHotspotLevel != client.level) {
                clearTrackedHotspot();
                if (hotspotRadarStage > 0 && client.player != null) {
                    restoreHotspotRadarSlot(client.player);
                }
            }

            while (TOGGLE_KEY.consumeClick()) {
                resetActions(client.player, true);
                JAWBUS_PAUSE.reset();
                enabled = !enabled;

                if (client.player != null) {
                    client.player.sendSystemMessage(
                            Component.literal(STATUS_PREFIX + " " + (enabled ? "§aON" : "§cOFF")));
                }
            }

            while (CONFIG_KEY.consumeClick()) {
                openConfigMenu();
            }

            if (!enabled) return;
            JawbusPause.Change jawbusChange = JAWBUS_PAUSE.tick(client);
            if (jawbusChange == JawbusPause.Change.STARTED) beginJawbusPause(client);
            if (jawbusChange == JawbusPause.Change.ENDED) {
                resetFishingActions(client.player, true);
                client.player.sendSystemMessage(Component.literal(STATUS_PREFIX + " §bJawbus pause ended"));
                return; // Recheck menus/Hoppity/death next tick; never re-enable the helper here.
            }
            if (JAWBUS_PAUSE.active()) return;
            if (recoveryRetryTicks > 0) recoveryRetryTicks--;
            UUID currentHook = client.player == null ? null : hookUuid(findOwnedBobber(client, client.player));
            if (WATCHDOG.observe(currentHook)) {
                lastProblem = "";
                if (currentHook != null && !HOOK_ENCOUNTERS.wasReeled(currentHook)) pendingRecast = false;
            }

            if (thunderResponse != null) {
                if (!thunderResponse.canContinue()) {
                    finishThunderResponse();
                    recoveryRetryTicks = 100;
                    WATCHDOG.failed();
                } else if (!thunderResponse.tick()) {
                    boolean aborted = thunderResponse.aborted();
                    finishThunderResponse();
                    if (aborted) reportProblem(client.player, "thunder", "Thunder action ended; fishing will continue automatically");
                    else pendingRecast = true; // Thunder died; restart fishing on the next tick.
                    recoveryRetryTicks = aborted ? 100 : 0;
                    if (aborted) WATCHDOG.failed();
                }
                return; // No fishing, pet, flare, radar or Grinch input in a Thunder tick.
            }

            if (hookRecovery != null) {
                boolean cancelled = !hookRecovery.canContinue();
                if (cancelled || !hookRecovery.tick()) {
                    var problem = cancelled ? RecastSequence.Problem.CANCELLED : hookRecovery.problem();
                    boolean equipPet = (petAfterRecovery || hookRecovery.castIssued()) && problem == RecastSequence.Problem.NONE;
                    finishHookRecovery();
                    recoveryRetryTicks = 100;
                    if (problem == RecastSequence.Problem.NONE) {
                        pendingRecast = false;
                        WATCHDOG.completed(); lastProblem = "";
                        if (equipPet) { fishingAction = 5; actionTimer = 2; }
                    } else {
                        pendingRecast = true;
                        WATCHDOG.failed();
                        reportRecoveryProblem(client.player, problem);
                    }
                }
                return;
            }

            if (hoppityAwaitingWindow
                    && client.screen != hoppityScreenBeforeAccept
                    && client.screen instanceof AbstractContainerScreen<?> screen
                    && isHoppityOfferScreen(screen, client)) {
                hoppityAwaitingWindow = false;
                hoppityWindowDetected = true;
                if (client.player != null) {
                    boolean validOffer = screen.getMenu().slots.size() > 22
                            && isHoppityOfferTooltip(screen.getMenu().slots.get(22).getItem(), client);
                    boolean alreadyOwned = validOffer && isHoppityRabbitAlreadyOwned(screen, client);
                    String ownership = alreadyOwned
                            ? "§eRabbit already found; no purchase made"
                            : validOffer ? "§eRabbit not found yet" : "§eCould not verify the rabbit offer";
                    client.player.sendSystemMessage(Component.literal(
                            STATUS_PREFIX + " §aHoppity window opened. " + ownership));

                    if (Config.INSTANCE.autoBuyHoppityRabbit && validOffer) {
                        if (alreadyOwned) {
                            client.setScreen(null);
                        } else if (client.gameMode != null) {
                            client.gameMode.handleContainerInput(
                                    screen.getMenu().containerId,
                                    22,
                                    0,
                                    ContainerInput.PICKUP,
                                    client.player
                            );
                            client.player.sendSystemMessage(Component.literal(
                                    STATUS_PREFIX + " §eAuto-buy clicked for the unowned rabbit"));
                        }
                    }
                }
            }

            if (hoppityWindowDetected) {
                if (client.screen instanceof AbstractContainerScreen<?>) {
                    return;
                }
                hoppityWindowDetected = false;
                hoppityAwaitingYes = false;
                hoppityAwaitingWindow = false;
                hoppityPauseTicks = 20;
            }

            if (hoppityPauseTicks > 0) {
                if (--hoppityPauseTicks <= 0) {
                    hoppityPauseTicks = 0;
                    hoppityAwaitingYes = false;
                    hoppityAwaitingWindow = false;
                    biteAlertHandled = false;
                    bobberTrackingInitialized = false;
                }
                return;
            }

            if (client.player == null || client.level == null || client.gameMode == null || !client.player.isAlive()) {
                return;
            }

            boolean ownedPetMenu = fishingAction == 6 && client.screen instanceof AbstractContainerScreen<?> petScreen
                    && isPetsMenu(petScreen.getTitle().getString());
            if (fishingAction == 6 && petMenuSeen && !ownedPetMenu) {
                // The server closes the Pets menu after a successful equip click.
                finishFishingAction();
            }
            if (client.screen != null && !ownedPetMenu) return;
            if (normalRod == null) normalRod = new RodAccess(client.player, -1, null);
            if (ownedPetMenu) ACTION_DEADLINE.reset();
            if (!ownedPetMenu && ACTION_DEADLINE.expired(hotspotRadarStage > 0 ? 100 + hotspotRadarStage : fishingAction, 100)) {
                restoreActionRod(client.player);
                if (hotspotRadarStage > 0) restoreHotspotRadarSlot(client.player);
                if (ownedPetMenu) client.setScreen(null);
                finishFishingAction();
                ACTION_DEADLINE.reset();
                reportProblem(client.player, "action-timeout", "Action timed out; recovering the fishing rod automatically");
                beginRecast(client, findOwnedBobber(client, client.player), false, false);
                return;
            }

            // Do not let a timed-out call action cause fishing inputs while its offer menu is open.
            if (hoppityWindowDetected && client.screen instanceof AbstractContainerScreen<?>) {
                return;
            }
            hoppityWindowDetected = false;

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
            if (!normalRod.available() && fishingAction < 0) {
                reportProblem(player, "rod", "Waiting for a fishing rod in the hotbar or offhand");
                return;
            }
            if (client.screen == null && HookRecoveryFeature.isBlockingMob(activeBobber, player)) {
                Entity hookedMob = activeBobber.getHookedIn();
                boolean newMobEncounter = hookedMob != null
                        && !HOOK_ENCOUNTERS.wasUsed(activeBobber.getUUID(), hookedMob.getUUID());
                if (recoveryRetryTicks == 0 || newMobEncounter) {
                    beginRecast(client, activeBobber, true, false);
                } else {
                    GrinchAutoClickerFeature.tick(client, player, hookedMob);
                }
                return;
            }
            if (rareCreatureTicks > 0) { rareCreatureTicks--; return; }
            if (pendingRecast && fishingAction < 0) {
                if (recoveryRetryTicks == 0) beginRecast(client, activeBobber, false, false);
                return;
            }
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
                    petMenuRetryTicks = 0;
                    petEquipAttempted = false;
                    petMenuSeen = false;
                }
            }

            Entity hookedEntity = activeBobber == null ? null : activeBobber.getHookedIn();
            GrinchAutoClickerFeature.tick(client, player, hookedEntity);
            // Finish an active bite sequence before checking the currently held item.
            // During this sequence the main hand intentionally holds the selected action weapon.
            if (fishingAction >= 0) {
                if (actionTimer > 0) {
                    actionTimer--;
                    if (actionTimer > 0) {
                        return;
                    }
                }

                if (fishingAction == 0) {
                    if (activeBobber != null && !activeBobber.getUUID().equals(actionHook)) { finishFishingAction(); return; }
                    RodAccess rod = new RodAccess(player, rodSelectedSlotForAction, rodHandForAction);
                    if (!rod.select()) { beginRecast(client, activeBobber, false, false); return; }
                    rodHandForAction = rod.hand();
                    rodSelectedSlotForAction = player.getInventory().getSelectedSlot();
                    if (activeBobber != null && activeBobber.getUUID().equals(actionHook) && !HOOK_ENCOUNTERS.wasReeled(actionHook)) {
                        client.gameMode.useItem(player, rodHandForAction);
                        player.swing(rodHandForAction);
                        HOOK_ENCOUNTERS.reeled(actionHook);
                    }

                    fishingAction = actionWeaponSlotForAction >= 0 ? 1 : 3;
                    actionTimer = actionWeaponSlotForAction >= 0
                            ? randomSwapDelayTicks()
                            : ThreadLocalRandom.current().nextInt(4, 6);
                } else if (fishingAction == 1) {
                    actionWeaponSlotForAction = findActionWeaponHotbarSlot(player);
                    if (actionWeaponSlotForAction >= 0) player.getInventory().setSelectedSlot(actionWeaponSlotForAction);
                    fishingAction = actionWeaponSlotForAction >= 0 ? 4 : 2;
                    actionTimer = randomSwapDelayTicks();
                } else if (fishingAction == 4) {
                    if (findActionWeaponHotbarSlot(player) == player.getInventory().getSelectedSlot()) {
                        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                        sosFlarePending = true;
                    }

                    fishingAction = 2;
                    actionTimer = randomSwapDelayTicks();
                } else if (fishingAction == 2) {
                    restoreActionRod(player);
                    fishingAction = 3;
                    // Let the catch-triggered AutoPet rule settle for 4–5 ticks before casting.
                    actionTimer = ThreadLocalRandom.current().nextInt(4, 6);
                } else if (fishingAction == 3) {
                    beginRecast(client, actionHook, null, false, true);
                } else if (fishingAction == 5) {
                    if (!(client.screen instanceof AbstractContainerScreen<?> screen
                            && isPetsMenu(screen.getTitle().getString()))) {
                        player.connection.sendCommand("pets");
                        player.sendSystemMessage(Component.literal(STATUS_PREFIX + " sending /pets"));
                    }
                    fishingAction = 6;
                    actionTimer = ThreadLocalRandom.current().nextInt(2, 4);
                    petMenuRetryTicks = 0;
                    petEquipAttempted = false;
                    petMenuSeen = false;
                } else if (fishingAction == 11) {
                    actionWeaponSlotForAction = findFlareHotbarSlot(player);
                    if (actionWeaponSlotForAction < 0) { restoreActionRod(player); finishFishingAction(); return; }
                    player.getInventory().setSelectedSlot(actionWeaponSlotForAction);
                    fishingAction = 12;
                    actionTimer = randomSwapDelayTicks();
                } else if (fishingAction == 12) {
                    if (hasNearbyFlareOrPlasmaflux(client, player) || findFlareHotbarSlot(player) != player.getInventory().getSelectedSlot()) {
                        restoreActionRod(player);
                        finishFishingAction();
                    } else {
                        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                        flarePlacementCooldown = 3 * 60 * 20;
                        fishingAction = 13;
                        actionTimer = 2;
                    }
                } else if (fishingAction == 13) {
                    beginRecast(client, actionHook, null, false, false);
                } else {
                    int petMenuSlot = 9 + Math.max(1, Math.min(7, Config.INSTANCE.petNumber));
                    if (client.screen instanceof AbstractContainerScreen<?> screen
                            && isPetsMenu(screen.getTitle().getString())
                            && screen.getMenu().slots.size() > petMenuSlot) {
                        petMenuSeen = true;
                        var petStack = screen.getMenu().slots.get(petMenuSlot).getItem();
                        if (hasPetTooltip(petStack, client, player, "click to despawn")) {
                            // It was already equipped, or the server did not close after the two-second check.
                            client.setScreen(null);
                            finishFishingAction();
                            return;
                        } else if (hasPetTooltip(petStack, client, player, "left-click to summon")) {
                            petMenuWaitTicks = 0;
                            int retryDelay = petEquipAttempted ? 1 : 3;
                            if (++petMenuRetryTicks >= retryDelay) {
                                client.gameMode.handleContainerInput(
                                        screen.getMenu().containerId,
                                        petMenuSlot,
                                        0,
                                        ContainerInput.PICKUP,
                                        player
                                );
                                petEquipAttempted = true;
                                petMenuRetryTicks = 0;
                            }
                            actionTimer = petEquipAttempted ? 40 : 1;
                        } else if ((petMenuWaitTicks += 40) >= 120) {
                            client.setScreen(null);
                            finishFishingAction();
                        } else {
                            actionTimer = 40;
                        }
                    } else if (++petMenuWaitTicks >= 20) {
                        if (client.screen instanceof AbstractContainerScreen<?> screen
                                && isPetsMenu(screen.getTitle().getString())) {
                            client.setScreen(null);
                        }
                        finishFishingAction();
                    } else {
                        petMenuRetryTicks = 0;
                        actionTimer = 1;
                    }
                }
                return;
            }

            if (!normalRod.select()) {
                reportProblem(player, "rod", "Waiting for a fishing rod in the hotbar or offhand");
                return;
            }
            InteractionHand rodHand = normalRod.hand();

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
                actionHook = hookUuid(bobber);
                rodSelectedSlotForAction = player.getInventory().getSelectedSlot();
                actionWeaponSlotForAction = findActionWeaponHotbarSlot(player);
                fishingAction = 0;
                actionTimer = 1;
                return;
            }

            // Wait until the current rod/action-weapon/pet sequence is finished. A flare is
            // queued only after an action-weapon use and placed on a later idle tick.
            if (sosFlarePending && flarePlacementCooldown == 0
                    && Config.INSTANCE.flareTier != Config.FlareTier.NONE
                    && !hasNearbyFlareOrPlasmaflux(client, player)) {
                int flareSlot = findFlareHotbarSlot(player);
                if (flareSlot >= 0) {
                    rodHandForAction = rodHand;
                    actionHook = hookUuid(bobber);
                    rodSelectedSlotForAction = player.getInventory().getSelectedSlot();
                    actionWeaponSlotForAction = flareSlot;
                    fishingAction = 11;
                    actionTimer = randomSwapDelayTicks();
                    sosFlarePending = false;
                    return;
                }
            }

            boolean normalWait = bobber != null && !HOOK_ENCOUNTERS.wasReeled(bobber.getUUID())
                    && (bobber.isInWater() || bobber.isInLava() || hasValidHookCountdown(client, bobber)
                        || Config.INSTANCE.slugfishReelEnabled && !slugfishWaitComplete);
            if (WATCHDOG.tick(enabled, normalWait)) {
                beginRecast(client, bobber, false, false);
            }
    }

    private static int findActionWeaponHotbarSlot(LocalPlayer player) {
        String search = Config.INSTANCE.actionWeapon.searchName;
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

    private static void beginThunderResponse(Minecraft client) {
        if (JAWBUS_PAUSE.active() || thunderResponse != null || client.player == null || client.level == null
                || client.gameMode == null || !client.player.isAlive()) return;
        // Only close the pet screen owned by the interrupted fishing sequence.
        if (client.screen != null) {
            if ((fishingAction == 5 || fishingAction == 6)
                    && client.screen instanceof AbstractContainerScreen<?> screen
                    && isPetsMenu(screen.getTitle().getString())) client.setScreen(null);
            else return;
        }
        finishHookRecovery(); // Restores the rod slot before Thunder takes its snapshot.
        int restoreSlot = rodSelectedSlotForAction >= 0 ? rodSelectedSlotForAction
                : hotspotRadarRestoreSlot >= 0 ? hotspotRadarRestoreSlot
                : client.player.getInventory().getSelectedSlot();
        FishingHook bobber = findOwnedBobber(client, client.player);
        var origin = bobber == null ? client.player.position() : bobber.position();
        finishFishingAction();

        sosFlarePending = false;
        hotspotRadarStage = 0;
        hotspotRadarTimer = 0;
        hotspotRadarSlot = -1;
        hotspotRadarRestoreSlot = -1;
        thunderResponse = new ThunderResponseFeature(client, restoreSlot, origin);
        client.player.sendSystemMessage(Component.literal(STATUS_PREFIX + " §bThunder response started"));
    }

    private static void finishThunderResponse() {
        if (thunderResponse == null) return;
        thunderResponse.finish();
        thunderResponse = null;
        WATCHDOG.interrupted();
        biteAlertHandled = false;
        bobberTrackingInitialized = false;
        bobberWasActive = false;
        bobberActiveTicks = 0;
        trackedBobberUuid = null;
    }

    private static void finishHookRecovery() {
        if (hookRecovery == null) return;
        hookRecovery.finish();
        hookRecovery = null;
        WATCHDOG.interrupted();
        finishFishingAction();
        trackedBobberUuid = null;
        bobberTrackingInitialized = false;
        bobberWasActive = false;
        bobberActiveTicks = 0;
        biteAlertHandled = false;
    }

    private static UUID hookUuid(FishingHook hook) { return hook == null ? null : hook.getUUID(); }

    private static void beginRecast(Minecraft client, FishingHook hook, boolean attack, boolean pet) {
        beginRecast(client, hookUuid(hook), hook == null || hook.getHookedIn() == null ? null : hook.getHookedIn().getUUID(), attack, pet);
    }

    private static void beginRecast(Minecraft client, UUID hook, UUID mob, boolean attack, boolean pet) {
        if (!enabled || JAWBUS_PAUSE.active() || hookRecovery != null || thunderResponse != null || client.player == null || client.screen != null) return;
        hookRecovery = new HookRecoveryFeature(client, hook, mob, rodSelectedSlotForAction, rodHandForAction, attack, HOOK_ENCOUNTERS);
        petAfterRecovery = pet;
        pendingRecast = false;
        finishFishingAction();
        if (!pet) sosFlarePending = false;
        ACTION_DEADLINE.reset();
    }

    private static void restoreActionRod(LocalPlayer player) {
        if (player == null) return;
        if (rodSelectedSlotForAction >= 0 || rodHandForAction != null)
            new RodAccess(player, rodSelectedSlotForAction, rodHandForAction).restore();
        else if (normalRod != null) normalRod.restore();
    }

    private static void reportProblem(LocalPlayer player, String key, String text) {
        if (player == null || key.equals(lastProblem)) return;
        lastProblem = key;
        player.sendSystemMessage(Component.literal(STATUS_PREFIX + " §e" + text));
    }

    private static void reportRecoveryProblem(LocalPlayer player, RecastSequence.Problem problem) {
        switch (problem) {
            case MISSING_ROD -> reportProblem(player, "rod", "Waiting for a fishing rod in the hotbar or offhand");
            case MISSING_HYPERION -> reportProblem(player, "hyperion", "Waiting for Hyperion in the hotbar; retrying automatically");
            case HOOK_RELEASE -> reportProblem(player, "hook-release", "Waiting for the old hook to disappear; checking automatically");
            case CAST_CONFIRMATION -> reportProblem(player, "cast-confirmation", "Cast not confirmed yet; checking again in 5 seconds");
            default -> { }
        }
    }

    private static boolean isHoppityRingMessage(String message) {
        String normalized = message.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("RING")
                && normalized.contains("[PICK UP]")
                && (message.startsWith("✆") || normalized.contains("HOPPITY"));
    }

    private static boolean isHoppityYesPrompt(String message) {
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("select an option:")
                && normalized.contains("[yes]")
                && normalized.contains("[no]");
    }

    private static String findClickableCommand(Component message, String label) {
        for (Component part : message.toFlatList()) {
            if (!part.getString().toLowerCase(java.util.Locale.ROOT)
                    .contains(label.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            if (part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand runCommand) {
                return runCommand.command();
            }
        }
        return null;
    }

    private static boolean isHoppityOfferScreen(AbstractContainerScreen<?> screen, Minecraft client) {
        String title = screen.getTitle().getString()
                .replaceAll("(?i)§[0-9A-FK-OR]", "")
                .toLowerCase(java.util.Locale.ROOT);
        if (title.contains("hoppity") || title.contains("chocolate rabbit")) {
            return true;
        }
        return screen.getMenu().slots.size() > 22
                && isHoppityOfferTooltip(screen.getMenu().slots.get(22).getItem(), client);
    }

    private static boolean isHoppityRabbitAlreadyOwned(AbstractContainerScreen<?> screen, Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return false;
        }

        if (screen.getMenu().slots.size() <= 22) {
            return false;
        }
        String details = getItemTooltipText(screen.getMenu().slots.get(22).getItem(), client, player);
        return details.contains("already owned") || details.contains("already found")
                || details.contains("you already have");
    }

    private static boolean isHoppityOfferTooltip(net.minecraft.world.item.ItemStack stack, Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return false;
        }
        String tooltip = getItemTooltipText(stack, client, player);
        return tooltip.contains("rabbit") && tooltip.contains("cost") && tooltip.contains("click to trade");
    }

    private static String getItemTooltipText(
            net.minecraft.world.item.ItemStack stack,
            Minecraft client,
            LocalPlayer player
    ) {
        if (client.level == null) {
            return "";
        }
        String details = stack.getHoverName().getString() + " " + stack.getComponents();
        Item.TooltipContext context = Item.TooltipContext.of(client.level);
        for (TooltipFlag flag : java.util.List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            details += " " + stack.getTooltipLines(context, player, flag).stream()
                    .map(Component::getString)
                    .collect(java.util.stream.Collectors.joining(" "));
        }
        return details.replaceAll("(?i)§[0-9A-FK-OR]", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static void sendChatClickCommand(LocalPlayer player, String command) {
        String normalizedCommand = command.stripLeading();
        while (normalizedCommand.startsWith("/")) {
            normalizedCommand = normalizedCommand.substring(1);
        }
        if (!normalizedCommand.isBlank()) {
            player.connection.sendCommand(normalizedCommand);
        }
    }

    private static void beginHoppityCall(Minecraft client, String pickupCommand) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        hoppityPauseTicks = 10 * 20;
        hoppityAwaitingYes = true;
        hoppityAwaitingWindow = false;
        hoppityWindowDetected = false;

        if (fishingAction >= 0 && rodSelectedSlotForAction >= 0) {
            player.getInventory().setSelectedSlot(rodSelectedSlotForAction);
        }
        fishingAction = -1;
        actionTimer = 0;
        petMenuWaitTicks = 0;
        petMenuRetryTicks = 0;
        petEquipAttempted = false;
        petMenuSeen = false;
        rodHandForAction = null;
        rodSelectedSlotForAction = -1;
        actionWeaponSlotForAction = -1;


        sendChatClickCommand(player, pickupCommand);
        player.sendSystemMessage(Component.literal(STATUS_PREFIX + " §eHoppity call picked up; fishing paused for 10 seconds"));
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
        petMenuRetryTicks = 0;
        petEquipAttempted = false;
        petMenuSeen = false;
        rodHandForAction = null;
        rodSelectedSlotForAction = -1;
        actionWeaponSlotForAction = -1;
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

    static FishingHook findOwnedBobber(net.minecraft.client.Minecraft client, LocalPlayer player) {
        return OwnedHookResolver.find(client, player);
    }

    private static int randomSwapDelayTicks() {
        return ThreadLocalRandom.current().nextInt(1, 4);
    }

    private static void switchToActionWeaponForRareCreature(LocalPlayer player) {
        if (player == null || Config.INSTANCE.actionWeapon == Config.ActionWeapon.NONE) {
            return;
        }
        int weaponSlot = findActionWeaponHotbarSlot(player);
        if (weaponSlot >= 0) {
            player.getInventory().setSelectedSlot(weaponSlot);
        }
    }

    private static void stopForHotspotGone(LocalPlayer player) {
        restoreActionRod(player);
        finishFishingAction();
        clearTrackedHotspot();
        hotspotRadarStage = hotspotRadarTimer = 0;
        hotspotRadarSlot = hotspotRadarRestoreSlot = -1;
        sosFlarePending = false;
        reportProblem(player, "hotspot", "Hotspot expired; continuing fishing automatically");
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

    static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher) {
        for (String alias : java.util.List.of("fa", "fuschen")) {
            dispatcher.register(ClientCommands.literal(alias)
                    .then(ClientCommands.literal("gui").executes(context -> queueScreen(true)))
                    .executes(context -> queueScreen(false)));
        }
    }

    private static int queueScreen(boolean hud) {
        // ChatScreen closes itself after submitting a command. Open after that close,
        // including when already on the client thread (execute() would run immediately).
        Minecraft.getInstance().schedule(() -> {
            if (hud) openHudEditor(); else openConfigMenu();
        });
        return 1;
    }

    private static void openConfigMenu() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new ConfigScreen(client.screen, TOGGLE_KEY));
    }

    private static void openHudEditor() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new HudEditorScreen(client.screen));
    }

    private static void stopForWorldChange(LocalPlayer player) {
        boolean wasEnabled = enabled;
        enabled = false;
        JAWBUS_PAUSE.reset();
        while (TOGGLE_KEY.consumeClick()) { /* Do not carry an activation queued in the old world across. */ }
        resetActions(player, false);
        HOOK_ENCOUNTERS.clear();
        OwnedHookResolver.reset();
        SeaCreatureTracker.INSTANCE.reset();
        ThunderMuter.reset();
        if (wasEnabled && player != null) player.sendSystemMessage(Component.literal(
                STATUS_PREFIX + " §eOFF (server/world changed; activate FishHelper manually when ready)"));
    }

    private static void resetActions(LocalPlayer player, boolean restore) {
        resetFishingActions(player, restore);
        hoppityAwaitingYes = hoppityAwaitingWindow = hoppityWindowDetected = false;
        hoppityPauseTicks = 0; hoppityScreenBeforeAccept = null;
    }

    private static void beginJawbusPause(Minecraft client) {
        resetFishingActions(client.player, true);
        client.player.sendSystemMessage(Component.literal(STATUS_PREFIX + " §eJawbus nearby; fishing paused"));
    }

    /** Cancels every queued fishing input without clearing independent Hoppity/menu waits. */
    private static void resetFishingActions(LocalPlayer player, boolean restore) {
        finishHookRecovery();
        finishThunderResponse();
        if (restore && player != null) {
            if (hotspotRadarRestoreSlot >= 0) restoreHotspotRadarSlot(player);
            restoreActionRod(player);
        }
        finishFishingAction();
        WATCHDOG.reset(); ACTION_DEADLINE.reset();
        GrinchAutoClickerFeature.reset();
        normalRod = null;
        pendingRecast = petAfterRecovery = false;
        recoveryRetryTicks = rareCreatureTicks = flarePlacementCooldown = 0;
        sosFlarePending = false;
        biteAlertHandled = bobberTrackingInitialized = bobberWasActive = false;
        bobberActiveTicks = 0; trackedBobberUuid = null;
        clearTrackedHotspot();
        hotspotRadarStage = hotspotRadarTimer = 0;
        hotspotRadarSlot = hotspotRadarRestoreSlot = -1;
        lastProblem = "";
    }

    private static boolean hasValidHookCountdown(Minecraft client, FishingHook bobber) {
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand && stand.hasCustomName() && stand.distanceTo(bobber) <= 0.5) {
                String text = stand.getCustomName().getString().replaceAll("§.", "").trim();
                if (text.contains("!!!") || HOOK_TIMER_SECONDS.matcher(text).find()) return true;
            }
        }
        return false;
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

    private static boolean hasPetTooltip(
            net.minecraft.world.item.ItemStack stack,
            Minecraft client,
            LocalPlayer player,
            String target
    ) {
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
        petMenuRetryTicks = 0;
        petEquipAttempted = false;
        petMenuSeen = false;
        rodHandForAction = null;
        rodSelectedSlotForAction = -1;
        actionWeaponSlotForAction = -1;
        actionHook = null;
    }
}
