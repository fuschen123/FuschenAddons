package com.FishHelper

import foo.starred.cascade.graphics.extensions.rectangle.rounded.roundedRectangle
import foo.starred.cascade.graphics.geometry.CascadeGeometricRadius
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.min
import kotlin.math.max

/** A local catalog of observed /pets pages, never a fabricated inventory. */
class PetSelectionScreen(private val parent: Screen?) : Screen(Component.literal("Choose fishing pet")) {
    private var offset = 0
    private var x = 0
    private var y = 0
    private var w = 0
    private var h = 0
    private var visible = 1
    override fun init() {
        w = min(600, width - 20); h = min(410, height - 16)
        x = (width - w) / 2; y = (height - h) / 2
        visible = max(1, (h - 130) / 48)
        rebuild()
    }
    private fun rebuild() {
        clearWidgets()
        val pets = PetMenus.catalog()
        offset = offset.coerceIn(0, max(0, pets.size - visible))
        pets.drop(offset).take(visible).forEachIndexed { index, pet ->
            val detail = "${pet.rarity()} · Lvl ${pet.level()} · ${pet.heldItem()}"
            val duplicate = pets.count { it.fallback() == pet.fallback() } > 1
            val identity = if (duplicate && pet.stable()) " · #${pet.id().takeLast(6)}" else ""
            val valid = PetMenus.selectable(pet)
            addRenderableWidget(ConfigScreen.CascadeButton(x + 14, y + 70 + index * 48, w - 28, 40,
                Component.literal("${pet.name()}$identity · $detail"), Config.INSTANCE.selectedPet?.matches(pet) == true) {
                Config.INSTANCE.selectedPet = pet; Config.save(); rebuild()
            }).also { button ->
                button.active = valid
                button.setTooltip(Tooltip.create(Component.literal(if (valid) "$detail$identity" else "$detail · Ambiguous identity: read all pages / choose a uniquely identified pet")))
            }
        }
        addRenderableWidget(ConfigScreen.CascadeButton(x + 14, y + h - 38, 36, 26, Component.literal("↑"), false) { offset--; rebuild() }).active = offset > 0
        addRenderableWidget(ConfigScreen.CascadeButton(x + 54, y + h - 38, 36, 26, Component.literal("↓"), false) { offset++; rebuild() }).active = offset + visible < pets.size
        addRenderableWidget(ConfigScreen.CascadeButton(x + 100, y + h - 38, 110, 26, Component.literal("Clear catalog"), false) {
            Config.INSTANCE.petPages.clear(); Config.save(); rebuild()
        })
        addRenderableWidget(ConfigScreen.CascadeButton(x + w - 90, y + h - 38, 76, 26, Component.literal("Done"), false) { onClose() })
    }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0) { offset += if (scrollY > 0) -1 else 1; rebuild() }
        return true
    }
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0xB0080C14.toInt())
        graphics.roundedRectangle(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 0xFA111827.toInt(), CascadeGeometricRadius(12f))
        graphics.nextStratum()
        ConfigScreen.drawText(graphics, "Choose fishing pet", x + 16, y + 12, 0xFFEEF0F5.toInt(), 15)
        ConfigScreen.drawText(graphics, "Read /pets pages first · ${Config.INSTANCE.petPages.size}/${Config.INSTANCE.petPageCount} pages known", x + 16, y + 37, 0xFF9AA6BC.toInt(), 10)
        if (PetMenus.catalog().isEmpty()) ConfigScreen.drawText(graphics, "No observed pets yet", x + 16, y + 75, 0xFF9AA6BC.toInt(), 11)
        graphics.nextStratum()
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }
    override fun onClose() { minecraft.setScreen(parent) }
    override fun isPauseScreen() = false
}
