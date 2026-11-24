package org.organiza.presentation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.storage.Note

class NotesModelTest {
    private val today = LocalDate.parse("2026-11-16")
    private fun at(day: String, time: String) = Instant.parse("${day}T$time:00Z")
    private val notes = listOf(
        Note("c", "Call the dentist", "Errands", at("2026-11-16", "09:00"), at("2026-11-17", "09:30"), "Teeth"),
        Note("b", "Slept badly", "Journal", at("2026-11-15", "22:00"), null),
        Note("a", "Idea for the garden", null, at("2026-11-10", "18:05"), null),
    )

    @Test
    fun `notes fall under their day, newest first, and subjects can be chosen`() {
        val model = NotesModel.build(notes, null, today, ZoneOffset.UTC)
        assertEquals(listOf("Today", "Yesterday", "Tue 10 Nov"), model.days.map { it.label })
        assertEquals(NoteRow("c", "Teeth", "Call the dentist", "Errands", "09:00", "Reminder Tue 17 Nov, 09:30"), model.days.first().notes.single())
        assertEquals(listOf("Errands", "Journal"), model.subjects)
        assertEquals(listOf("b"), NotesModel.build(notes, "Journal", today, ZoneOffset.UTC).days.flatMap { it.notes }.map { it.id })
    }
}
