package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxAppsTest {
    private val root: Path = Files.createTempDirectory("xdg")

    @Test
    fun `a desktop ID that fits a package name is kept and any other is mapped once`() {
        assertEquals("org.gnome.Nautilus", LinuxApps.packageName("org.gnome.Nautilus")?.value)
        assertEquals("linux.google_chrome", LinuxApps.packageName("google-chrome")?.value)
        assertEquals("linux.firefox_firefox", LinuxApps.packageName("firefox_firefox")?.value)
        assertEquals("linux.Among_Us", LinuxApps.packageName("Among Us")?.value)
        assertEquals("linux.display_im6.q16", LinuxApps.packageName("display-im6.q16")?.value)
        assertEquals("linux.x7zip.app", LinuxApps.packageName("7zip.app")?.value)
        assertNull(LinuxApps.packageName(""))
    }

    @Test
    fun `an entry gives its name, icon and category, and a hidden one is not an app`() {
        val app = LinuxApps.parse(
            "org.gnome.Nautilus",
            """
            [Desktop Entry]
            Name=Files
            Name[de]=Dateien
            Icon=org.gnome.Nautilus
            Type=Application
            Categories=GNOME;GTK;Utility;Core;FileManager;
            StartupWMClass=nautilus

            [Desktop Action new-window]
            Name=New Window
            """.trimIndent(),
        )
        assertEquals(LinuxApp("org.gnome.Nautilus", "Files", "org.gnome.Nautilus", "Utilities", "nautilus"), app)
        assertEquals("Games", LinuxApps.parse("a.b", "[Desktop Entry]\nType=Application\nName=A\nCategories=Game;ActionGame;")?.category)
        assertEquals("Other", LinuxApps.parse("a.b", "[Desktop Entry]\nType=Application\nName=A")?.category)
        assertNull(LinuxApps.parse("a.b", "[Desktop Entry]\nType=Application\nName=A\nNoDisplay=true"))
        assertNull(LinuxApps.parse("a.b", "[Desktop Entry]\nType=Link\nName=A"))
        assertNull(LinuxApps.parse("a.b", "[Desktop Entry]\nType=Application"))
    }

    @Test
    fun `the first data directory wins and subdirectories join the ID with a dash`() {
        entry("home/applications/code.desktop", "Code (mine)")
        entry("usr/applications/code.desktop", "Code")
        entry("usr/applications/kde/kate.desktop", "Kate")
        val apps = LinuxApps.scan(listOf(root.resolve("home"), root.resolve("usr"), root.resolve("missing")))
        assertEquals(listOf("code" to "Code (mine)", "kde-kate" to "Kate"), apps.map { it.id to it.name }.sortedBy { it.first })
    }

    @Test
    fun `an icon name is found in hicolor or pixmaps and a path is used as it is`() {
        val svg = file("usr/icons/hicolor/scalable/apps/org.gnome.Nautilus.svg")
        val png = file("usr/icons/hicolor/48x48/apps/code.png")
        file("usr/icons/hicolor/16x16/apps/code.png")
        val pixmap = file("usr/pixmaps/old.png")
        val dirs = listOf(root.resolve("home"), root.resolve("usr"))
        assertEquals(svg, LinuxApps.iconFile("org.gnome.Nautilus", dirs))
        assertEquals(png, LinuxApps.iconFile("code", dirs))
        assertEquals(pixmap, LinuxApps.iconFile("old", dirs))
        assertEquals(png, LinuxApps.iconFile(png.toString(), dirs))
        assertNull(LinuxApps.iconFile("absent", dirs))
    }

    @Test
    fun `data directories follow the XDG defaults`() {
        assertEquals(
            listOf(Path.of("/h/.local/share"), Path.of("/usr/local/share"), Path.of("/usr/share")),
            LinuxApps.dataDirs(mapOf("HOME" to "/h")),
        )
        assertEquals(
            listOf(Path.of("/d"), Path.of("/a"), Path.of("/b")),
            LinuxApps.dataDirs(mapOf("HOME" to "/h", "XDG_DATA_HOME" to "/d", "XDG_DATA_DIRS" to "/a:/b:")),
        )
    }

    private fun entry(relative: String, name: String) =
        file(relative).writeText("[Desktop Entry]\nType=Application\nName=$name\n")

    private fun file(relative: String): Path =
        root.resolve(relative).also { it.parent.createDirectories(); Files.createFile(it) }
}
