package org.organiza.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.awt.Font
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import org.organiza.ui.DarkPalette
import org.organiza.ui.LightPalette
import org.organiza.ui.Palette

/** A menu entry; null draws a separator. */
internal typealias TrayItem = Pair<String, () -> Unit>?

/**
 * The tray icon with a menu in the app's palette. AWT draws a tray icon's own menu in fixed light colours
 * with a white highlight that hides the text on a dark desktop. A Swing popup takes the palette, and like
 * a native menu it grabs the pointer, so a click anywhere else closes it.
 */
@Composable
fun OrganizaTray(onOpen: () -> Unit, items: List<TrayItem>) {
    val palette by rememberUpdatedState(if (isSystemInDarkTheme()) DarkPalette else LightPalette)
    val entries by rememberUpdatedState(items)
    val open by rememberUpdatedState(onOpen)
    DisposableEffect(Unit) {
        val tray = SystemTray.getSystemTray()
        val icon = TrayIcon(trayImage(), "Organiza")
        icon.addActionListener { open() }
        icon.addMouseListener(object : MouseAdapter() {
            // X11 reports the popup trigger on press, Windows on release.
            override fun mousePressed(e: MouseEvent) = menu(e)
            override fun mouseReleased(e: MouseEvent) = menu(e)
            private fun menu(e: MouseEvent) {
                if (e.isPopupTrigger) showAt(trayMenu(palette, entries), palette, e.xOnScreen, e.yOnScreen)
            }
        })
        tray.add(icon)
        TrayNotices.icon = icon
        onDispose {
            TrayNotices.icon = null
            tray.remove(icon)
        }
    }
}

/** Notifications through the tray, as Windows shows them; a short-lived icon when Organiza has none showing. */
object TrayNotices {
    @Volatile var icon: TrayIcon? = null

    fun show(title: String, text: String) {
        if (!SystemTray.isSupported()) return
        icon?.let { return it.displayMessage(title, text, TrayIcon.MessageType.NONE) }
        val tray = SystemTray.getSystemTray()
        val temporary = TrayIcon(trayImage(), "Organiza")
        tray.add(temporary)
        temporary.displayMessage(title, text, TrayIcon.MessageType.NONE)
        javax.swing.Timer(NOTICE_MS) { tray.remove(temporary) }.apply { isRepeats = false }.start()
    }

    private const val NOTICE_MS = 10_000
}

/** A multi-resolution image: AWT asks for the size the screen's scale needs and the mark is drawn at it. */
private fun trayImage(): java.awt.Image {
    // AWT's X11 tray says 24x24 whatever the panel gives; GNOME's AppIndicator host gives 16x16 and showed
    // only the top-left corner of a larger picture.
    // shortcut: 16 px is GNOME at 100% scale; read the embedded window's real size if a scaled panel shows a corner again.
    val side = if ("GNOME" in System.getenv("XDG_CURRENT_DESKTOP").orEmpty()) 16f else SystemTray.getSystemTray().trayIconSize.width.toFloat()
    // Windows trays draw transparency; Linux ones need the mark's own ground.
    return (if (WINDOWS) Brand.mark else Brand.trayMark).toAwtImage(Density(1f), LayoutDirection.Ltr, Size(side, side))
}

/** The menu itself: the palette's surface, its raised grey under the pointer, Geist type. */
internal fun trayMenu(palette: Palette, items: List<TrayItem>): JPopupMenu {
    // Swing reads the highlight from the look and feel as each item is made.
    UIManager.put("MenuItem.selectionBackground", palette.raised.awt())
    UIManager.put("MenuItem.selectionForeground", palette.text.awt())
    return JPopupMenu().apply {
        background = palette.surface.awt()
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(palette.rule.awt()),
            BorderFactory.createEmptyBorder(6, 0, 6, 0),
        )
        items.forEach { item ->
            if (item == null) {
                add(JSeparator().apply { foreground = palette.rule.awt(); background = palette.surface.awt() })
            } else {
                add(JMenuItem(item.first).apply {
                    isOpaque = true
                    background = palette.surface.awt()
                    foreground = palette.text.awt()
                    font = MENU_FONT
                    border = BorderFactory.createEmptyBorder(8, 16, 8, 40)
                    addActionListener { item.second() }
                })
            }
        }
    }
}

/** Swing opens a popup from a component on screen; a one-pixel popup window at the pointer is that component. */
private fun showAt(menu: JPopupMenu, palette: Palette, x: Int, y: Int) {
    val anchor = JWindow().apply {
        type = Window.Type.POPUP
        // Seen only when Swing moves the menu off the pointer to keep it on screen.
        background = palette.surface.awt()
        setBounds(x, y, 1, 1)
        isVisible = true
    }
    menu.addPopupMenuListener(object : PopupMenuListener {
        override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) = Unit
        override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = SwingUtilities.invokeLater(anchor::dispose)
        override fun popupMenuCanceled(e: PopupMenuEvent) = Unit
    })
    // Swing moves the menu up or left when it would leave the screen.
    menu.show(anchor, 0, 0)
}

private fun Color.awt() = java.awt.Color(toArgb(), true)

private val MENU_FONT: Font = runCatching {
    Brand::class.java.getResourceAsStream("/font/geist.ttf")!!.use { Font.createFont(Font.TRUETYPE_FONT, it) }.deriveFont(14f)
}.getOrElse { Font(Font.SANS_SERIF, Font.PLAIN, 14) }
