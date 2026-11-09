package org.organiza.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FocusSourcesTest {
    private val apps = listOf(
        LinuxApp("org.gnome.Nautilus", "Files", null, "Utilities", "nautilus"),
        LinuxApp("google-chrome", "Chrome", null, "Internet", null),
        LinuxApp("code_code", "Code", null, "Development", "Code"),
        LinuxApp("firefox", "Firefox", null, "Internet", null),
    )

    @Test
    fun `the session is checked in order and the first supported source is used`() {
        assertIs<GnomeFocus>(FocusSource.forSession(mapOf("XDG_CURRENT_DESKTOP" to "ubuntu:GNOME", "XDG_SESSION_TYPE" to "wayland"), apps))
        assertIs<GnomeFocus>(FocusSource.forSession(mapOf("XDG_CURRENT_DESKTOP" to "GNOME", "XDG_SESSION_TYPE" to "x11"), apps))
        assertIs<HyprlandFocus>(FocusSource.forSession(mapOf("HYPRLAND_INSTANCE_SIGNATURE" to "abc", "XDG_SESSION_TYPE" to "wayland"), apps))
        assertIs<SwayFocus>(FocusSource.forSession(mapOf("SWAYSOCK" to "/run/sway.sock", "XDG_SESSION_TYPE" to "wayland"), apps))
        assertIs<X11Focus>(FocusSource.forSession(mapOf("XDG_CURRENT_DESKTOP" to "XFCE", "XDG_SESSION_TYPE" to "x11"), apps))
        // KDE on Wayland has no source yet, so tracking says so instead of guessing.
        assertNull(FocusSource.forSession(mapOf("XDG_CURRENT_DESKTOP" to "KDE", "XDG_SESSION_TYPE" to "wayland"), apps).start { })
    }

    @Test
    fun `the GNOME extension reports a desktop file and its window's process`() {
        assertEquals(Focus("org.gnome.Nautilus", 4321), GnomeFocus.focus("/org/organiza/Focus: org.organiza.Focus.Changed ('org.gnome.Nautilus.desktop', 4321)"))
        assertEquals(Focus("firefox_firefox", null), GnomeFocus.focus("('firefox_firefox.desktop',)"))
        assertEquals(Focus("", null), GnomeFocus.focus("/org/organiza/Focus: org.organiza.Focus.Changed ('', 0)"))
        // A window GNOME cannot match to an app has no entry, so it counts as nothing in front.
        assertEquals(Focus("", null), GnomeFocus.focus("('window:42', 77)"))
        assertNull(GnomeFocus.focus("Monitoring signals on object /org/organiza/Focus owned by org.gnome.Shell"))
    }

    @Test
    fun `xprop gives the active window, its app and its process`() {
        assertEquals("0x3a00007", X11Focus.window("_NET_ACTIVE_WINDOW(WINDOW): window id # 0x3a00007"))
        assertNull(X11Focus.window("_NET_ACTIVE_WINDOW(WINDOW): window id # 0x0"))
        assertNull(X11Focus.window("_NET_ACTIVE_WINDOW:  not found."))
        assertEquals(
            Focus("org.gnome.Nautilus", 812),
            X11Focus.focus("_GTK_APPLICATION_ID(UTF8_STRING) = \"org.gnome.Nautilus\"\nWM_CLASS(STRING) = \"nautilus\", \"Nautilus\"\n_NET_WM_PID(CARDINAL) = 812", apps),
        )
        assertEquals(Focus("google-chrome", null), X11Focus.focus("_GTK_APPLICATION_ID:  not found.\nWM_CLASS(STRING) = \"google-chrome\", \"Google-chrome\"", apps))
        assertEquals(Focus("code_code", null), X11Focus.focus("WM_CLASS(STRING) = \"code\", \"Code\"", apps))
        assertNull(X11Focus.focus("WM_CLASS(STRING) = \"xterm\", \"XTerm\"", apps))
    }

    @Test
    fun `Hyprland and Sway name the window's app and process`() {
        assertEquals(Focus("firefox", 99), HyprlandFocus.focus("""{"address": "0x55", "class": "firefox", "title": "ignored", "pid": 99}""", apps))
        assertNull(HyprlandFocus.focus("""{"class": "unknown-app", "pid": 3}""", apps))
        val native = """{"change": "focus", "container": {"app_id": "org.gnome.Nautilus", "pid": 501, "name": "ignored"}}"""
        assertEquals(Focus("org.gnome.Nautilus", 501), SwayFocus.focus(native, apps))
        val x11 = """{"change": "focus", "container": {"app_id": null, "pid": 7, "window_properties": {"class": "Code", "title": "ignored"}}}"""
        assertEquals(Focus("code_code", 7), SwayFocus.focus(x11, apps))
    }

    @Test
    fun `an older GNOME extension is replaced once, and a missing one is left for the person to allow`() {
        val dir = java.nio.file.Files.createTempDirectory("ext").resolve(GnomeFocus.UUID)
        assertNull(GnomeFocus.updateIfOld(dir))
        assertEquals(false, java.nio.file.Files.exists(dir))
        java.nio.file.Files.createDirectories(dir)
        java.nio.file.Files.writeString(dir.resolve("extension.js"), "// the version without process IDs")
        kotlin.test.assertNotNull(GnomeFocus.updateIfOld(dir))
        assertNull(GnomeFocus.updateIfOld(dir))
    }
}
