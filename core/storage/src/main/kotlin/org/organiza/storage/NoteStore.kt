package org.organiza.storage

import java.time.Instant

/** A journal entry or reminder (ADR-0030). [remindAt] is a one-off reminder, if any; [title] is typed by hand (ADR-0036). */
public data class Note(
    val id: String,
    val body: String,
    val subject: String?,
    val createdAt: Instant,
    val remindAt: Instant?,
    val title: String? = null,
)

public class NoteStore(private val db: Database) {
    public fun add(note: Note) {
        db.execute(
            "INSERT INTO note (id, body, subject, created_at_ms, remind_at_ms, title) VALUES (?, ?, ?, ?, ?, ?)",
            listOf(note.id, note.body, note.subject, note.createdAt.toEpochMilli(), note.remindAt?.toEpochMilli(), note.title),
        )
    }

    public fun update(id: String, body: String, subject: String?, remindAt: Instant?, title: String?) {
        db.execute(
            "UPDATE note SET body = ?, subject = ?, remind_at_ms = ?, title = ? WHERE id = ?",
            listOf(body, subject, remindAt?.toEpochMilli(), title, id),
        )
    }

    public fun delete(id: String) {
        db.execute("DELETE FROM note WHERE id = ?", listOf(id))
    }

    /** Every note, newest first. */
    public fun all(): List<Note> = read("SELECT id, body, subject, created_at_ms, remind_at_ms, title FROM note ORDER BY created_at_ms DESC, id", emptyList())

    /** Notes whose reminder is still ahead of [now], soonest first. */
    public fun upcoming(now: Instant): List<Note> = read(
        "SELECT id, body, subject, created_at_ms, remind_at_ms, title FROM note WHERE remind_at_ms > ? ORDER BY remind_at_ms",
        listOf(now.toEpochMilli()),
    )

    private fun read(sql: String, args: List<Any?>): List<Note> = db.query(sql, args) { row ->
        Note(
            row.string(0).orEmpty(),
            row.string(1).orEmpty(),
            row.string(2),
            Instant.ofEpochMilli(row.long(3) ?: 0),
            row.long(4)?.let(Instant::ofEpochMilli),
            row.string(5),
        )
    }
}
