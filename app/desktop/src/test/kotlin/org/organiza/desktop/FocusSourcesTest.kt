package org.organiza.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FocusSourcesTest {
    @Test
    fun `the GNOME extension reports a desktop file, or nothing in front`() {
        assertEquals("org.gnome.Nautilus", GnomeFocus.appId("/org/organiza/Focus: org.organiza.Focus.Changed ('org.gnome.Nautilus.desktop',)"))
        assertEquals("firefox_firefox", GnomeFocus.appId("('firefox_firefox.desktop',)"))
        assertEquals("", GnomeFocus.appId("/org/organiza/Focus: org.organiza.Focus.Changed ('',)"))
        // A window GNOME cannot match to an app has no entry, so it counts as nothing in front.
        assertEquals("", GnomeFocus.appId("('window:42',)"))
        assertNull(GnomeFocus.appId("Monitoring signals on object /org/organiza/Focus owned by org.gnome.Shell"))
    }

    @Test
    fun `xprop gives the active window and the app it belongs to`() {
        assertEquals("0x3a00007", X11Focus.window("_NET_ACTIVE_WINDOW(WINDOW): window id # 0x3a00007"))
        assertNull(X11Focus.window("_NET_ACTIVE_WINDOW(WINDOW): window id # 0x0"))
        assertNull(X11Focus.window("_NET_ACTIVE_WINDOW:  not found."))
        val apps = listOf(
            LinuxApp("org.gnome.Nautilus", "Files", null, "Utilities", "nautilus"),
            LinuxApp("google-chrome", "Chrome", null, "Internet", null),
            LinuxApp("code_code", "Code", null, "Development", "Code"),
        )
        assertEquals("org.gnome.Nautilus", X11Focus.appId("_GTK_APPLICATION_ID(UTF8_STRING) = \"org.gnome.Nautilus\"\nWM_CLASS(STRING) = \"nautilus\", \"Nautilus\"", apps))
        assertEquals("google-chrome", X11Focus.appId("_GTK_APPLICATION_ID:  not found.\nWM_CLASS(STRING) = \"google-chrome\", \"Google-chrome\"", apps))
        assertEquals("code_code", X11Focus.appId("WM_CLASS(STRING) = \"code\", \"Code\"", apps))
        assertNull(X11Focus.appId("WM_CLASS(STRING) = \"xterm\", \"XTerm\"", apps))
    }
}
