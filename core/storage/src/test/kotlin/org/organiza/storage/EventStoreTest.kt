package org.organiza.storage

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.organiza.testing.JdbcDatabase

class EventStoreTest {
    private val events = EventStore(JdbcDatabase().also { Migrations.migrate(it) })
    private fun at(day: String, time: String) = Instant.parse("${day}T$time:00Z")

    @Test
    fun `events are kept by start, found by the days they touch, and deleted`() {
        events.add(CalendarEvent("b", "Dentist", at("2026-12-02", "14:00"), 45, remindBefore = 10))
        events.add(CalendarEvent("a", "Exam", at("2026-12-01", "09:00"), 120, remindBefore = null))
        events.add(CalendarEvent("late", "Night shift", at("2026-11-30", "22:00"), 600, remindBefore = null))
        // The night shift runs into the 1st, so the 1st shows it too.
        assertEquals(listOf("late", "a"), events.between(at("2026-12-01", "00:00"), at("2026-12-02", "00:00")).map { it.id })
        assertEquals(at("2026-12-02", "14:45"), events.between(at("2026-12-02", "00:00"), at("2026-12-03", "00:00")).single().end)
        events.delete("a")
        assertEquals(listOf("late", "b"), events.between(at("2026-11-01", "00:00"), at("2027-01-01", "00:00")).map { it.id })
    }

    @Test
    fun `only reminders still ahead are scheduled, at their lead time`() {
        events.add(CalendarEvent("b", "Dentist", at("2026-12-02", "14:00"), 45, remindBefore = 10))
        events.add(CalendarEvent("a", "Exam", at("2026-12-01", "09:00"), 120, remindBefore = null))
        events.add(CalendarEvent("past", "Lunch", at("2026-11-01", "12:00"), 60, remindBefore = 0))
        assertEquals(listOf("b"), events.reminders(at("2026-11-28", "12:00")).map { it.id })
    }

    @Test
    fun `an empty title or a day-long overrun is refused`() {
        assertFails { events.add(CalendarEvent("x", "", at("2026-12-01", "09:00"), 30, null)) }
        assertFails { events.add(CalendarEvent("y", "Trip", at("2026-12-01", "09:00"), 1441, null)) }
    }
}
