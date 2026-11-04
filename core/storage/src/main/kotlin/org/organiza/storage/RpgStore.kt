package org.organiza.storage

import java.time.Instant
import java.time.LocalDate

public enum class RpgKind { HABIT, DAILY, TODO }

public enum class RpgReason { HABIT_UP, HABIT_DOWN, DAILY, TODO, MISSED_DAILY, BROKE_BLOCK, FAINTED }

/** A habit, daily or to-do (ADR-0019). [doneAt] is set only on a finished to-do. */
public data class RpgTask(val id: String, val kind: RpgKind, val title: String, val createdAt: Instant, val doneAt: Instant?)

public class RpgStore(private val db: Database) {
    public fun addTask(id: String, kind: RpgKind, title: String, at: Instant) {
        db.execute(
            "INSERT INTO rpg_task (id, kind, title, created_at_ms) VALUES (?, ?, ?, ?)",
            listOf(id, kind.name.lowercase(), title, at.toEpochMilli()),
        )
    }

    public fun tasks(): List<RpgTask> =
        db.query("SELECT id, kind, title, created_at_ms, done_at_ms FROM rpg_task ORDER BY created_at_ms, id") { row ->
            RpgTask(
                row.string(0).orEmpty(),
                RpgKind.valueOf(row.string(1).orEmpty().uppercase()),
                row.string(2).orEmpty(),
                Instant.ofEpochMilli(row.long(3) ?: 0),
                row.long(4)?.let(Instant::ofEpochMilli),
            )
        }

    public fun complete(id: String, at: Instant) {
        db.execute("UPDATE rpg_task SET done_at_ms = ? WHERE id = ? AND kind = 'todo'", listOf(at.toEpochMilli(), id))
    }

    public fun delete(id: String) {
        db.execute("DELETE FROM rpg_task WHERE id = ?", listOf(id))
    }

    public fun log(reason: RpgReason, taskId: String?, day: LocalDate, xp: Int, hp: Int, at: Instant) {
        db.execute(
            "INSERT INTO rpg_event (task_id, reason, local_date, xp, hp, at_ms) VALUES (?, ?, ?, ?, ?, ?)",
            listOf(taskId, reason.name.lowercase(), day.toString(), xp.toLong(), hp.toLong(), at.toEpochMilli()),
        )
    }

    /** Removes a daily's check for [day], and the XP it earned. */
    public fun uncheck(taskId: String, day: LocalDate) {
        db.execute("DELETE FROM rpg_event WHERE task_id = ? AND local_date = ? AND reason = 'daily'", listOf(taskId, day.toString()))
    }

    public fun dailyChecks(): Map<String, Set<LocalDate>> =
        db.query("SELECT task_id, local_date FROM rpg_event WHERE reason = 'daily' AND task_id IS NOT NULL") { row ->
            row.string(0).orEmpty() to LocalDate.parse(row.string(1))
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    /** Every XP and HP change, oldest first, as (xp, hp). */
    public fun entries(): List<Pair<Int, Int>> =
        db.query("SELECT xp, hp FROM rpg_event ORDER BY id") { row -> (row.long(0) ?: 0).toInt() to (row.long(1) ?: 0).toInt() }
}
