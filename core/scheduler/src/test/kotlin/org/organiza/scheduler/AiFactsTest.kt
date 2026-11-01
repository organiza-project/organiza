package org.organiza.scheduler

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.ai.BusyBlock
import org.organiza.domain.port.Capability
import org.organiza.storage.Migrations
import org.organiza.storage.RuleStore
import org.organiza.storage.SessionSource
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredRule
import org.organiza.storage.StoredSession
import org.organiza.storage.TamperStore
import org.organiza.testing.JdbcDatabase

class AiFactsTest {
    private val monday = Instant.parse("2026-11-02T00:00:00Z")
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val sessions = SessionStore(db)
    private val tamper = TamperStore(db)
    private val facts = AiFacts(db)

    init {
        RuleStore(db).save(StoredRule("r", "Secret name", "#focus", false, false, true, emptySet(), emptySet()), monday)
    }

    private fun session(
        id: String,
        state: SessionState,
        startHours: Long,
        minutes: Long,
        finished: Boolean = true,
    ): StoredSession {
        val start = monday.plus(Duration.ofHours(startHours))
        val end = start.plus(Duration.ofMinutes(minutes))
        return StoredSession(id, "r", SessionSource.MANUAL, state, null, start, end, if (finished) end else null)
            .also(sessions::insert)
    }

    @Test
    fun `weekly facts count only the requested week`() {
        session("a", SessionState.COMPLETED, 9, 60)
        session("b", SessionState.COMPLETED, 33, 30)
        session("c", SessionState.ABORTED, 10, 20)
        session("old", SessionState.COMPLETED, -24, 600)
        session("missed", SessionState.ABORTED, 12, 60, finished = false)
        tamper.record(null, Capability.VPN, monday.plusSeconds(60))
        tamper.record(null, Capability.VPN, monday.minusSeconds(60))
        val week = facts.weekly(monday, monday.plus(Duration.ofDays(7)))
        assertEquals(2, week.sessionsCompleted)
        assertEquals(1, week.sessionsAborted)
        assertEquals(90, week.focusMinutes)
        assertEquals(1, week.tamperEvents)
        assertEquals(emptyList(), week.habits)
    }

    @Test
    fun `busy blocks give day minute length and tag only`() {
        session("a", SessionState.SCHEDULED, 9, 60, finished = false)
        session("late", SessionState.SCHEDULED, 24 * 9, 60, finished = false)
        assertEquals(
            listOf(BusyBlock(DayOfWeek.MONDAY, 540, 60, "#focus")),
            facts.busy(monday, monday.plus(Duration.ofDays(7)), ZoneOffset.UTC),
        )
    }
}
