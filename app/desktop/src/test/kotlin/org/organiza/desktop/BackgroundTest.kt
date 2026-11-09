package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `a socket path too long to bind does not stop Organiza`() {
        val deep = dir.resolve("x".repeat(120)).resolve("organiza.sock")
        Instance.listen(deep) {}.close()
    }

    @Test
    fun `the login service restarts after a crash and quotes what systemd would expand`() {
        val unit = Autostart.unitText(listOf("/opt/organiza/bin/organiza", "--name=50% \$HOME"))
        assertTrue("ExecStart=\"/opt/organiza/bin/organiza\" \"--name=50%% \$\$HOME\" \"--background\"" in unit)
        assertTrue("Restart=on-failure" in unit)
        assertTrue("WantedBy=graphical-session.target" in unit)
    }
}
