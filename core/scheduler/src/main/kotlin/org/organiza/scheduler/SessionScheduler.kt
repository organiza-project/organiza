package org.organiza.scheduler

import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.organiza.calendar.Occurrence
import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.ProcessName
import org.organiza.domain.port.Clock
import org.organiza.enforcement.BlockList
import org.organiza.enforcement.SessionPolicy
import org.organiza.storage.Database
import org.organiza.storage.FocusTier
import org.organiza.storage.RuleStore
import org.organiza.storage.SessionSource
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredRule
import org.organiza.storage.StoredSession

public data class CalendarSync(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val skippedStrict: List<String>,
)

public data class ScheduleState(
    val policy: SessionPolicy,
    val nextTransition: Instant?,
    val activeSessionIds: List<String>,
)

public data class ManualRequest(
    val packages: Set<PackageName>,
    val domains: Set<DomainName>,
    val strict: Boolean = false,
    val blockShortForm: Boolean = false,
    val processes: Set<ProcessName> = emptySet(),
    val tier: FocusTier = FocusTier.STANDARD,
)

public enum class StartResult { Started, InvalidDuration }

public enum class StopResult { Stopped, RefusedStrict }

public enum class RuleEdit { Saved, RefusedStrict }

/** Turns occurrences into sessions and sessions into the policy in force (ADR-0008). */
public class SessionScheduler(
    private val db: Database,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val rules = RuleStore(db)
    private val sessions = SessionStore(db)

    /** Applies a calendar read. Only sessions still `scheduled` can change or disappear. */
    public fun syncCalendar(
        occurrences: List<Occurrence>,
        windowStart: Instant,
        windowEnd: Instant,
    ): CalendarSync = db.transaction {
        val now = clock.now()
        val tagged = rules.all()
            .filter { it.enabled && it.calendarTag != null }
            .associateBy { it.calendarTag.orEmpty().lowercase(Locale.ROOT) }
        val seen = mutableSetOf<String>()
        val skippedStrict = mutableListOf<String>()
        var added = 0
        var updated = 0
        for (occurrence in occurrences) {
            if (occurrence.end <= now) continue
            val rule = occurrence.tags.filter(tagged::containsKey).minOrNull()?.let(tagged::getValue) ?: continue
            if (rule.strict && Duration.between(occurrence.start, occurrence.end) > MAX_STRICT) {
                skippedStrict += occurrence.uid
                continue
            }
            val id = "cal:${occurrence.uid}:${occurrence.start.toEpochMilli()}"
            seen += id
            val existing = sessions.find(id)
            when {
                existing == null -> {
                    sessions.insert(calendarSession(id, rule, occurrence))
                    added++
                }
                existing.state == SessionState.SCHEDULED &&
                    (existing.end != occurrence.end || existing.ruleId != rule.id) -> {
                    sessions.reschedule(id, rule.id, occurrence.end)
                    updated++
                }
            }
        }
        val stale = sessions.inState(SessionState.SCHEDULED).filter {
            it.source == SessionSource.CALENDAR && it.start >= windowStart && it.start < windowEnd && it.id !in seen
        }
        stale.forEach { sessions.removeScheduled(it.id) }
        CalendarSync(added, updated, stale.size, skippedStrict)
    }

    /** Applies every transition due by now and returns the policy that should be in force. */
    public fun reconcile(): ScheduleState = db.transaction {
        val now = clock.now()
        for (session in sessions.inState(SessionState.SCHEDULED)) {
            when {
                session.end <= now -> sessions.finish(session.id, SessionState.ABORTED, null)
                session.start <= now -> sessions.activate(session.id)
            }
        }
        for (session in sessions.inState(SessionState.ACTIVE)) {
            if (session.end <= now) sessions.finish(session.id, SessionState.COMPLETED, session.end)
        }
        removeUnusedManualRules()
        val active = sessions.inState(SessionState.ACTIVE)
        val upcoming = sessions.inState(SessionState.SCHEDULED).map { it.start } + active.map { it.end }
        ScheduleState(policyFor(active), upcoming.minOrNull(), active.map { it.id })
    }

    public fun startManual(request: ManualRequest, duration: Duration): StartResult {
        val tooLong = request.strict && duration > MAX_STRICT
        if (duration.isNegative || duration.isZero || tooLong) return StartResult.InvalidDuration
        val now = clock.now()
        val id = newId()
        val rule = StoredRule(
            id = MANUAL_RULE_PREFIX + id,
            name = "Manual session",
            calendarTag = null,
            strict = request.strict,
            blockShortForm = request.blockShortForm,
            enabled = true,
            packages = request.packages,
            domains = request.domains,
            processes = request.processes,
        )
        db.transaction {
            rules.save(rule, now)
            sessions.insert(
                StoredSession(id, rule.id, SessionSource.MANUAL, SessionState.ACTIVE, null, now, now.plus(duration), null, request.tier),
            )
        }
        return StartResult.Started
    }

    /** Ends every running session, unless any of them is strict. */
    public fun stopActive(): StopResult = db.transaction {
        val active = sessions.inState(SessionState.ACTIVE)
        if (active.any { it.ruleId?.let(rules::find)?.strict == true }) {
            StopResult.RefusedStrict
        } else {
            finishAll(active)
            StopResult.Stopped
        }
    }

    /** Ends strict sessions too. Only debug builds and tests may call this. */
    public fun endAllForTesting() {
        db.transaction { finishAll(sessions.inState(SessionState.ACTIVE)) }
    }

    public fun isStrictRunning(): Boolean =
        sessions.inState(SessionState.ACTIVE).any { it.ruleId?.let(rules::find)?.strict == true }

    /** Saves a rule, unless a running strict session depends on it. */
    public fun saveRule(rule: StoredRule): RuleEdit = editRule(rule.id) { rules.save(rule, clock.now()) }

    /** Deletes a rule, unless a running strict session depends on it. */
    public fun deleteRule(id: String): RuleEdit = editRule(id) { rules.delete(id) }

    private fun editRule(id: String, edit: () -> Unit): RuleEdit = db.transaction {
        val inUse = rules.find(id)?.strict == true && sessions.inState(SessionState.ACTIVE).any { it.ruleId == id }
        if (inUse) {
            RuleEdit.RefusedStrict
        } else {
            edit()
            RuleEdit.Saved
        }
    }

    private fun finishAll(active: List<StoredSession>) {
        val now = clock.now()
        active.forEach { sessions.finish(it.id, SessionState.ABORTED, now) }
        removeUnusedManualRules()
    }

    private fun removeUnusedManualRules() {
        val inUse = (sessions.inState(SessionState.ACTIVE) + sessions.inState(SessionState.SCHEDULED))
            .mapNotNull { it.ruleId }
            .toSet()
        rules.all().filter { it.id.startsWith(MANUAL_RULE_PREFIX) && it.id !in inUse }.forEach { rules.delete(it.id) }
    }

    /** Monitor sessions add nothing. Any lockdown turns the list into its allowed apps (ADR-0020). */
    private fun policyFor(active: List<StoredSession>): SessionPolicy {
        val blocking = active.filter { it.tier != FocusTier.MONITOR }
        val lockdown = blocking.filter { it.tier == FocusTier.LOCKDOWN }.mapNotNull { session -> session.ruleId?.let(rules::find) }
        val inForce = blocking.mapNotNull { session -> session.ruleId?.let(rules::find) }
        if (inForce.isEmpty()) return SessionPolicy.IDLE
        val list = if (lockdown.isNotEmpty()) {
            BlockList(lockdown.flatMap { it.packages }.toSet(), emptySet(), allowOnly = true)
        } else {
            BlockList(
                inForce.flatMap { it.packages }.toSet(),
                inForce.flatMap { it.domains }.toSet(),
                inForce.flatMap { it.processes }.toSet(),
            )
        }
        return SessionPolicy(
            list,
            strict = inForce.any { it.strict },
            blockShortForm = inForce.any { it.blockShortForm },
        )
    }

    private fun calendarSession(id: String, rule: StoredRule, occurrence: Occurrence) = StoredSession(
        id,
        rule.id,
        SessionSource.CALENDAR,
        SessionState.SCHEDULED,
        occurrence.uid,
        occurrence.start,
        occurrence.end,
        null,
    )

    public companion object {
        /** No strict session may run longer than this, to prevent lockout (ADR-0008). */
        public val MAX_STRICT: Duration = Duration.ofHours(24)
        private const val MANUAL_RULE_PREFIX = "manual:"
    }
}
