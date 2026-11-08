package org.organiza.desktop

import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.storage.FocusLog
import org.organiza.storage.Migrations
import org.organiza.testing.FakeSecretStore

class LoggedUsageTest {
    private val db = DesktopStorage.open(Files.createTempDirectory("organiza").resolve("u.db"), FakeSecretStore()).also { Migrations.migrate(it) }
    private val log = FocusLog(db)
    private val files = LinuxApps.packageName("org.gnome.Nautilus")!!
    private val chrome = LinuxApps.packageName("google-chrome")!!
    private fun at(time: String) = Instant.parse("2026-11-09T$time:00Z")

    @Test
    fun `time in front is counted per app and nothing in front counts for none`() {
        log.record(at("08:50"), chrome)
        log.record(at("09:10"), files)
        log.record(at("09:40"), null)
        log.record(at("10:00"), chrome)
        val usage = LoggedUsage(log)
        assertEquals(
            mapOf(chrome to Duration.ofMinutes(10 + 15), files to Duration.ofMinutes(30)),
            usage.perApp(at("09:00"), at("10:15")),
        )
        assertEquals(Duration.ofMinutes(30), usage.foregroundTime(files, at("09:00"), at("10:15")))
    }
}
