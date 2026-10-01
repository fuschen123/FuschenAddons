package com.FishHelper

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class HudEditorScreen(private val parent: Screen?) : Screen(Component.literal("FuschenAddons HUD editor")) {
    private var moving = false
    private var grabX = 0.0
    private var grabY = 0.0
    override fun init() { moving = false }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0x90080C14.toInt())
        val helpY = if (SeaCreatureHud.position().pixelY(height, SeaCreatureHud.HEIGHT) + SeaCreatureHud.HEIGHT > height - 40) 12 else height - 27
        SeaCreatureHud.text(graphics, "Drag the healthbar · Esc to save and close", 12, helpY, 0xFFEEEFF5.toInt(), 10)
        val creature = SeaCreatureTracker.INSTANCE.hudTarget(minecraft)
        // Explicit sample values; never used for a real creature's missing health.
        SeaCreatureHud.draw(graphics, width, height, creature?.nametag() ?: SeaCreatureNametag("Sea Creature", 65.0, 100.0), creature == null, moving)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val w = SeaCreatureHud.barWidth(width)
        val pos = SeaCreatureHud.position()
        val x = pos.pixelX(width, w)
        val y = pos.pixelY(height, SeaCreatureHud.HEIGHT)
        if (event.button() == 0 && event.x() >= x && event.x() < x + w && event.y() >= y && event.y() < y + SeaCreatureHud.HEIGHT) {
            moving = true; grabX = event.x() - x; grabY = event.y() - y
            return true
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (moving && event.button() == 0) {
            val pos = HudPosition.fromPixels(event.x() - grabX, event.y() - grabY, width, height, SeaCreatureHud.barWidth(width), SeaCreatureHud.HEIGHT)
            Config.INSTANCE.healthbarX = pos.x(); Config.INSTANCE.healthbarY = pos.y()
            return true
        }
        return super.mouseDragged(event, deltaX, deltaY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (moving && event.button() == 0) { moving = false; Config.save(); return true }
        return super.mouseReleased(event)
    }

    override fun removed() { Config.save(); moving = false }
    override fun onClose() { Config.save(); minecraft.setScreen(parent) }
    override fun isPauseScreen() = false
}
