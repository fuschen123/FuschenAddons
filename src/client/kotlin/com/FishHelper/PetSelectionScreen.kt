package com.FishHelper

import foo.starred.cascade.graphics.extensions.rectangle.rounded.roundedRectangle
import foo.starred.cascade.graphics.geometry.CascadeGeometricRadius
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.min

/** A visual copy of observed /pets pages, preserving every pet's row and column. */
class PetSelectionScreen(private val parent: Screen?) : Screen(Component.literal("Choose fishing pet")) {
    private var page = 1
    private var x = 0
    private var y = 0
    private var w = 0
    private var h = 0
    private var cell = 40
    private var gridX = 0
    private var gridY = 0

    override fun init() {
        w = min(420, width - 16)
        cell = min(48, min((w - 32) / 7, (height - 146) / 4)).coerceAtLeast(20)
        h = cell * 4 + 130
        x = (width - w) / 2; y = (height - h) / 2
        gridX = x + (w - cell * 7) / 2; gridY = y + 57
        rebuild()
    }

    private fun rebuild() {
        clearWidgets()
        page = page.coerceIn(1, Config.INSTANCE.petPageCount.coerceAtLeast(1))
        PetMenus.page(page).forEach { entry ->
            val column = entry.slot() % 9 - 1
            val row = entry.slot() / 9 - 1
            val pet = entry.pet()
            val selected = Config.INSTANCE.selectedPet?.matches(pet) == true
            val valid = PetMenus.selectable(pet)
            val identity = if (pet.stable()) "\nID: …${pet.id().takeLast(6)}" else ""
            val detail = "${pet.name()}\n${pet.rarity()} · Lvl ${pet.level()}\nHeld item: ${pet.heldItem()}$identity"
            addRenderableWidget(PetButton(gridX + column * cell + 2, gridY + row * cell + 2,
                cell - 4, entry, selected, detail) {
                Config.INSTANCE.selectedPet = pet; Config.save(); rebuild()
            }).also {
                it.active = valid
                it.setTooltip(Tooltip.create(Component.literal(detail + when {
                    !valid -> "\nRead all /pets pages; this pet must be uniquely identifiable."
                    selected -> "\nSelected fishing pet"
                    else -> "\nClick to select as fishing pet"
                })))
            }
        }
        val footerY = y + h - 34
        addRenderableWidget(ConfigScreen.CascadeButton(x + 14, footerY, 28, 24, Component.literal("‹"), false) {
            changePage(-1)
        }).active = page > 1
        addRenderableWidget(ConfigScreen.CascadeButton(x + 46, footerY, 28, 24, Component.literal("›"), false) {
            changePage(1)
        }).active = page < Config.INSTANCE.petPageCount
        addRenderableWidget(ConfigScreen.CascadeButton(x + 82, footerY, 96, 24, Component.literal("Clear catalog"), false) {
            Config.INSTANCE.petPages.clear(); Config.INSTANCE.petMenuPages.clear()
            Config.INSTANCE.petPageCount = 1; page = 1; Config.save(); rebuild()
        })
        addRenderableWidget(ConfigScreen.CascadeButton(x + w - 80, footerY, 66, 24, Component.literal("Done"), false) { onClose() })
    }

    private fun changePage(amount: Int) {
        val next = (page + amount).coerceIn(1, Config.INSTANCE.petPageCount.coerceAtLeast(1))
        if (next != page) { page = next; rebuild() }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0) changePage(if (scrollY > 0) -1 else 1)
        return true
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0xB0080C14.toInt())
        graphics.roundedRectangle(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 0xFA111827.toInt(), CascadeGeometricRadius(12f))
        graphics.nextStratum()
        ConfigScreen.drawText(graphics, "Choose fishing pet", x + 14, y + 12, 0xFFEEF0F5.toInt(), 15)
        ConfigScreen.drawText(graphics, "Pets · Page $page / ${Config.INSTANCE.petPageCount.coerceAtLeast(1)}", x + 14, y + 35, 0xFF9AA6BC.toInt(), 10)
        for (row in 0..3) for (column in 0..6) {
            graphics.roundedRectangle((gridX + column * cell + 2).toFloat(), (gridY + row * cell + 2).toFloat(),
                (cell - 4).toFloat(), (cell - 4).toFloat(), 0xFF202B3D.toInt(), CascadeGeometricRadius(4f))
        }
        graphics.nextStratum()
        if (PetMenus.page(page).isEmpty()) {
            val cy = gridY + cell * 2 - 16
            graphics.fill(gridX, cy - 5, gridX + cell * 7, cy + 29, 0xF0111827.toInt())
            graphics.nextStratum()
            graphics.centeredText(font, "Open /pets page $page to load its pets", x + w / 2, cy, 0xFFEEF0F5.toInt())
            graphics.centeredText(font, "Then return here to choose", x + w / 2, cy + 13, 0xFF9AA6BC.toInt())
        }
        val selected = Config.INSTANCE.selectedPet?.let { "Selected: ${it.name()} · Lvl ${it.level()}" }
            ?: "Hover a pet for details · Click to select"
        graphics.text(font, font.plainSubstrByWidth(selected, w - 28), x + 14, y + h - 52, 0xFF9AA6BC.toInt(), false)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private inner class PetButton(x: Int, y: Int, size: Int, entry: PetMenuSlot, private val selected: Boolean,
                                  detail: String, press: () -> Unit) :
        Button(x, y, size, size, Component.literal(detail), OnPress { press() }, DEFAULT_NARRATION) {
        private val icon = entry.icon()
        private val rarity = when (entry.pet().rarity().uppercase(Locale.ROOT)) {
            "UNCOMMON" -> 0xFF55FF55.toInt()
            "RARE" -> 0xFF5599FF.toInt()
            "EPIC" -> 0xFFB46BFF.toInt()
            "LEGENDARY" -> 0xFFFFAA33.toInt()
            "MYTHIC" -> 0xFFFF55FF.toInt()
            "DIVINE" -> 0xFF55FFFF.toInt()
            else -> 0xFFCCD3DF.toInt()
        }
        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            graphics.nextStratum()
            val border = if (selected || isFocused) 0xFF6ADBD0.toInt() else if (active) rarity else 0xFF566174.toInt()
            graphics.roundedRectangle(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), border, CascadeGeometricRadius(4f))
            graphics.nextStratum()
            graphics.roundedRectangle((x + 1).toFloat(), (y + 1).toFloat(), (width - 2).toFloat(), (height - 2).toFloat(),
                if (selected) 0xFF245D69.toInt() else if (isHoveredOrFocused) 0xFF405578.toInt() else 0xFF263449.toInt(), CascadeGeometricRadius(3f))
            graphics.nextStratum()
            val scale = min(2f, (width - 6) / 16f)
            graphics.pose().pushMatrix()
            graphics.pose().translate(x + (width - 16 * scale) / 2f, y + (height - 16 * scale) / 2f)
            graphics.pose().scale(scale, scale)
            graphics.item(icon, 0, 0)
            graphics.pose().popMatrix()
            if (selected) {
                graphics.nextStratum()
                graphics.text(font, "✔", x + width - 9, y + height - 9, 0xFF8FFFF0.toInt(), true)
            }
        }
    }

    override fun onClose() { minecraft.setScreen(parent) }
    override fun isPauseScreen() = false
}
