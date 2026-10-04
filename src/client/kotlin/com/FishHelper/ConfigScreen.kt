package com.FishHelper

import com.mojang.blaze3d.platform.InputConstants
import foo.starred.cascade.graphics.extensions.rectangle.rounded.roundedRectangle
import foo.starred.cascade.graphics.geometry.CascadeGeometricRadius
import foo.starred.cascade.graphics.geometry.CascadeGeometricColor
import foo.starred.cascade.graphics.font.CascadeFonts
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/** Cascade draws the surface; native widgets retain keyboard focus, narration and text editing. */
class ConfigScreen(private val parent: Screen?, private val toggleKey: KeyMapping) :
    Screen(Component.literal("FuschenAddons Config")) {
    private enum class Tab(val label: String) { FISHING("Fishing"), THUNDER("Thunder"), GENERAL("General") }
    private data class Row(val title: String, val detail: String, val create: (Int, Int, Int) -> Unit)

    private var tab = Tab.FISHING
    private var scroll = 0
    private var listeningForKey = false
    private var keyButton: Button? = null
    private val rows = mutableListOf<Row>()
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var visibleRows = 1
    private val rowHeight = 40
    private data class Dropdown(val owner: CascadeButton, val labels: List<String>, val choose: (Int) -> Unit,
                                var highlighted: Int, var offset: Int = 0)
    private var dropdown: Dropdown? = null

    override fun init() {
        panelWidth = min(600, width - 20)
        panelHeight = min(420, height - 16)
        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight) / 2
        visibleRows = max(1, (panelHeight - 134) / rowHeight)
        rebuild()
    }

    private fun rebuild() {
        dropdown = null
        clearWidgets()
        keyButton = null
        rows.clear()
        val config = Config.INSTANCE
        when (tab) {
            Tab.FISHING -> {
                choice("Action weapon", "Weapon used after a catch", Config.ActionWeapon.values().map { it.displayName }, { config.actionWeapon.ordinal }) {
                    config.actionWeapon = Config.ActionWeapon.values()[it]
                }
                choice("Flare", "Place when no nearby flare or Plasmaflux is active", Config.FlareTier.values().map { it.displayName }, { config.flareTier.ordinal }) {
                    config.flareTier = Config.FlareTier.values()[it]
                }
                choice("Fishing pet", "Position in the Pets menu (1–7)", (1..7).map { "Pet $it" }, { config.petNumber - 1 }) {
                    config.petNumber = it + 1
                }
                toggle("Slugfish timing", "Wait 10 seconds, adjusted for your ping", { config.slugfishReelEnabled }) {
                    config.slugfishReelEnabled = it
                }
                toggle("Ping-adjusted reel", "Use the hook countdown for all fish", { config.reelInUsingPing }) {
                    config.reelInUsingPing = it
                }
                toggle("Close menu to reel", "Close any open screen when a bite signal is ready", { config.closeMenuWhenReeling }) {
                    config.closeMenuWhenReeling = it
                }
                number("Lowest ping", "Round-trip latency in milliseconds (0–5000)", { config.reelPingMs.toString() }, false) {
                    config.reelPingMs = it.toInt().coerceIn(0, 5000)
                }
            }
            Tab.THUNDER -> {
                toggle("Thunder Muter", "Mute lightning/guardian sounds in SkyBlock; independent of FishHelper", { config.thunderMuterEnabled }) {
                    config.thunderMuterEnabled = it
                }
                toggle("Thunder response", "Interrupt fishing when your Thunder spawn message appears", { config.thunderResponseEnabled }) {
                    config.thunderResponseEnabled = it
                }
                info("01  Aim and freeze", "Face Thunder; use Ice Spray Wand if it is in your hotbar.")
                info("02  Ink Wand", "Keep aiming at Thunder and use Ink Wand if available.")
                info("03  Hyperion", "Look down; use at 5 CPS within 5 blocks. Wait outside range until all tracked Thunder are gone.")
                info("Return to fishing", "Restore your view and slot. Temporary interruptions resume automatically.")
            }
            Tab.GENERAL -> {
                toggle("Sea Creature healthbar", "Show HP from creature nametags; keep the nearest target until it leaves", { config.seaCreatureHealthbarEnabled }) {
                    config.seaCreatureHealthbarEnabled = it
                }
                rows += Row("Healthbar position", "Drag the preview to move it · /fa gui") { x, y, w ->
                    button(x, y, w, "Edit HUD") { minecraft.setScreen(HudEditorScreen(this)) }
                }
                rows += Row("Start / stop key", "Click to bind a keyboard key; Escape cancels") { x, y, w ->
                    keyButton = button(x, y, w, keyLabel()) {
                        listeningForKey = true
                        keyButton?.message = Component.literal("Press a key...")
                    }
                    keyButton?.setTooltip(Tooltip.create(Component.literal("Start / stop fishing key")))
                }
                toggle("Grinch auto clicker", "Left-click a hooked Grinch under the crosshair", { config.grinchAutoClickerEnabled }) {
                    config.grinchAutoClickerEnabled = it
                }
                number("Grinch CPS", "Clicks per second (3–15)", { config.grinchClickCps.toString() }, true) {
                    config.grinchClickCps = it.coerceIn(3.0, 15.0)
                }
                toggle("Hoppity auto-buy", "Buy offered rabbits only when they are not already owned", { config.autoBuyHoppityRabbit }) {
                    config.autoBuyHoppityRabbit = it
                }
                toggle("Random movement", "Allow the movement keybind (default K)", { config.randomMovementEnabled }) {
                    config.randomMovementEnabled = it
                }
            }
        }

        val tabWidth = (panelWidth - 32) / 3
        Tab.entries.forEachIndexed { index, value ->
            button(panelX + 12 + index * (tabWidth + 4), panelY + 47, tabWidth, value.label, value == tab) {
                listeningForKey = false
                tab = value
                scroll = 0
                rebuild()
            }
        }
        scroll = scroll.coerceIn(0, max(0, rows.size - visibleRows))
        rows.drop(scroll).take(visibleRows).forEachIndexed { index, row ->
            val controlWidth = min(128, panelWidth / 3)
            row.create(panelX + panelWidth - controlWidth - 22, panelY + 85 + index * rowHeight, controlWidth)
        }
        val footerY = panelY + panelHeight - 34
        button(panelX + panelWidth - 88, footerY, 76, "Done") { onClose() }
        button(panelX + 12, footerY, 28, "↑") { moveScroll(-1) }.active = scroll > 0
        button(panelX + 44, footerY, 28, "↓") { moveScroll(1) }.active = scroll + visibleRows < rows.size
    }

    private fun toggle(title: String, detail: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        rows += Row(title, detail) { x, y, w ->
            val control = button(x, y, w, if (get()) "ON" else "OFF", get()) { current ->
                set(!get())
                Config.save()
                current.message = Component.literal(if (get()) "ON" else "OFF")
                current.selected = get()
            }
            control.setTooltip(Tooltip.create(Component.literal("$title: $detail")))
        }
    }

    private fun choice(title: String, detail: String, labels: List<String>, get: () -> Int, set: (Int) -> Unit) {
        rows += Row(title, detail) { x, y, w ->
            button(x, y, w, "${labels[get()]} ▾") { current ->
                listeningForKey = false
                dropdown = Dropdown(current, labels, { index ->
                    set(index)
                    Config.save()
                    current.message = Component.literal("${labels[index]} ▾")
                }, get())
                revealDropdownSelection()
            }.setTooltip(Tooltip.create(Component.literal("$title: $detail")))
        }
    }

    private fun dropdownLayout(menu: Dropdown) = DropdownLayout.place(width, height, menu.owner.x, menu.owner.y,
        menu.owner.width, menu.owner.height, menu.labels.size)

    private fun revealDropdownSelection() {
        val menu = dropdown ?: return
        val visible = dropdownLayout(menu).visibleRows()
        menu.offset = menu.offset.coerceIn(max(0, menu.highlighted - visible + 1), menu.highlighted)
            .coerceIn(0, max(0, menu.labels.size - visible))
    }

    private fun selectDropdown(index: Int) {
        val menu = dropdown ?: return
        menu.choose(index)
        dropdown = null
        setFocused(menu.owner)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val menu = dropdown
        if (menu != null) {
            val row = dropdownLayout(menu).rowAt(event.x(), event.y())
            if (event.button() == 0 && row >= 0) selectDropdown(menu.offset + row)
            else dropdown = null
            return true // Closing the popup must not activate a control underneath it.
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    private fun info(title: String, detail: String) {
        rows += Row(title, detail) { _, _, _ -> }
    }

    private fun number(title: String, detail: String, get: () -> String, decimal: Boolean, set: (Double) -> Unit) {
        rows += Row(title, detail) { x, y, w ->
            val field = object : EditBox(font, x + 4, y + 4, w - 8, 18, Component.literal("$title: $detail")) {
                override fun setFocused(focused: Boolean) {
                    if (!focused) value = get()
                    super.setFocused(focused)
                }
            }
            field.setMaxLength(if (decimal) 5 else 4)
            field.value = get()
            field.setResponder { value ->
                if (!value.matches(if (decimal) Regex("[0-9]*\\.?[0-9]*") else Regex("[0-9]*"))) return@setResponder
                value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                    set(it)
                    Config.save()
                }
            }
            field.setHint(Component.literal(if (decimal) "3–15" else "ms"))
            field.setTooltip(Tooltip.create(Component.literal(detail)))
            addRenderableWidget(field)
        }
    }

    private fun button(x: Int, y: Int, w: Int, text: String, selected: Boolean = false, press: (CascadeButton) -> Unit): CascadeButton {
        return addRenderableWidget(CascadeButton(x, y, w, 26, Component.literal(text), selected, press))
    }

    private fun keyLabel() = if (listeningForKey) "Press a key..." else toggleKey.translatedKeyMessage.string

    override fun keyPressed(event: KeyEvent): Boolean {
        dropdown?.let { menu ->
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE -> dropdown = null
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> selectDropdown(menu.highlighted)
                GLFW.GLFW_KEY_UP -> menu.highlighted = max(0, menu.highlighted - 1)
                GLFW.GLFW_KEY_DOWN -> menu.highlighted = min(menu.labels.lastIndex, menu.highlighted + 1)
                GLFW.GLFW_KEY_HOME -> menu.highlighted = 0
                GLFW.GLFW_KEY_END -> menu.highlighted = menu.labels.lastIndex
                GLFW.GLFW_KEY_TAB -> { dropdown = null; return super.keyPressed(event) }
            }
            revealDropdownSelection()
            return true
        }
        if (listeningForKey) {
            if (event.key() != InputConstants.KEY_ESCAPE) {
                Config.INSTANCE.toggleKeyCode = event.key()
                toggleKey.setKey(InputConstants.getKey(event))
                KeyMapping.resetMapping()
                Config.save()
            }
            listeningForKey = false
            keyButton?.message = Component.literal(keyLabel())
            return true
        }
        return super.keyPressed(event)
    }

    private fun moveScroll(amount: Int) {
        val next = (scroll + amount).coerceIn(0, max(0, rows.size - visibleRows))
        if (next != scroll) {
            listeningForKey = false
            scroll = next
            rebuild()
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        dropdown?.let { menu ->
            val visible = dropdownLayout(menu).visibleRows()
            if (scrollY != 0.0) menu.offset = (menu.offset + if (scrollY > 0) -1 else 1).coerceIn(0, max(0, menu.labels.size - visible))
            menu.highlighted = menu.highlighted.coerceIn(menu.offset, min(menu.labels.lastIndex, menu.offset + visible - 1))
            return true
        }
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY + 79 && mouseY < panelY + panelHeight - 40) {
            if (scrollY != 0.0) moveScroll(if (scrollY > 0) -1 else 1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0xB0080C14.toInt())
        graphics.roundedRectangle(panelX.toFloat(), panelY.toFloat(), panelWidth.toFloat(), panelHeight.toFloat(), 0xFA111827.toInt(), CascadeGeometricRadius(12f))
        graphics.nextStratum()
        graphics.roundedRectangle((panelX + 12).toFloat(), (panelY + 12).toFloat(), 3f, 24f, ACCENT, CascadeGeometricRadius(1.5f))
        graphics.nextStratum()
        drawText(graphics, "FuschenAddons", panelX + 23, panelY + 9, TEXT, 15)
        drawText(graphics, "Make every cast count.", panelX + 23, panelY + 28, MUTED, 9)
        rows.drop(scroll).take(visibleRows).forEachIndexed { index, row ->
            val y = panelY + 79 + index * rowHeight
            graphics.roundedRectangle((panelX + 12).toFloat(), y.toFloat(), (panelWidth - 24).toFloat(), 39f, 0xFF1B2536.toInt(), CascadeGeometricRadius(6f))
            graphics.nextStratum()
            val textWidth = if (tab == Tab.THUNDER && scroll + index > 1) panelWidth - 44 else panelWidth - min(128, panelWidth / 3) - 56
            drawText(graphics, ellipsize(row.title, textWidth, 11), panelX + 22, y + 5, TEXT, 11)
            val detail = ellipsize(row.detail, textWidth, 9)
            drawText(graphics, detail, panelX + 22, y + 23, MUTED, 9)
            if (dropdown == null && mouseX >= panelX + 12 && mouseX < panelX + panelWidth - 12 && mouseY >= y && mouseY < y + 39) {
                graphics.setTooltipForNextFrame(Component.literal(row.detail), mouseX, mouseY)
            }
        }
        val last = min(scroll + visibleRows, rows.size)
        val footer = if (panelWidth < 420) "Saved · ${scroll + 1}–$last/${rows.size}" else "${scroll + 1}–$last / ${rows.size} · Saved automatically"
        drawText(graphics, footer, panelX + 82, panelY + panelHeight - 26, MUTED, 9)
        graphics.nextStratum()
        super.extractRenderState(graphics, if (dropdown == null) mouseX else -1, if (dropdown == null) mouseY else -1, delta)
        dropdown?.let { menu ->
            val box = dropdownLayout(menu)
            graphics.nextStratum()
            graphics.roundedRectangle(box.x().toFloat(), box.y().toFloat(), box.width().toFloat(), box.height().toFloat(), 0xFF354761.toInt(), CascadeGeometricRadius(5f))
            graphics.nextStratum()
            val hovered = box.rowAt(mouseX.toDouble(), mouseY.toDouble())
            repeat(box.visibleRows()) { row ->
                val index = menu.offset + row
                val y = box.y() + DropdownLayout.PADDING + row * DropdownLayout.ROW_HEIGHT
                if (row == hovered || index == menu.highlighted) {
                    graphics.roundedRectangle((box.x() + 3).toFloat(), y.toFloat(), (box.width() - 6).toFloat(), DropdownLayout.ROW_HEIGHT.toFloat(), 0xFF245D69.toInt(), CascadeGeometricRadius(3f))
                    graphics.nextStratum()
                }
                drawText(graphics, ellipsize(menu.labels[index], box.width() - 18, 11), box.x() + 8, y + 4, TEXT, 11)
            }
            if (menu.labels.size > box.visibleRows()) {
                val track = (box.height() - 2 * DropdownLayout.PADDING).toFloat()
                val thumb = track * box.visibleRows() / menu.labels.size
                val thumbY = box.y() + DropdownLayout.PADDING + (track - thumb) * menu.offset / (menu.labels.size - box.visibleRows())
                graphics.nextStratum()
                graphics.roundedRectangle((box.x() + box.width() - 6).toFloat(), (box.y() + DropdownLayout.PADDING).toFloat(), 3f, track, 0xFF1B2536.toInt(), CascadeGeometricRadius(1.5f))
                graphics.nextStratum()
                graphics.roundedRectangle((box.x() + box.width() - 6).toFloat(), thumbY, 3f, thumb, ACCENT, CascadeGeometricRadius(1.5f))
            }
        }
    }

    override fun onClose() {
        dropdown = null
        listeningForKey = false
        Config.save()
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen() = false

    private class CascadeButton(x: Int, y: Int, w: Int, h: Int, label: Component, var selected: Boolean, press: (CascadeButton) -> Unit) :
        Button(x, y, w, h, label, OnPress { press(it as CascadeButton) }, DEFAULT_NARRATION) {
        override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            graphics.nextStratum()
            val color = when {
                !active -> 0xFF202A39.toInt()
                isHoveredOrFocused -> 0xFF405578.toInt()
                selected -> 0xFF245D69.toInt()
                else -> 0xFF2B3850.toInt()
            }
            if (isFocused) {
                graphics.roundedRectangle((x - 1).toFloat(), (y - 1).toFloat(), (width + 2).toFloat(), (height + 2).toFloat(), ACCENT, CascadeGeometricRadius(6f))
                graphics.nextStratum()
            }
            graphics.roundedRectangle(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), color, CascadeGeometricRadius(5f))
            graphics.nextStratum()
            val label = ellipsize(message.string, width - 8, 11)
            val labelX = x + (width - CascadeFonts.sans.width(label, 11)) / 2f
            val labelY = y + (height - CascadeFonts.sans.regular.height * 11) / 2f
            drawText(graphics, label, labelX, labelY, if (active) TEXT else MUTED, 11)
        }
    }

    companion object {
        private const val TEXT = -0x11100B
        private const val MUTED = 0xFF9AA6BC.toInt()
        private const val ACCENT = -0x952434

        private fun ellipsize(text: String, maxWidth: Int, size: Int): String =
            CascadeFonts.sans.truncate(text, size, maxWidth, "…", cached = true)

        private fun drawText(graphics: GuiGraphicsExtractor, text: String, x: Number, y: Number, color: Int, size: Int) {
            CascadeFonts.sans.extract(graphics, text, x, y, CascadeGeometricColor(color), shadow = false, size = size)
        }
    }
}
