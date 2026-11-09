package org.organiza.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/** Calls Windows itself (ADR-0027). The Windows CI job runs it; everywhere else it is skipped. */
@EnabledOnOs(OS.WINDOWS)
class WindowsLiveTest {
    @Test
    fun `dpapi seals a secret that only this login opens again`() {
        val dir = Files.createTempDirectory("secrets")
        val keyring = DpapiKeyring(dir)
        keyring.put("database-key", byteArrayOf(1, 2, 3, 4))
        assertFalse(dir.resolve("database-key.dpapi").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), keyring.get("database-key"))
    }

    @Test
    fun `the start menu lists programs`() {
        val apps = WindowsApps.scan(WindowsApps.startMenus())
        assertTrue(apps.isNotEmpty())
        assertTrue(apps.all { it.icon.orEmpty().endsWith(".exe", ignoreCase = true) })
    }

    @Test
    fun `a frozen program stops running until it is thawed`() {
        // ping prints a line a second, so its output shows whether it runs.
        val ping = ProcessBuilder("ping", "-n", "60", "127.0.0.1").start()
        val lines = AtomicInteger()
        thread(isDaemon = true) { ping.inputStream.bufferedReader().forEachLine { lines.incrementAndGet() } }
        val freezer = Freezer(Files.createTempDirectory("organiza").resolve("frozen"))
        try {
            Thread.sleep(1_500)
            freezer.freeze(ping.pid())
            Thread.sleep(500)
            val frozenAt = lines.get()
            Thread.sleep(3_000)
            assertEquals(frozenAt, lines.get(), "printed while frozen")
            freezer.thawAll()
            Thread.sleep(3_000)
            assertTrue(lines.get() > frozenAt, "still frozen after thawing")
        } finally {
            freezer.thawAll()
            ping.destroy()
        }
    }

    @Test
    fun `starting at sign-in is written to this user's run key`() {
        try {
            RunKeyAutostart.enable(listOf("""C:\Users\a\AppData\Local\Organiza\Organiza.exe"""))
            assertTrue(RunKeyAutostart.enabled())
        } finally {
            // Only what the test wrote is removed, so a failure above is the one reported.
            val run = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
            if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, run) && Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, run, "Organiza")) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, run, "Organiza")
            }
        }
    }

    @Test
    fun `the foreground window is read without failing`() {
        WindowsFocus(WindowsApps.scan(WindowsApps.startMenus())).start {}.close()
    }
}
