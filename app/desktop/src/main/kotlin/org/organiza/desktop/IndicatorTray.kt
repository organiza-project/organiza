package org.organiza.desktop

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/**
 * The Linux tray through AppIndicator, so GNOME Shell draws the icon and its menu in its own style, as it
 * does for every other app. A small Python helper owns the indicator, the same route as the keyring
 * (ADR-0024), and prints the item chosen; closing its input ends it. Without the library, [available] is
 * false and the AWT tray is used instead.
 */
object IndicatorTray {
    /** Whether python3 with Ayatana AppIndicator is installed. Asked once: it starts a process. */
    val available: Boolean by lazy { !WINDOWS && output("python3", "-c", "$GI\nprint('ok')") == "ok\n" }

    /** Starts the indicator, its icon drawn into [dir]; each chosen item's id goes to [onChoice] on the UI thread. */
    fun start(dir: Path, onChoice: (String) -> Unit, onMain: (() -> Unit) -> Unit): AutoCloseable? {
        val file = dir.resolve("organiza-tray.png")
        val process = try {
            Files.createDirectories(dir)
            ImageIO.write(Brand.render(Brand.symbolicMark, ICON_PX), "png", file.toFile())
            ProcessBuilder("python3", "-c", HELPER, file.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (_: IOException) {
            return null
        }
        thread(isDaemon = true, name = "organiza-indicator") {
            process.inputStream.bufferedReader().forEachLine { line -> line.trim().takeIf { it in ITEMS }?.let { onMain { onChoice(it) } } }
        }
        return AutoCloseable {
            process.outputStream.close()
            process.destroy()
        }
    }

    private val ITEMS = setOf("open", "copilot", "quit")
    private const val ICON_PX = 64
    private const val GI = "import gi\ngi.require_version('Gtk', '3.0')\ngi.require_version('AyatanaAppIndicator3', '0.1')\n" +
        "from gi.repository import Gtk, GLib, AyatanaAppIndicator3 as AI"

    private val HELPER = GI + """

import sys
indicator = AI.Indicator.new('organiza', sys.argv[1], AI.IndicatorCategory.APPLICATION_STATUS)
indicator.set_status(AI.IndicatorStatus.ACTIVE)
indicator.set_title('Organiza')
menu = Gtk.Menu()
def item(label, choice):
    entry = Gtk.MenuItem.new_with_label(label)
    entry.connect('activate', lambda _: (print(choice), sys.stdout.flush()))
    menu.append(entry)
    return entry
indicator.set_secondary_activate_target(item('Open Organiza', 'open'))
item('Copilot', 'copilot')
menu.append(Gtk.SeparatorMenuItem())
item('Quit', 'quit')
menu.show_all()
indicator.set_menu(menu)
# When Organiza closes its end, the helper ends too.
GLib.io_add_watch(GLib.IOChannel.unix_new(sys.stdin.fileno()), GLib.PRIORITY_DEFAULT, GLib.IOCondition.HUP, lambda *_: Gtk.main_quit())
Gtk.main()
"""
}
