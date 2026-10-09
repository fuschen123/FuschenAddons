package com.FishHelper;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import java.util.HashSet;
import java.util.UUID;

/** Uses the same encounter relevance as the fishing pause, but has no action controls. */
final class JawbusShurikenWarningFeature {
    static final JawbusShurikenWarningFeature INSTANCE = new JawbusShurikenWarningFeature();
    private final JawbusPauseFeature encounter = new JawbusPauseFeature();
    private final JawbusShurikenWarning warning = new JawbusShurikenWarning();
    private ClientLevel world;
    private ClientPacketListener connection;

    static void initialize() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("tsclient", "jawbus_shuriken"),
                (graphics, delta) -> INSTANCE.draw(Minecraft.getInstance(), graphics));
    }

    void reset() { encounter.reset(); warning.reset(); world = null; connection = null; }

    void tick(Minecraft client) {
        if (world != client.level || connection != client.getConnection()) {
            reset(); world = client.level; connection = client.getConnection();
        }
        if (client.level == null || client.player == null) { reset(); return; }
        encounter.tick(client);
        var alive = new HashSet<UUID>();
        for (var target : encounter.targets()) {
            var entity = encounter.resolve(client, target);
            if (entity != null && entity.isAlive() && !entity.isRemoved()
                    && !SeaCreatureTracker.INSTANCE.confirmedDead(target.uuid())) alive.add(target.uuid());
        }
        warning.tick(alive, SeaCreatureTracker.INSTANCE.shurikenObservations());
    }

    boolean visible(Minecraft client) {
        return client.player != null && client.level != null && world == client.level
                && connection == client.getConnection() && warning.visible();
    }

    private void draw(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!visible(client) || client.options.hideGui) return;
        String text = "No Shuriken!";
        int halfWidth = client.font.width(text) / 2;
        int halfHeight = client.font.lineHeight / 2;
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(client.getWindow().getGuiScaledWidth() / 2f, client.getWindow().getGuiScaledHeight() / 2f);
        graphics.pose().scale(2f, 2f);
        graphics.fill(-halfWidth - 6, -halfHeight - 5, halfWidth + 6, halfHeight + 5, 0xBC160B0B);
        graphics.nextStratum();
        graphics.text(client.font, text, -halfWidth, -halfHeight, 0xFFFF5555, true);
        graphics.pose().popMatrix();
    }
}
