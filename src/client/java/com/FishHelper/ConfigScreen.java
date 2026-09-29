package com.FishHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private final KeyMapping toggleKey;

    private Button keyButton;
    private Button hyperionButton;
    private Button flareButton;
    private Button petButton;
    private Button slugfishButton;
    private Button reelInButton;
    private Button grinchClickerButton;
    private EditBox pingField;
    private EditBox grinchCpsField;
    private boolean listeningForKey = false;

    public ConfigScreen(Screen parent, KeyMapping toggleKey) {
        super(Component.literal("FuschenAddons Config"));
        this.parent = parent;
        this.toggleKey = toggleKey;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 105;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 23;

        keyButton = Button.builder(getKeyLabel(), btn -> {
                    listeningForKey = true;
                    keyButton.setMessage(Component.literal("Press a key..."));
                })
                .bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(keyButton);

        hyperionButton = Button.builder(getHyperionLabel(), btn -> {
                    Config.INSTANCE.useHyperion = !Config.INSTANCE.useHyperion;
                    Config.save();
                    hyperionButton.setMessage(getHyperionLabel());
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(hyperionButton);

        flareButton = Button.builder(getFlareLabel(), btn -> {
                    Config.INSTANCE.flareTier = Config.INSTANCE.flareTier.next();
                    Config.save();
                    flareButton.setMessage(getFlareLabel());
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(flareButton);

        petButton = Button.builder(getPetLabel(), btn -> {
                    Config.INSTANCE.petNumber = Config.INSTANCE.petNumber % 7 + 1;
                    Config.save();
                    petButton.setMessage(getPetLabel());
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(petButton);

        slugfishButton = Button.builder(getSlugfishLabel(), btn -> {
                    Config.INSTANCE.slugfishReelEnabled = !Config.INSTANCE.slugfishReelEnabled;
                    Config.save();
                    slugfishButton.setMessage(getSlugfishLabel());
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(slugfishButton);

        reelInButton = Button.builder(getReelInLabel(), btn -> {
                    Config.INSTANCE.reelInUsingPing = !Config.INSTANCE.reelInUsingPing;
                    Config.save();
                    reelInButton.setMessage(getReelInLabel());
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing * 5, buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(reelInButton);

        pingField = new EditBox(this.font, centerX - 5, startY + spacing * 6, 65, buttonHeight,
                Component.literal("Ping (ms)"));
        pingField.setMaxLength(4);
        pingField.setValue(Integer.toString(Config.INSTANCE.reelPingMs));
        pingField.setResponder(this::updatePingValue);
        pingField.setHint(Component.literal("ms"));
        addRenderableWidget(pingField);

        grinchClickerButton = Button.builder(getGrinchClickerLabel(), btn -> {
                    Config.INSTANCE.grinchAutoClickerEnabled = !Config.INSTANCE.grinchAutoClickerEnabled;
                    Config.save();
                    grinchClickerButton.setMessage(getGrinchClickerLabel());
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing * 7, buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(grinchClickerButton);

        grinchCpsField = new EditBox(this.font, centerX - 5, startY + spacing * 8, 65, buttonHeight,
                Component.literal("Grinch CPS"));
        grinchCpsField.setMaxLength(5);
        grinchCpsField.setValue(Double.toString(Config.INSTANCE.grinchClickCps));
        grinchCpsField.setResponder(this::updateGrinchCps);
        grinchCpsField.setHint(Component.literal("3–15"));
        addRenderableWidget(grinchCpsField);

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
                    listeningForKey = false;
                    savePingValue();
                    saveGrinchCps();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(parent);
                    }
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing * 9, buttonWidth, buttonHeight)
                .build());
    }

    private Component getKeyLabel() {
        String keyName = toggleKey.getTranslatedKeyMessage().getString();
        return Component.literal("Start/Stop Key: " + keyName);
    }

    private Component getHyperionLabel() {
        return Component.literal("Use Hyperion: " + (Config.INSTANCE.useHyperion ? "§aON" : "§cOFF"));
    }

    private Component getFlareLabel() {
        return Component.literal("Flare Tier: " + Config.INSTANCE.flareTier.displayName);
    }

    private Component getPetLabel() {
        return Component.literal("Pet to equip: " + Config.INSTANCE.petNumber);
    }

    private Component getSlugfishLabel() {
        return Component.literal("Slugfish: " + (Config.INSTANCE.slugfishReelEnabled ? "§aON" : "§cOFF"));
    }

    private Component getReelInLabel() {
        return Component.literal("Adjust reel time for ping: "
                + (Config.INSTANCE.reelInUsingPing ? "§aON" : "§cOFF"));
    }

    private Component getGrinchClickerLabel() {
        return Component.literal("Grinch Auto Clicker: "
                + (Config.INSTANCE.grinchAutoClickerEnabled ? "§aON" : "§cOFF"));
    }

    private void updatePingValue(String value) {
        if (value.isEmpty()) {
            return;
        }
        try {
            Config.INSTANCE.reelPingMs = Math.max(0, Math.min(5000, Integer.parseInt(value)));
            Config.save();
        } catch (NumberFormatException ignored) {
            // Ignore incomplete/non-numeric input; savePingValue restores the last valid value.
        }
    }

    private void savePingValue() {
        updatePingValue(pingField.getValue());
        pingField.setValue(Integer.toString(Config.INSTANCE.reelPingMs));
    }

    private void updateGrinchCps(String value) {
        if (value.isEmpty() || value.equals(".")) {
            return;
        }
        try {
            double cps = Double.parseDouble(value);
            if (Double.isFinite(cps)) {
                Config.INSTANCE.grinchClickCps = Math.max(3.0, Math.min(15.0, cps));
                Config.save();
            }
        } catch (NumberFormatException ignored) {
            // Ignore an incomplete value until it can be parsed or the screen closes.
        }
    }

    private void saveGrinchCps() {
        updateGrinchCps(grinchCpsField.getValue());
        grinchCpsField.setValue(Double.toString(Config.INSTANCE.grinchClickCps));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningForKey) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                listeningForKey = false;
                keyButton.setMessage(getKeyLabel());
                return true;
            }
            Config.INSTANCE.toggleKeyCode = event.key();
            Config.save();
            // Update the live KeyMapping so the change applies immediately
            toggleKey.setKey(InputConstants.getKey(event));
            KeyMapping.resetMapping();
            listeningForKey = false;
            keyButton.setMessage(getKeyLabel());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Dark translucent background
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);

        graphics.centeredText(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        graphics.centeredText(this.font, Component.literal("§7Changes save automatically"), this.width / 2, 56, 0xAAAAAA);
        graphics.text(
                this.font,
                Component.literal("Lowest Ping:"),
                this.width / 2 - 78,
                this.height / 2 - 105 + 23 * 6 + 6,
                0xFFFFFF
        );
        graphics.text(
                this.font,
                Component.literal("Grinch CPS:"),
                this.width / 2 - 78,
                this.height / 2 - 105 + 23 * 8 + 6,
                0xFFFFFF
        );

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        listeningForKey = false;
        savePingValue();
        saveGrinchCps();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
