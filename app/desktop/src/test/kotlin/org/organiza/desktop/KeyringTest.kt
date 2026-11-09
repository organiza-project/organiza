package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue

/** The libsecret helper against a login keyring of its own, on a private bus; the real keyring is never touched. */
class KeyringTest {
    private val home: Path = Files.createTempDirectory("organiza-keyring")
    private var bus: Long? = null

    @AfterTest
    fun end() {
        // Only this test's daemons: the bus it started and the keyring whose home is the temp folder.
        bus?.let { ProcessHandle.of(it).ifPresent(ProcessHandle::destroy) }
        ProcessHandle.allProcesses().filter { it.info().command().orElse("").endsWith("gnome-keyring-daemon") }.forEach { process ->
            val environ = runCatching { Files.readString(Path.of("/proc/${process.pid()}/environ")) }.getOrDefault("")
            if ("HOME=$home\u0000" in environ) process.destroy()
        }
    }

    @Test
    fun `a secret is stored, read back and removed`() {
        assumeTrue(listOf("dbus-daemon", "gnome-keyring-daemon", "python3").all { output("which", it) != null }, "no keyring tools")
        val started = ProcessBuilder("dbus-daemon", "--session", "--fork", "--print-address=1", "--print-pid=1").start()
        val (address, pid) = started.inputStream.bufferedReader().readLines()
        bus = pid.trim().toLong()
        val env = mapOf(
            "HOME" to "$home",
            "XDG_DATA_HOME" to "$home/data",
            "XDG_CONFIG_HOME" to "$home/config",
            "XDG_RUNTIME_DIR" to "$home",
            "DBUS_SESSION_BUS_ADDRESS" to address.trim(),
        )
        val daemon = ProcessBuilder("gnome-keyring-daemon", "--unlock", "--components=secrets").apply { environment().putAll(env) }.start()
        daemon.outputStream.use { it.write("test".toByteArray()) }
        daemon.waitFor()

        val keyring = Keyring(env)
        val secret = ByteArray(32) { it.toByte() }
        keyring.put(DesktopStorage.KEY, secret)
        assertContentEquals(secret, keyring.get(DesktopStorage.KEY))
        keyring.remove(DesktopStorage.KEY)
        assertNull(keyring.get(DesktopStorage.KEY))
    }
}
