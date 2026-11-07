package org.organiza.scheduler

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.storage.HabitStore
import org.organiza.storage.Migrations
import org.organiza.storage.RuleStore
import org.organiza.storage.SessionSource
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredRule
import org.organiza.testing.FakeClock
import org.organiza.testing.JdbcDatabase
import org.organiza.voice.VoiceCommand

class VoiceExecutorTest {
    private val now = Instant.parse("2026-11-01T10:00:00Z")
    private val clock = FakeClock(now)
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val habits = HabitStore(db)
    private val rules = RuleStore(db)
    private val sessions = SessionStore(db)
    private val scheduler = SessionScheduler(db, clock)
    private var nextId = 0
    private val executor = VoiceExecutor(db, clock, scheduler) { "id${nextId++}" }
    private val today = LocalDate.of(2026, 11, 1)
    private val later = now.plus(Duration.ofHours(2))

    init {
        rules.save(StoredRule("r", "Focus", "#focus", false, false, true, emptySet(), emptySet()), now)
        rules.save(StoredRule("s", "Hard", "#hard", true, false, true, emptySet(), emptySet()), now)
    }

    private fun scheduled() = sessions.inState(SessionState.SCHEDULED)

    @Test
    fun `adds a habit and refuses a duplicate`() {
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.AddHabit("Stretch")))
        assertEquals(VoiceOutcome.AlreadyExists, executor.apply(VoiceCommand.AddHabit("stretch")))
        assertEquals(listOf("Stretch"), habits.active().map { it.name })
    }

    @Test
    fun `removing archives so history survives`() {
        habits.add("h", "Stretch", now)
        habits.mark("h", today, true)
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.RemoveHabit("stretch")))
        assertEquals(emptyList(), habits.active())
        assertEquals(mapOf(today to true), habits.results("h", today, today.plusDays(1)))
    }

    @Test
    fun `marks a habit matching case and spoken numbers`() {
        habits.add("h", "Read ten pages", now)
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.MarkHabit("read 10 pages", today, true)))
        assertEquals(mapOf(today to true), habits.results("h", today, today.plusDays(1)))
    }

    @Test
    fun `a unique partial name matches and an ambiguous one does not`() {
        habits.add("a", "Morning run", now)
        habits.add("b", "Evening run", now)
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.MarkHabit("evening", today, true)))
        assertEquals(
            VoiceOutcome.AmbiguousHabit(listOf("Evening run", "Morning run")),
            executor.apply(VoiceCommand.MarkHabit("run", today, true)),
        )
        assertEquals(VoiceOutcome.NoSuchHabit, executor.apply(VoiceCommand.RemoveHabit("swim")))
    }

    @Test
    fun `future days cannot be marked`() {
        habits.add("h", "Stretch", now)
        assertEquals(VoiceOutcome.InvalidTime, executor.apply(VoiceCommand.MarkHabit("stretch", today.plusDays(2), true)))
    }

    @Test
    fun `schedules a session for the rule with that tag`() {
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.AddSession("focus", later, 60)))
        val session = scheduled().single()
        assertEquals("r", session.ruleId)
        assertEquals(SessionSource.MANUAL, session.source)
        assertEquals(later.plus(Duration.ofHours(1)), session.end)
    }

    @Test
    fun `scheduled sessions activate and calendar sync leaves them alone`() {
        executor.apply(VoiceCommand.AddSession("focus", later, 60))
        scheduler.syncCalendar(emptyList(), now, now.plus(Duration.ofDays(60)))
        assertEquals(1, scheduled().size)
        clock.advance(Duration.ofHours(2))
        assertEquals(1, scheduler.reconcile().activeSessionIds.size)
    }

    @Test
    fun `unknown tags past times and overlong strict sessions are refused`() {
        assertEquals(VoiceOutcome.NoSuchRule, executor.apply(VoiceCommand.AddSession("nap", later, 60)))
        assertEquals(VoiceOutcome.InvalidTime, executor.apply(VoiceCommand.AddSession("focus", now.minusSeconds(60), 60)))
        assertEquals(VoiceOutcome.InvalidTime, executor.apply(VoiceCommand.AddSession("hard", later, 25 * 60)))
        assertEquals(emptyList(), scheduled())
    }

    @Test
    fun `cancels the exact session or the next one`() {
        val second = later.plus(Duration.ofHours(4))
        executor.apply(VoiceCommand.AddSession("focus", later, 60))
        executor.apply(VoiceCommand.AddSession("focus", second, 60))
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.CancelSession("focus", second)))
        assertEquals(listOf(later), scheduled().map { it.start })
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.CancelSession("focus", null)))
        assertEquals(emptyList(), scheduled())
        assertEquals(VoiceOutcome.NothingToCancel, executor.apply(VoiceCommand.CancelSession("focus", null)))
    }

    @Test
    fun `a running session cannot be cancelled by voice`() {
        executor.apply(VoiceCommand.AddSession("focus", later, 60))
        clock.advance(Duration.ofHours(2))
        scheduler.reconcile()
        assertEquals(VoiceOutcome.NothingToCancel, executor.apply(VoiceCommand.CancelSession("focus", null)))
        assertEquals(1, sessions.inState(SessionState.ACTIVE).size)
    }

    @Test
    fun `the schedule is frozen during a strict session but habits are not`() {
        executor.apply(VoiceCommand.AddSession("focus", later, 60))
        scheduler.startManual(ManualRequest(emptySet(), emptySet(), strict = true), Duration.ofMinutes(30))
        assertEquals(VoiceOutcome.RefusedStrict, executor.apply(VoiceCommand.CancelSession("focus", null)))
        assertEquals(VoiceOutcome.RefusedStrict, executor.apply(VoiceCommand.AddSession("focus", later.plusSeconds(99), 60)))
        assertEquals(1, scheduled().size)
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.AddHabit("stretch")))
    }

    @Test
    fun `logged minutes go to the named habit`() {
        executor.apply(VoiceCommand.AddHabit("Reading"))
        assertEquals(VoiceOutcome.Done, executor.apply(VoiceCommand.LogMinutes("reading", today, 30)))
        val id = habits.active().single().id
        assertEquals(30, habits.loggedMinutes(id, today))
        assertEquals(VoiceOutcome.NoSuchHabit, executor.apply(VoiceCommand.LogMinutes("chess", today, 30)))
        assertEquals(VoiceOutcome.InvalidTime, executor.apply(VoiceCommand.LogMinutes("reading", today.plusDays(1), 30)))
    }

    @Test
    fun `an event is handed to the phone's calendar and stores nothing here`() {
        assertEquals(VoiceOutcome.OpenCalendar, executor.apply(VoiceCommand.AddEvent("Meeting with John", later, 60)))
        assertEquals(emptyList(), scheduled())
    }
}
