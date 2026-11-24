package org.organiza.storage

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.organiza.testing.JdbcDatabase

class NoteStoreTest {
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val notes = NoteStore(db)
    private fun at(time: String) = Instant.parse("2026-11-16T$time:00Z")

    @Test
    fun `notes are kept newest first, and can be changed and deleted`() {
        notes.add(Note("a", "Felt scattered this morning", "Journal", at("08:00"), null))
        notes.add(Note("b", "Call the dentist", null, at("09:00"), at("17:00")))
        assertEquals(listOf("b", "a"), notes.all().map { it.id })
        notes.update("a", "Felt calm after the walk", "Mood", null, null)
        assertEquals(Note("a", "Felt calm after the walk", "Mood", at("08:00"), null), notes.all().last())
        notes.delete("b")
        assertEquals(listOf("a"), notes.all().map { it.id })
    }

    @Test
    fun `only reminders still ahead are scheduled`() {
        notes.add(Note("past", "Water the plants", null, at("07:00"), at("08:00")))
        notes.add(Note("soon", "Call the dentist", null, at("07:00"), at("17:00")))
        notes.add(Note("none", "Just a thought", null, at("07:00"), null))
        assertEquals(listOf("soon"), notes.upcoming(at("12:00")).map { it.id })
    }

    @Test
    fun `an empty note or an overlong subject is refused`() {
        assertFails { notes.add(Note("x", "", null, at("08:00"), null)) }
        assertFails { notes.add(Note("y", "text", "s".repeat(41), at("08:00"), null)) }
    }

    @Test
    fun `a note keeps the title typed for it, and a title stays short`() {
        notes.add(Note("t", "Ask about the new schedule and the parking", "Work", at("08:00"), null, "Monday meeting"))
        assertEquals("Monday meeting", notes.all().single().title)
        notes.update("t", "Ask about parking", "Work", null, "Meeting")
        assertEquals("Meeting", notes.all().single().title)
        assertFails { notes.add(Note("u", "text", null, at("08:00"), null, "t".repeat(81))) }
    }
}
