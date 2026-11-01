package org.organiza.storage

import java.time.Instant
import org.organiza.domain.port.Capability

public enum class SessionSource(internal val column: String) {
    MANUAL("manual"),
    CALENDAR("calendar"),
}

public enum class SessionState(internal val column: String) {
    SCHEDULED("scheduled"),
    ACTIVE("active"),
    COMPLETED("completed"),
    ABORTED("aborted"),
}

public data class StoredSession(
    val id: String,
    val ruleId: String?,
    val source: SessionSource,
    val state: SessionState,
    val calendarEventUid: String?,
    val start: Instant,
    val end: Instant,
    val finishedAt: Instant?,
)

public class SessionStore(private val db: Database) {
    public fun insert(session: StoredSession) {
        db.execute(
            "INSERT INTO session (id, rule_id, source, state, calendar_event_uid, starts_at_ms, ends_at_ms, finished_at_ms) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(
                session.id,
                session.ruleId,
                session.source.column,
                session.state.column,
                session.calendarEventUid,
                session.start.toEpochMilli(),
                session.end.toEpochMilli(),
                session.finishedAt?.toEpochMilli(),
            ),
        )
    }

    public fun find(id: String): StoredSession? = select("WHERE id = ?", listOf(id)).firstOrNull()

    public fun inState(state: SessionState): List<StoredSession> = select("WHERE state = ?", listOf(state.column))

    public fun activate(id: String) {
        db.execute("UPDATE session SET state = ? WHERE id = ?", listOf(SessionState.ACTIVE.column, id))
    }

    public fun finish(id: String, state: SessionState, at: Instant?) {
        db.execute(
            "UPDATE session SET state = ?, finished_at_ms = ? WHERE id = ?",
            listOf(state.column, at?.toEpochMilli(), id),
        )
    }

    /** Changes a session that has not started yet. */
    public fun reschedule(id: String, ruleId: String?, end: Instant) {
        db.execute(
            "UPDATE session SET rule_id = ?, ends_at_ms = ? WHERE id = ? AND state = ?",
            listOf(ruleId, end.toEpochMilli(), id, SessionState.SCHEDULED.column),
        )
    }

    /** Removes a session that has not started yet. */
    public fun removeScheduled(id: String) {
        db.execute("DELETE FROM session WHERE id = ? AND state = ?", listOf(id, SessionState.SCHEDULED.column))
    }

    public fun grantedCapabilities(id: String): Set<Capability>? =
        db.query("SELECT granted_capabilities FROM session WHERE id = ?", listOf(id)) { it.string(0) }
            .firstOrNull()
            ?.let { stored -> stored.split(',').filter(String::isNotEmpty).map(Capability::valueOf).toSet() }

    /** Records what is granted now against every active session. */
    public fun saveGrantedCapabilities(granted: Set<Capability>) {
        db.execute(
            "UPDATE session SET granted_capabilities = ? WHERE state = ?",
            listOf(granted.joinToString(",") { it.name }, SessionState.ACTIVE.column),
        )
    }

    private fun select(where: String, args: List<Any?>): List<StoredSession> =
        db.query(
            "SELECT id, rule_id, source, state, calendar_event_uid, starts_at_ms, ends_at_ms, finished_at_ms " +
                "FROM session $where ORDER BY starts_at_ms, id",
            args,
        ) { row ->
            StoredSession(
                id = row.string(0).orEmpty(),
                ruleId = row.string(1),
                source = SessionSource.entries.first { it.column == row.string(2) },
                state = SessionState.entries.first { it.column == row.string(3) },
                calendarEventUid = row.string(4),
                start = Instant.ofEpochMilli(row.long(5) ?: 0),
                end = Instant.ofEpochMilli(row.long(6) ?: 0),
                finishedAt = row.long(7)?.let(Instant::ofEpochMilli),
            )
        }
}
