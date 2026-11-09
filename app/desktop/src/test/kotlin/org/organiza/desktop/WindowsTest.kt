package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The Windows logic that needs no Windows; WindowsLiveTest calls Windows itself in CI. */
class WindowsTest {
    private val menu: Path = Files.createTempDirectory("start-menu")

    private fun shortcut(name: String) = menu.resolve(name).also { it.parent.createDirectories(); it.writeText("lnk") }

    @Test
    fun `apps are the start menu shortcuts to programs, named after the shortcut`() {
        shortcut("Discord Inc/Discord.lnk")
        shortcut("LibreWolf.lnk")
        shortcut("LibreWolf/Uninstall LibreWolf.lnk")
        shortcut("Readme.lnk")
        shortcut("Discord.lnk")
        val targets = mapOf(
            "Discord.lnk" to """C:\Users\a\AppData\Local\Discord\app-1.0\Discord.exe""",
            "LibreWolf.lnk" to """C:\Program Files\LibreWolf\librewolf.exe""",
            "Uninstall LibreWolf.lnk" to """C:\Program Files\LibreWolf\uninstall\helper.exe""",
            "Readme.lnk" to """C:\Program Files\LibreWolf\readme.txt""",
        )
        val apps = WindowsApps.scan(listOf(menu, menu.resolve("missing"))) { targets[it.fileName.toString()] }
        // The same program from both start menus is one app.
        assertEquals(listOf("discord", "librewolf"), apps.map { it.id })
        assertEquals("LibreWolf", apps.last().name)
        assertEquals("""C:\Program Files\LibreWolf\librewolf.exe""", apps.last().icon)
    }

    @Test
    fun `a window's program file names the app in front`() {
        val apps = listOf(LinuxApp("librewolf", "LibreWolf", null, "Other", null))
        assertEquals(Focus("librewolf", 42), WindowsFocus.focus("""C:\Program Files\LibreWolf\LibreWolf.exe""", 42, apps))
        assertNull(WindowsFocus.focus("""C:\Windows\explorer.exe""", 7, apps))
    }

    @Test
    fun `squirrel apps are named after their folder, as their running program is`() {
        assertEquals("discord", WindowsApps.id("""C:\Users\a\AppData\Local\Discord\Update.exe"""))
        assertEquals("slack", WindowsApps.id("""C:\Users\a\AppData\Local\slack\Update.exe"""))
        assertEquals("discord", WindowsApps.id("""C:\Users\a\AppData\Local\Discord\app-1.0.9\Discord.exe"""))
    }

    @Test
    fun `windows programs live under their own prefix in core`() {
        assertEquals("windows.discord", LinuxApps.packageName("discord", prefix = "windows")?.value)
    }

    @Test
    fun `a frozen process is suspended once, since windows counts suspensions`() {
        val signals = mutableListOf<String>()
        val freezer = Freezer(Files.createTempDirectory("organiza").resolve("frozen")) { name, pids -> signals += "$name $pids" }
        val me = ProcessBuilder("sleep", "30").start()
        try {
            freezer.freeze(me.pid())
            freezer.freeze(me.pid())
            freezer.thawAll()
        } finally {
            me.destroy()
        }
        assertEquals(listOf("STOP [${me.pid()}]", "CONT [${me.pid()}]"), signals)
    }

    @Test
    fun `a session process is never frozen, whichever separator its path uses`() {
        assertFalse(Freezer.freezable("""C:\Windows\explorer.exe"""))
        assertFalse(Freezer.freezable("/usr/bin/gnome-shell"))
    }

    @Test
    fun `secrets are sealed on disk and opened again`() {
        val dir = Files.createTempDirectory("secrets")
        val keyring = DpapiKeyring(dir, seal = { it.reversedArray() }, open = { it.reversedArray() })
        keyring.put("database-key", byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(3, 2, 1), dir.resolve("database-key.dpapi").readBytes())
        assertContentEquals(byteArrayOf(1, 2, 3), keyring.get("database-key"))
        keyring.remove("database-key")
        assertNull(keyring.get("database-key"))
    }
}
