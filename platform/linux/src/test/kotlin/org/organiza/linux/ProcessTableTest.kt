package org.organiza.linux

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessTableTest {
    private val proc = ProcFixture()

    @Test
    fun `reads pid uid and every name a process goes by`() {
        proc.add(4242, 1000, "firefox-bin", exe = "/usr/lib/firefox/firefox-bin", argv0 = "/usr/bin/firefox")
        val info = ProcessTable(proc.root).list().single()
        assertEquals(4242, info.pid)
        assertEquals(1000, info.uid)
        assertEquals(setOf("firefox-bin", "firefox"), info.names)
    }

    @Test
    fun `finds the flatpak app id in the cgroup`() {
        proc.add(7, 1000, "Discord", cgroup = "0::/user.slice/user-1000.slice/user@1000.service/app.slice/app-flatpak-com.discordapp.Discord-1234.scope")
        assertEquals("com.discordapp.Discord", ProcessTable(proc.root).list().single().flatpakId)
    }

    @Test
    fun `ignores entries that are not processes or vanish mid scan`() {
        Files.createDirectories(proc.root.resolve("self"))
        Files.createDirectories(proc.root.resolve("99"))
        Files.writeString(proc.root.resolve("uptime"), "1 2")
        proc.add(5, 1000, "steam")
        assertEquals(listOf(5), ProcessTable(proc.root).list().map { it.pid })
    }

    @Test
    fun `kernel threads without a command line are kept with their comm only`() {
        proc.add(2, 0, "kthreadd", argv0 = "")
        assertEquals(setOf("kthreadd"), ProcessTable(proc.root).list().single().names)
    }
}
