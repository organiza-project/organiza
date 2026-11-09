package org.organiza.desktop

import androidx.compose.ui.graphics.toArgb
import javax.swing.JMenuItem
import javax.swing.JSeparator
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.organiza.ui.DarkPalette

class TrayMenuTest {
    @Test
    fun `the tray menu is drawn in the palette and highlights in grey, not white`() {
        val opened = mutableListOf<String>()
        val menu = trayMenu(DarkPalette, listOf("Open Organiza" to { opened += "open" }, null, "Quit" to { opened += "quit" }))
        val parts = menu.components.toList()
        assertTrue(parts[1] is JSeparator)
        val open = parts[0] as JMenuItem
        assertEquals(DarkPalette.surface.toArgb(), open.background.rgb)
        assertEquals(DarkPalette.text.toArgb(), open.foreground.rgb)
        // The item made after the colours were set keeps them as its UI's highlight.
        assertEquals(DarkPalette.raised.toArgb(), UIManager.getColor("MenuItem.selectionBackground").rgb)
        open.doClick()
        (parts[2] as JMenuItem).doClick()
        assertEquals(listOf("open", "quit"), opened)
    }
}
