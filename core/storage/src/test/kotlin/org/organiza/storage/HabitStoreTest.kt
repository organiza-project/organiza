package org.organiza.storage

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.testing.JdbcDatabase

class HabitStoreTest {
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val habits = HabitStore(db)
    private val at = Instant.parse("2026-11-01T10:00:00Z")
    private val day = LocalDate.of(2026, 11, 1)

    @Test
    fun `habits are added and listed by name`() {
        habits.add("b", "Stretch", at)
        habits.add("a", "Read", at)
        assertEquals(listOf(StoredHabit("a", "Read"), StoredHabit("b", "Stretch")), habits.active())
    }

    @Test
    fun `archiving hides a habit but keeps its history`() {
        habits.add("a", "Read", at)
        habits.mark("a", day, true)
        habits.archive("a", at)
        assertEquals(emptyList(), habits.active())
        assertEquals(mapOf(day to true), habits.results("a", day, day.plusDays(1)))
    }

    @Test
    fun `marking twice keeps the latest result`() {
        habits.add("a", "Read", at)
        habits.mark("a", day, true)
        habits.mark("a", day, false)
        assertEquals(mapOf(day to false), habits.results("a", day, day.plusDays(1)))
    }

    @Test
    fun `results cover only the requested range`() {
        habits.add("a", "Read", at)
        habits.mark("a", day.minusDays(1), true)
        habits.mark("a", day, true)
        habits.mark("a", day.plusDays(1), true)
        assertEquals(setOf(day), habits.results("a", day, day.plusDays(1)).keys)
    }
}
