package org.organiza.presentation

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.organiza.storage.Note

/** One note as the Notes tab shows it: its title, text, subject, time of day and reminder. */
public data class NoteRow(val id: String, val title: String?, val body: String, val subject: String?, val time: String, val reminder: String?)

public data class NoteDay(val label: String, val notes: List<NoteRow>)

/** Notes by day, newest first, optionally only one [subject] (ADR-0030). [more] is true when older notes are not loaded. */
public data class NotesModel(val days: List<NoteDay>, val subjects: List<String>, val more: Boolean = false) {
    public companion object {
        private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
        private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

        public fun build(
            notes: List<Note>,
            subject: String?,
            today: LocalDate,
            zone: ZoneId,
            subjects: List<String> = notes.mapNotNull { it.subject }.distinct().sorted(),
            more: Boolean = false,
        ): NotesModel {
            val shown = notes.filter { subject == null || it.subject == subject }
            val days = shown.groupBy { it.createdAt.atZone(zone).toLocalDate() }.map { (day, group) ->
                NoteDay(label(day, today), group.map { note -> row(note, zone) })
            }
            return NotesModel(days, subjects, more)
        }

        private fun label(day: LocalDate, today: LocalDate): String = when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(DAY)
        }

        private fun row(note: Note, zone: ZoneId): NoteRow {
            val reminder = note.remindAt?.atZone(zone)?.let { "Reminder ${it.toLocalDate().format(DAY)}, ${it.format(TIME)}" }
            return NoteRow(note.id, note.title, note.body, note.subject, note.createdAt.atZone(zone).format(TIME), reminder)
        }
    }
}
