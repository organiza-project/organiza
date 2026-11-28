package org.organiza.storage

import java.time.Duration
import java.time.Instant

/** A calendar event (ADR-0040): [remindBefore] minutes before its start a reminder rings, or none when null. */
public data class CalendarEvent(val id: String, val title: String, val start: Instant, val minutes: Int, val remindBefore: Int?) {
    val end: Instant get() = start.plus(Duration.ofMinutes(minutes.toLong()))
}

public class EventStore(private val db: Database) {
    public fun add(event: CalendarEvent) {
        db.execute(
            "INSERT INTO calendar_event (id, title, start_ms, minutes, remind_before) VALUES (?, ?, ?, ?, ?)",
            listOf(event.id, event.title, event.start.toEpochMilli(), event.minutes.toLong(), event.remindBefore?.toLong()),
        )
    }

    public fun delete(id: String) {
        db.execute("DELETE FROM calendar_event WHERE id = ?", listOf(id))
    }

    /** Events that overlap [from] to [to], earliest first, so one running past midnight shows on both days. */
    public fun between(from: Instant, to: Instant): List<CalendarEvent> = read(
        "SELECT $COLUMNS FROM calendar_event WHERE start_ms < ? AND start_ms + minutes * 60000 > ? ORDER BY start_ms, id",
        listOf(to.toEpochMilli(), from.toEpochMilli()),
    )

    /** Events whose reminder is still ahead of [now], soonest first. */
    public fun reminders(now: Instant): List<CalendarEvent> = read(
        "SELECT $COLUMNS FROM calendar_event WHERE remind_before IS NOT NULL AND start_ms - remind_before * 60000 > ? ORDER BY start_ms",
        listOf(now.toEpochMilli()),
    )

    private fun read(sql: String, args: List<Any?>): List<CalendarEvent> = db.query(sql, args) { row ->
        CalendarEvent(
            row.string(0).orEmpty(),
            row.string(1).orEmpty(),
            Instant.ofEpochMilli(row.long(2) ?: 0),
            (row.long(3) ?: 0).toInt(),
            row.long(4)?.toInt(),
        )
    }

    private companion object {
        const val COLUMNS = "id, title, start_ms, minutes, remind_before"
    }
}
