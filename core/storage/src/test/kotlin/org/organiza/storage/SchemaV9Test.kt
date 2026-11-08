package org.organiza.storage

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.testing.JdbcDatabase

class SchemaV9Test {
    private val log = FocusLog(JdbcDatabase().also { Migrations.migrate(it) })
    private val files = app("org.gnome.Nautilus")
    private val chrome = app("linux.google_chrome")
    private fun at(time: String) = Instant.parse("2026-11-09T$time:00Z")

    @Test
    fun `a change is stored only when the app in front changes`() {
        log.record(at("09:00"), files)
        log.record(at("09:05"), files)
        log.record(at("09:10"), chrome)
        log.record(at("09:20"), null)
        log.record(at("09:30"), null)
        assertEquals(
            listOf(FocusChange(at("09:00"), files), FocusChange(at("09:10"), chrome), FocusChange(at("09:20"), null)),
            log.changes(at("08:00"), at("10:00")),
        )
        assertEquals(FocusChange(at("09:20"), null), log.last())
    }

    @Test
    fun `the window starts with the app already in front`() {
        log.record(at("09:00"), files)
        log.record(at("09:10"), chrome)
        log.record(at("09:40"), files)
        assertEquals(
            listOf(FocusChange(at("09:10"), chrome), FocusChange(at("09:40"), files)),
            log.changes(at("09:30"), at("10:00")),
        )
    }

    private fun app(raw: String) = (PackageName.parse(raw) as Parsed.Valid).value
}
