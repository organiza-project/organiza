package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class BackgroundTest {
    private val dir: Path = Files.createTempDirectory("rt")

    @Test
    fun `a second start hands its request to the first and only known requests pass`() {
        val socket = dir.resolve("organiza.sock")
        assertFalse(Instance.handOff(socket, "show"))
        val got = java.util.concurrent.LinkedBlockingQueue<String>()
        Instance.listen(socket) { got.add(it) }.use {
            assertTrue(Instance.handOff(socket, "rm -rf"))
            assertTrue(Instance.handOff(socket, "copilot"))
            assertEquals("copilot", got.poll(5, java.util.concurrent.TimeUnit.SECONDS))
        }
    }

    @Test
    fun `only one start at a time holds the instance lock`() {
        val file = dir.resolve("organiza.lock")
        val first = assertNotNull(Instance.lock(file))
        assertNull(Instance.lock(file))
        first.close()
        assertNotNull(Instance.lock(file)).close()
    }

    @Test
    fun `a socket path too long to bind does not stop Organiza`() {
        val deep = dir.resolve("x".repeat(120)).resolve("organiza.sock")
        Instance.listen(deep) {}.close()
    }

    @Test
    fun `starting at login links the service into the graphical session, and again does no harm`() {
        val autostart = Autostart(dir.resolve("config"))
        assertFalse(autostart.enabled())
        autostart.enable(listOf("/opt/organiza/bin/Organiza"))
        autostart.enable(listOf("/opt/organiza/bin/Organiza"))
        assertTrue(autostart.enabled())
        val wanted = dir.resolve("config/systemd/user/graphical-session.target.wants/organiza.service")
        assertTrue("ExecStart=\"/opt/organiza/bin/Organiza\" \"--background\"" in Files.readString(wanted))
    }

    @Test
    fun `the copilot shortcut is added beside the person's own shortcuts, once`() {
        // Only ever against the keyfile backend the test task sets up, never the real dconf.
        assumeTrue(System.getenv("GSETTINGS_BACKEND") == "keyfile", "not run from the Gradle test task")
        assumeTrue(output("gsettings", "list-keys", MEDIA_KEYS) != null, "no GNOME media-keys schema")
        output("gsettings", "set", MEDIA_KEYS, "custom-keybindings", "['/theirs/']")
        Shortcut.install(listOf("/opt/organiza/bin/Organiza", "--copilot"))
        Shortcut.install(listOf("/opt/organiza/bin/Organiza", "--copilot"))
        val path = "/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/organiza/"
        assertEquals("['/theirs/', '$path']", output("gsettings", "get", MEDIA_KEYS, "custom-keybindings")?.trim())
        assertTrue(Shortcut.installed())
        val binding = "$MEDIA_KEYS.custom-keybinding:$path"
        assertEquals("'/opt/organiza/bin/Organiza --copilot'", output("gsettings", "get", binding, "command")?.trim())
        assertEquals("'${Shortcut.KEYS}'", output("gsettings", "get", binding, "binding")?.trim())
    }

    private companion object {
        const val MEDIA_KEYS = "org.gnome.settings-daemon.plugins.media-keys"
    }

    @Test
    fun `the login service restarts after a crash and quotes what systemd would expand`() {
        val unit = Autostart.unitText(listOf("/opt/organiza/bin/organiza", "--name=50% \$HOME"))
        assertTrue("ExecStart=\"/opt/organiza/bin/organiza\" \"--name=50%% \$\$HOME\" \"--background\"" in unit)
        assertTrue("Restart=on-failure" in unit)
        assertTrue("WantedBy=graphical-session.target" in unit)
    }
}
