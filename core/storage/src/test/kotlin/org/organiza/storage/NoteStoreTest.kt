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
        notes.update("a", "Felt calm after the walk", "Mood", null)
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
}
