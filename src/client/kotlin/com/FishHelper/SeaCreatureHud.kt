package com.FishHelper

import foo.starred.cascade.graphics.extensions.rectangle.rounded.roundedRectangle
import foo.starred.cascade.graphics.geometry.CascadeGeometricRadius
import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import foo.starred.cascade.graphics.font.CascadeFonts
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.text.NumberFormat
import java.util.Locale

object SeaCreatureHud {
    const val HEIGHT = 55
    fun barWidth(screenWidth: Int) = minOf(250, screenWidth)
    fun position() = HudPosition(Config.INSTANCE.healthbarX, Config.INSTANCE.healthbarY)
    private val numbers = NumberFormat.getIntegerInstance(Locale.US)

    @JvmStatic fun initialize() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("tsclient", "sea_creature_health")) { graphics, _ ->
            val client = Minecraft.getInstance()
            if (Config.INSTANCE.seaCreatureHealthbarEnabled && !client.options.hideGui && client.screen !is HudEditorScreen) {
                SeaCreatureTracker.INSTANCE.hudTarget(client)?.let { entry ->
                    val hp = if (SeaCreatureTracker.INSTANCE.entity(entry) == null) entry.nametag().unknownHealth() else entry.nametag()
                    draw(graphics, client.window.guiScaledWidth, client.window.guiScaledHeight, hp, false, false)
                }
            }
        }
    }

    fun draw(graphics: GuiGraphicsExtractor, screenWidth: Int, screenHeight: Int, data: SeaCreatureNametag,
             preview: Boolean, dragging: Boolean) {
        val w = barWidth(screenWidth)
        val x = position().pixelX(screenWidth, w)
        val y = position().pixelY(screenHeight, HEIGHT)
        graphics.nextStratum()
        graphics.roundedRectangle(x.toFloat(), y.toFloat(), w.toFloat(), HEIGHT.toFloat(), 0xF5111827.toInt(), CascadeGeometricRadius(8f))
        graphics.nextStratum()
        graphics.roundedRectangle((x + 8).toFloat(), (y + 8).toFloat(), 3f, 24f,
            if (dragging) 0xFFFFFFFF.toInt() else 0xFF6ADBCC.toInt(), CascadeGeometricRadius(1.5f))
        graphics.nextStratum()
        text(graphics, CascadeFonts.sans.truncate(data.name() + if (preview) " · Preview" else "", 11, w - 30, "…"), x + 17, y + 6, 0xFFEEEFF5.toInt(), 11)
        val current = data.currentHp()
        val maximum = data.maxHp()?.takeIf { it > 0 }
        val health = if (current == null && maximum == null) "HP unknown" else
            (current?.let { numbers.format(it) } ?: "?") + (maximum?.let { " / ${numbers.format(it)}" } ?: " · Max HP unknown")
        text(graphics, CascadeFonts.sans.truncate(health, 9, w - 30, "…"), x + 17, y + 24, 0xFF9AA6BC.toInt(), 9)
        graphics.roundedRectangle((x + 10).toFloat(), (y + 43).toFloat(), (w - 20).toFloat(), 5f, 0xFF354761.toInt(), CascadeGeometricRadius(2.5f))
        if (current != null && maximum != null) {
            val fill = ((current / maximum).coerceIn(0.0, 1.0) * (w - 20)).toFloat()
            if (fill > 0) {
                graphics.nextStratum()
                graphics.roundedRectangle((x + 10).toFloat(), (y + 43).toFloat(), fill, 5f, 0xFF6ADBCC.toInt(), CascadeGeometricRadius(2.5f))
            }
        }
    }

    internal fun text(graphics: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int, size: Int) {
        CascadeFonts.sans.extract(graphics, text, x, y, CascadeGeometricColor(color), shadow = false, size = size)
    }
}
