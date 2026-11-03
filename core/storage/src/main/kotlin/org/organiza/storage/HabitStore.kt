package org.organiza.storage

import java.time.Instant
import java.time.LocalDate

public data class StoredHabit(val id: String, val name: String)

public enum class HabitKind(internal val column: String) {
    MANUAL("manual"),
    SCREEN_TIME_UNDER("screen_time_under"),
    ZERO_LAUNCHES("zero_launches"),
}

public enum class HabitFrequency { DAILY, WEEKLY, MONTHLY }

/**
 * How often a habit is due (ADR-0018). [target] is the number of met days a period needs: always 1
 * for a daily habit. [reminderMinute] is a local time of day, or null for no reminder.
 */
public data class HabitPlan(val frequency: HabitFrequency, val target: Int, val reminderMinute: Int?) {
    public companion object {
        public val DAILY: HabitPlan = HabitPlan(HabitFrequency.DAILY, 1, null)
    }
}

public data class HabitReminder(val id: String, val name: String, val minute: Int)

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

    public fun add(id: String, name: String, plan: HabitPlan, at: Instant) {
        db.execute(
            "INSERT INTO habit (id, name, criterion_kind, created_at_ms, frequency, target, reminder_minute) " +
                "VALUES (?, ?, 'manual', ?, ?, ?, ?)",
            listOf(id, name, at.toEpochMilli(), plan.frequency.name.lowercase(), plan.target.toLong(), plan.reminderMinute?.toLong()),
        )
    }

    public fun plan(id: String): HabitPlan? =
        db.query("SELECT frequency, target, reminder_minute FROM habit WHERE id = ?", listOf(id)) { row ->
            HabitPlan(HabitFrequency.valueOf(row.string(0).orEmpty().uppercase()), row.long(1)?.toInt() ?: 1, row.long(2)?.toInt())
        }.firstOrNull()

    public fun setPlan(id: String, plan: HabitPlan) {
        db.execute(
            "UPDATE habit SET frequency = ?, target = ?, reminder_minute = ? WHERE id = ?",
            listOf(plan.frequency.name.lowercase(), plan.target.toLong(), plan.reminderMinute?.toLong(), id),
        )
    }

    /** Active habits with a reminder, for the alarm scheduler. */
    public fun reminders(): List<HabitReminder> = db.query(
        "SELECT id, name, reminder_minute FROM habit WHERE archived_at_ms IS NULL AND reminder_minute IS NOT NULL ORDER BY reminder_minute",
    ) { row -> HabitReminder(row.string(0).orEmpty(), row.string(1).orEmpty(), row.long(2)?.toInt() ?: 0) }

    public fun criterion(id: String): HabitCriterion? =
        db.query("SELECT criterion_kind, criterion_target, criterion_limit_ms FROM habit WHERE id = ?", listOf(id)) { row ->
            HabitCriterion(HabitKind.entries.first { it.column == row.string(0) }, row.string(1), row.long(2))
        }.firstOrNull()

    public fun active(): List<StoredHabit> =
        db.query("SELECT id, name FROM habit WHERE archived_at_ms IS NULL ORDER BY name COLLATE NOCASE") { row ->
            StoredHabit(row.string(0).orEmpty(), row.string(1).orEmpty())
        }

    public fun createdAt(id: String): Instant? =
        db.query("SELECT created_at_ms FROM habit WHERE id = ?", listOf(id)) { row -> row.long(0) }
            .firstOrNull()?.let(Instant::ofEpochMilli)

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
