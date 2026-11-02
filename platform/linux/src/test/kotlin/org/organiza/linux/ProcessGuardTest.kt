package org.organiza.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.domain.Parsed
import org.organiza.domain.ProcessName

class ProcessGuardTest {
    private fun names(vararg raw: String) = raw.map { (ProcessName.parse(it) as Parsed.Valid).value }.toSet()

    private fun info(pid: Int, uid: Int, vararg names: String, flatpak: String? = null) =
        ProcessInfo(pid, uid, names.toSet(), flatpak)

    private val guard = ProcessGuard(selfPid = 300)

    @Test
    fun `matches by any name including the truncated comm`() {
        val processes = listOf(
            info(10, 1000, "steam"),
            info(11, 1000, "steamwebhelper"),
            info(12, 1000, "telegram-deskto"),
        )
        assertEquals(listOf(10, 12), guard.targets(processes, names("steam", "telegram-desktop")))
    }

    @Test
    fun `matches flatpak ids`() {
        val processes = listOf(info(20, 1000, "Discord", flatpak = "com.discordapp.Discord"))
        assertEquals(listOf(20), guard.targets(processes, names("com.discordapp.Discord")))
    }

    @Test
    fun `never touches root system processes or itself`() {
        val processes = listOf(
            info(1, 0, "systemd"),
            info(30, 0, "steam"),
            info(31, 999, "steam"),
            info(300, 1000, "steam"),
        )
        assertEquals(emptyList(), guard.targets(processes, names("steam", "systemd")))
    }

    @Test
    fun `never touches shells sessions or the desktop even when listed`() {
        val protected = listOf("bash", "zsh", "sh", "fish", "sudo", "sshd", "login", "gnome-shell", "Xwayland", "kwin_wayland", "plasmashell", "gdm-wayland-session", "systemd", "java")
        val processes = protected.mapIndexed { index, name -> info(100 + index, 1000, name) }
        assertEquals(emptyList(), guard.targets(processes, names(*protected.toTypedArray())))
    }

    @Test
    fun `nothing listed means nothing to end`() {
        assertEquals(emptyList(), guard.targets(listOf(info(10, 1000, "steam")), emptySet()))
    }
}
