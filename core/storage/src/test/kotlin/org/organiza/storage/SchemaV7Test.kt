package org.organiza.storage

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.organiza.testing.JdbcDatabase

class SchemaV7Test {
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val rpg = RpgStore(db)
    private val at = Instant.parse("2026-11-04T10:00:00Z")
    private val today = LocalDate.parse("2026-11-04")

    @Test
    fun `tasks keep their kind and order, and a to-do can be completed`() {
        rpg.addTask("t1", RpgKind.DAILY, "Drink water", at)
        rpg.addTask("t2", RpgKind.HABIT, "Stretch", at.plusSeconds(1))
        rpg.addTask("t3", RpgKind.TODO, "Pay rent", at.plusSeconds(2))
        rpg.complete("t3", at.plusSeconds(60))
        assertEquals(listOf("t1", "t2", "t3"), rpg.tasks().map { it.id })
        assertEquals(RpgTask("t3", RpgKind.TODO, "Pay rent", at.plusSeconds(2), at.plusSeconds(60)), rpg.tasks().last())
    }

    @Test
    fun `a daily is checked once per day and can be unchecked`() {
        rpg.addTask("t1", RpgKind.DAILY, "Drink water", at)
        rpg.log(RpgReason.DAILY, "t1", today, xp = 10, hp = 0, at)
        assertFailsWith<Exception> { rpg.log(RpgReason.DAILY, "t1", today, xp = 10, hp = 0, at) }
        assertEquals(mapOf("t1" to setOf(today)), rpg.dailyChecks())
        rpg.uncheck("t1", today)
        assertEquals(emptyMap(), rpg.dailyChecks())
    }

    @Test
    fun `events sum in order, and outlive a deleted task`() {
        rpg.addTask("t1", RpgKind.HABIT, "Stretch", at)
        rpg.log(RpgReason.HABIT_UP, "t1", today, 5, 0, at)
        rpg.log(RpgReason.HABIT_DOWN, "t1", today, 0, -5, at)
        rpg.delete("t1")
        assertEquals(listOf(5 to 0, 0 to -5), rpg.entries())
        assertEquals(emptyList(), rpg.tasks())
    }

    @Test
    fun `the last settled day is remembered`() {
        val settings = SettingsStore(db)
        assertNull(settings.text(Setting.RPG_SETTLED))
        settings.setText(Setting.RPG_SETTLED, "2026-11-03")
        assertEquals("2026-11-03", settings.text(Setting.RPG_SETTLED))
    }

    @Test
    fun `titles and reasons are bounded by the schema`() {
        assertFailsWith<Exception> { rpg.addTask("x", RpgKind.TODO, "", at) }
        assertFailsWith<Exception> { rpg.addTask("y", RpgKind.TODO, "a".repeat(81), at) }
    }
}
