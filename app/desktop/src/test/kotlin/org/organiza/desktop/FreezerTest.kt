package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FreezerTest {
    private val record: Path = Files.createTempDirectory("organiza").resolve("frozen")
    private val child = ProcessBuilder("sleep", "60").start()
    private val pid = child.pid()

    @AfterTest
    fun end() {
        ProcessBuilder("kill", "-CONT", pid.toString()).start().waitFor()
        child.destroy()
    }

    // The third field of /proc/<pid>/stat: T when stopped, S while sleeping.
    private fun state(): Char = Files.readString(Path.of("/proc/$pid/stat")).substringAfterLast(')').trim().first()

    @Test
    fun `a frozen app is stopped until thawed, and the record goes with it`() {
        val freezer = Freezer(record)
        freezer.freeze(pid)
        assertEquals('T', state())
        assertEquals(listOf(pid.toString()), Files.readAllLines(record))
        freezer.thawAll()
        assertEquals('S', state())
        assertFalse(record.exists())
    }

    @Test
    fun `apps left frozen by a crash are thawed at the next start`() {
        ProcessBuilder("kill", "-STOP", pid.toString()).start().waitFor()
        record.writeText("$pid\n")
        Freezer(record).recover()
        assertEquals('S', state())
        assertFalse(record.exists())
    }

    @Test
    fun `Organiza itself and the desktop shell are never frozen`() {
        val freezer = Freezer(record)
        freezer.freeze(ProcessHandle.current().pid())
        freezer.freeze(1)
        assertFalse(record.exists())
    }
}
