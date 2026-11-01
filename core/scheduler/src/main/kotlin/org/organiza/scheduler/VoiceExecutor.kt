package org.organiza.scheduler

import java.time.Duration
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import org.organiza.domain.port.Clock
import org.organiza.storage.Database
import org.organiza.storage.HabitStore
import org.organiza.storage.RuleStore
import org.organiza.storage.SessionSource
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredHabit
import org.organiza.storage.StoredRule
import org.organiza.storage.StoredSession
import org.organiza.voice.SpokenNumbers
import org.organiza.voice.VoiceCommand

public sealed interface VoiceOutcome {
    public data object Done : VoiceOutcome

    public data object AlreadyExists : VoiceOutcome

    public data object NoSuchHabit : VoiceOutcome

    public data class AmbiguousHabit(val matches: List<String>) : VoiceOutcome

    public data object NoSuchRule : VoiceOutcome

    public data object NothingToCancel : VoiceOutcome

    public data object RefusedStrict : VoiceOutcome

    public data object InvalidTime : VoiceOutcome
}

/** Applies a confirmed command. Voice gets no power the app lacks otherwise (ADR-0010). */
public class VoiceExecutor(
    private val db: Database,
    private val clock: Clock,
    private val scheduler: SessionScheduler,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val habits = HabitStore(db)
    private val rules = RuleStore(db)
    private val sessions = SessionStore(db)

    public fun apply(command: VoiceCommand): VoiceOutcome = db.transaction {
        when (command) {
            is VoiceCommand.AddHabit -> addHabit(command.name)
            is VoiceCommand.RemoveHabit -> withHabit(command.name) { habit ->
                habits.archive(habit.id, clock.now())
                VoiceOutcome.Done
            }
            is VoiceCommand.MarkHabit -> {
                if (command.date.isAfter(clock.now().atZone(zone).toLocalDate())) {
                    VoiceOutcome.InvalidTime
                } else {
                    withHabit(command.name) { habit ->
                        habits.mark(habit.id, command.date, command.done)
                        VoiceOutcome.Done
                    }
                }
            }
            is VoiceCommand.AddSession -> addSession(command)
            is VoiceCommand.CancelSession -> cancelSession(command)
        }
    }

    private fun addHabit(name: String): VoiceOutcome {
        if (habits.active().any { normalized(it.name) == normalized(name) }) return VoiceOutcome.AlreadyExists
        habits.add(newId(), name.replaceFirstChar { it.uppercase(Locale.ROOT) }, clock.now())
        return VoiceOutcome.Done
    }

    private fun withHabit(spoken: String, action: (StoredHabit) -> VoiceOutcome): VoiceOutcome {
        val wanted = normalized(spoken)
        val active = habits.active()
        val exact = active.firstOrNull { normalized(it.name) == wanted }
        if (exact != null) return action(exact)
        val partial = active.filter { wanted in normalized(it.name) }
        return when (partial.size) {
            0 -> VoiceOutcome.NoSuchHabit
            1 -> action(partial.single())
            else -> VoiceOutcome.AmbiguousHabit(partial.map { it.name })
        }
    }

    private fun normalized(name: String) = SpokenNumbers.toDigits(name.lowercase(Locale.ROOT).trim())

    private fun addSession(command: VoiceCommand.AddSession): VoiceOutcome {
        if (scheduler.isStrictRunning()) return VoiceOutcome.RefusedStrict
        val rule = ruleFor(command.tag) ?: return VoiceOutcome.NoSuchRule
        val length = Duration.ofMinutes(command.minutes.toLong())
        val tooLong = rule.strict && length > SessionScheduler.MAX_STRICT
        if (tooLong || !command.start.isAfter(clock.now())) return VoiceOutcome.InvalidTime
        sessions.insert(
            StoredSession(
                newId(),
                rule.id,
                SessionSource.MANUAL,
                SessionState.SCHEDULED,
                null,
                command.start,
                command.start.plus(length),
                null,
            ),
        )
        return VoiceOutcome.Done
    }

    private fun cancelSession(command: VoiceCommand.CancelSession): VoiceOutcome {
        if (scheduler.isStrictRunning()) return VoiceOutcome.RefusedStrict
        val rule = ruleFor(command.tag) ?: return VoiceOutcome.NoSuchRule
        val target = sessions.inState(SessionState.SCHEDULED).firstOrNull { session ->
            session.ruleId == rule.id && (command.start == null || session.start == command.start)
        } ?: return VoiceOutcome.NothingToCancel
        sessions.removeScheduled(target.id)
        return VoiceOutcome.Done
    }

    /** Spoken tags have spaces where written ones may not: "deep work" finds #deepwork. */
    private fun ruleFor(spoken: String): StoredRule? {
        fun compact(text: String) = text.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        return rules.all().firstOrNull { it.enabled && it.calendarTag != null && compact(it.calendarTag.orEmpty()) == compact(spoken) }
    }
}
