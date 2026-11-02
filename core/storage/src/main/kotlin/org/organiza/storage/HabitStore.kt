package org.organiza.storage

import java.time.Instant
import java.time.LocalDate

public data class StoredHabit(val id: String, val name: String)

public enum class HabitKind(internal val column: String) {
    MANUAL("manual"),
    SCREEN_TIME_UNDER("screen_time_under"),
    ZERO_LAUNCHES("zero_launches"),
}

/** How a habit is judged. [target] is a package name, and [limitMillis] a daily limit. */
public data class HabitCriterion(val kind: HabitKind, val target: String?, val limitMillis: Long?)

public class HabitStore(private val db: Database) {
    public fun add(id: String, name: String, at: Instant) {
        db.execute(
            "INSERT INTO habit (id, name, criterion_kind, created_at_ms) VALUES (?, ?, 'manual', ?)",
            listOf(id, name, at.toEpochMilli()),
        )
    }

    public fun add(id: String, name: String, criterion: HabitCriterion, at: Instant) {
        db.execute(
            "INSERT INTO habit (id, name, criterion_kind, criterion_target, criterion_limit_ms, created_at_ms) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            listOf(id, name, criterion.kind.column, criterion.target, criterion.limitMillis, at.toEpochMilli()),
        )
    }

    public fun criterion(id: String): HabitCriterion? =
        db.query("SELECT criterion_kind, criterion_target, criterion_limit_ms FROM habit WHERE id = ?", listOf(id)) { row ->
            HabitCriterion(HabitKind.entries.first { it.column == row.string(0) }, row.string(1), row.long(2))
        }.firstOrNull()

    public fun active(): List<StoredHabit> =
        db.query("SELECT id, name FROM habit WHERE archived_at_ms IS NULL ORDER BY name COLLATE NOCASE") { row ->
            StoredHabit(row.string(0).orEmpty(), row.string(1).orEmpty())
        }

    /** Hides a habit and keeps its history. */
    public fun archive(id: String, at: Instant) {
        db.execute("UPDATE habit SET archived_at_ms = ? WHERE id = ?", listOf(at.toEpochMilli(), id))
    }

    public fun mark(id: String, date: LocalDate, success: Boolean) {
        db.execute(
            "INSERT INTO habit_log (habit_id, local_date, success) VALUES (?, ?, ?) " +
                "ON CONFLICT (habit_id, local_date) DO UPDATE SET success = excluded.success",
            listOf(id, date.toString(), if (success) 1L else 0L),
        )
    }

    /** Results from [from] up to, but not including, [to]. */
    public fun results(id: String, from: LocalDate, to: LocalDate): Map<LocalDate, Boolean> =
        db.query(
            "SELECT local_date, success FROM habit_log WHERE habit_id = ? AND local_date >= ? AND local_date < ?",
            listOf(id, from.toString(), to.toString()),
        ) { row -> LocalDate.parse(row.string(0).orEmpty()) to (row.long(1) == 1L) }.toMap()
}
