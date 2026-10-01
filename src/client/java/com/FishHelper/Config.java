package com.FishHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("fuschenaddons.json");

    public static Config INSTANCE = new Config();

    /** GLFW key code for the start/stop toggle (default O). */
    public int toggleKeyCode = InputConstants.KEY_O;

    /** Selects the weapon used for the fishing actions; NONE disables weapon swapping. */
    public ActionWeapon actionWeapon = ActionWeapon.HYPERION;

    /** Automatically buy Hoppity's offered rabbit when the offer says it has not been found yet. */
    public boolean autoBuyHoppityRabbit = false;

    /** Which flare (if any) to place after using the selected action weapon. */
    public FlareTier flareTier = FlareTier.SOS;

    /** Pet to equip from the Pets menu, numbered from 1 to 7. */
    public int petNumber = 1;

    /** Delay !!! reeling for Slugfish fishing until the bobber has been active for 10 seconds. */
    public boolean slugfishReelEnabled = false;

    /** Reel any fish when its nearby Hypixel hook countdown reaches the configured ping threshold. */
    public boolean reelInUsingPing = false;

    /** User-entered round-trip latency, in milliseconds. */
    public int reelPingMs = 100;

    /** Click frequency used while a Grinch is hooked, matching Odin's default click rate. */
    public double grinchClickCps = 5.0;

    /** Automatically left-click a hooked Grinch while it is under the crosshair. */
    public boolean grinchAutoClickerEnabled = true;

    /** Allow the randomly strafing movement macro to be activated by its Controls keybind. */
    public boolean randomMovementEnabled = false;

    /** Interrupt fishing for the Ice Spray / Ink Wand / Hyperion Thunder response. */
    public boolean thunderResponseEnabled = false;
    public boolean thunderMuterEnabled = false;

    public boolean seaCreatureHealthbarEnabled = true;
    public double healthbarX = .5;
    public double healthbarY = .12;

    public enum FlareTier {
        NONE("None", ""),
        WARNING("Warning Flare", "warning flare"),
        ALERT("Alert Flare", "alert flare"),
        SOS("SOS Flare", "sos flare");

        public final String displayName;
        public final String searchName;

        FlareTier(String displayName, String searchName) {
            this.displayName = displayName;
            this.searchName = searchName;
        }

        public FlareTier next() {
            FlareTier[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum ActionWeapon {
        NONE("Off", ""),
        HYPERION("Hyperion", "hyperion"),
        SOUL_WHIP("Soul Whip", "soul whip"),
        FLAMING_FLAY("Flaming Flay", "flaming flay");

        public final String displayName;
        public final String searchName;

        ActionWeapon(String displayName, String searchName) {
            this.displayName = displayName;
            this.searchName = searchName;
        }

        public ActionWeapon next() {
            ActionWeapon[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                Config loaded = GSON.fromJson(json, Config.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    if (INSTANCE.actionWeapon == null) {
                        INSTANCE.actionWeapon = ActionWeapon.HYPERION;
                    }
                    if (INSTANCE.flareTier == null) INSTANCE.flareTier = FlareTier.SOS;
                    HudPosition position = new HudPosition(INSTANCE.healthbarX, INSTANCE.healthbarY);
                    INSTANCE.healthbarX = position.x();
                    INSTANCE.healthbarY = position.y();
                    INSTANCE.petNumber = Math.max(1, Math.min(7, INSTANCE.petNumber));
                    INSTANCE.reelPingMs = Math.max(0, Math.min(5000, INSTANCE.reelPingMs));
                    INSTANCE.grinchClickCps = Math.max(3.0, Math.min(15.0, INSTANCE.grinchClickCps));
                    JsonObject savedConfig = JsonParser.parseString(json).getAsJsonObject();
                    if (!savedConfig.has("actionWeapon") && savedConfig.has("useHyperion")) {
                        INSTANCE.actionWeapon = savedConfig.get("useHyperion").getAsBoolean()
                                ? ActionWeapon.HYPERION : ActionWeapon.NONE;
                    }
                    if (!savedConfig.has("slugfishReelEnabled") && savedConfig.has("slugfishReelAtBobberTicks")) {
                        INSTANCE.slugfishReelEnabled = savedConfig.get("slugfishReelAtBobberTicks").getAsInt() > 0;
                    }
                    if (!savedConfig.has("reelInUsingPing") && savedConfig.has("reelAtBobberTicks")) {
                        int oldTiming = savedConfig.get("reelAtBobberTicks").getAsInt();
                        INSTANCE.reelInUsingPing = oldTiming > 0;
                        INSTANCE.reelPingMs = oldTiming == 198 ? 100 : 0;
                    }
                }
            } catch (Exception e) {
                System.err.println("[FuschenAddons] Failed to load config: " + e.getMessage());
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            System.err.println("[FuschenAddons] Failed to save config: " + e.getMessage());
        }
    }
}
