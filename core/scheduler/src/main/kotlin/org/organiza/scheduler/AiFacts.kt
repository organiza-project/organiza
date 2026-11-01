package org.organiza.scheduler

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.organiza.ai.BusyBlock
import org.organiza.ai.HabitFacts
import org.organiza.ai.Sanitizer
import org.organiza.ai.WeeklyFacts
import org.organiza.storage.Database
import org.organiza.storage.HabitStore
import org.organiza.storage.RuleStore
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.TamperStore

/** Reduces local history to the few structured facts prompts may use (ADR-0009). */
public class AiFacts(db: Database) {
    private val rules = RuleStore(db)
    private val sessions = SessionStore(db)
    private val tamper = TamperStore(db)
    private val habits = HabitStore(db)

    public fun weekly(from: Instant, to: Instant, habits: List<HabitFacts> = emptyList()): WeeklyFacts {
        fun inWeek(state: SessionState) = sessions.inState(state).filter { it.start >= from && it.start < to }
        val completed = inWeek(SessionState.COMPLETED)
        return WeeklyFacts(
            sessionsCompleted = completed.size,
            // A session with no finish time was missed, not given up on.
            sessionsAborted = inWeek(SessionState.ABORTED).count { it.finishedAt != null },
            focusMinutes = completed.sumOf { Duration.between(it.start, it.end).toMinutes() },
            tamperEvents = tamper.all().count { it.at >= from && it.at < to },
            habits = habits,
        )
    }

    /** Each active habit's name, sanitized, and its result count for [from] until [to]. */
    public fun habits(from: LocalDate, to: LocalDate, sanitizer: Sanitizer): List<HabitFacts> =
        habits.active().map { habit ->
            HabitFacts(
                name = sanitizer.clean(habit.name),
                daysMet = habits.results(habit.id, from, to).values.count { it },
                daysTotal = ChronoUnit.DAYS.between(from, to).toInt(),
            )
        }

    public fun busy(from: Instant, to: Instant, zone: ZoneId): List<BusyBlock> =
        (sessions.inState(SessionState.ACTIVE) + sessions.inState(SessionState.SCHEDULED))
            .filter { it.start >= from && it.start < to }
            .map { session ->
                val local = session.start.atZone(zone)
                BusyBlock(
                    day = local.dayOfWeek,
                    startMinute = local.hour * 60 + local.minute,
                    minutes = Duration.between(session.start, session.end).toMinutes().toInt(),
                    tag = session.ruleId?.let(rules::find)?.calendarTag,
                )
            }
}
