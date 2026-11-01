package org.organiza.scheduler

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.calendar.Occurrence
import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.enforcement.BlockList
import org.organiza.enforcement.SessionPolicy
import org.organiza.storage.Migrations
import org.organiza.storage.RuleStore
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredRule
import org.organiza.testing.FakeClock
import org.organiza.testing.JdbcDatabase

class SessionSchedulerTest {
    private val start = Instant.parse("2026-11-01T10:00:00Z")
    private val clock = FakeClock(start)
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val rules = RuleStore(db)
    private val sessions = SessionStore(db)
    private val scheduler = SessionScheduler(db, clock)

    private fun pkg(raw: String) = (PackageName.parse(raw) as Parsed.Valid).value
    private fun domain(raw: String) = (DomainName.parse(raw) as Parsed.Valid).value

    private val instagram = pkg("com.instagram.android")
    private val reddit = domain("reddit.com")
    private val focus = rule("focus", "#focus", packages = setOf(instagram)).also { rules.save(it, start) }

    private fun rule(
        id: String,
        tag: String?,
        strict: Boolean = false,
        packages: Set<PackageName> = emptySet(),
        domains: Set<DomainName> = emptySet(),
        blockShortForm: Boolean = false,
        enabled: Boolean = true,
    ) = StoredRule(id, id, tag, strict, blockShortForm, enabled, packages, domains)

    private fun occurrence(
        uid: String = "u1",
        fromNow: Duration = Duration.ofHours(1),
        length: Duration = Duration.ofHours(1),
        tags: Set<String> = setOf("#focus"),
    ) = Occurrence(uid, start.plus(fromNow), start.plus(fromNow).plus(length), tags)

    private fun sync(vararg occurrences: Occurrence) = scheduler.syncCalendar(
        occurrences.toList(),
        windowStart = start,
        windowEnd = start.plus(Duration.ofDays(60)),
    )

    private fun scheduled() = sessions.inState(SessionState.SCHEDULED)

    @Test
    fun `tagged events become scheduled sessions`() {
        val result = sync(occurrence())
        assertEquals(CalendarSync(added = 1, updated = 0, removed = 0, skippedStrict = emptyList()), result)
        val session = scheduled().single()
        assertEquals("focus", session.ruleId)
        assertEquals("u1", session.calendarEventUid)
    }

    @Test
    fun `events without a matching rule are ignored`() {
        sync(occurrence(tags = setOf("#other")), occurrence(uid = "u2", tags = emptySet()))
        assertEquals(emptyList(), scheduled())
    }

    @Test
    fun `disabled rules do not schedule`() {
        rules.save(rule("off", "#off", enabled = false), start)
        sync(occurrence(tags = setOf("#off")))
        assertEquals(emptyList(), scheduled())
    }

    @Test
    fun `tag matching ignores case on the rule side`() {
        rules.save(rule("deep", "#DeepWork"), start)
        sync(occurrence(tags = setOf("#deepwork")))
        assertEquals("deep", scheduled().single().ruleId)
    }

    @Test
    fun `with several matching tags the alphabetically first wins`() {
        rules.save(rule("alpha", "#alpha"), start)
        sync(occurrence(tags = setOf("#focus", "#alpha")))
        assertEquals("alpha", scheduled().single().ruleId)
    }

    @Test
    fun `syncing twice is idempotent`() {
        sync(occurrence())
        assertEquals(CalendarSync(0, 0, 0, emptyList()), sync(occurrence()))
        assertEquals(1, scheduled().size)
    }

    @Test
    fun `a changed end time updates the scheduled session`() {
        sync(occurrence())
        assertEquals(1, sync(occurrence(length = Duration.ofHours(2))).updated)
        assertEquals(start.plus(Duration.ofHours(3)), scheduled().single().end)
    }

    @Test
    fun `an event removed from the calendar drops its scheduled session`() {
        sync(occurrence())
        assertEquals(1, sync().removed)
        assertEquals(emptyList(), scheduled())
    }

    @Test
    fun `events already over are not imported`() {
        sync(occurrence(fromNow = Duration.ofHours(-2)))
        assertEquals(emptyList(), scheduled())
    }

    @Test
    fun `deleting an event cannot end a running session`() {
        sync(occurrence(fromNow = Duration.ZERO))
        scheduler.reconcile()
        sync()
        assertEquals(1, sessions.inState(SessionState.ACTIVE).size)
        assertEquals(setOf(instagram), scheduler.reconcile().policy.blockList.packages)
    }

    @Test
    fun `shortening an event cannot end a running session early`() {
        sync(occurrence(fromNow = Duration.ZERO))
        scheduler.reconcile()
        sync(occurrence(fromNow = Duration.ZERO, length = Duration.ofMinutes(1)))
        assertEquals(start.plus(Duration.ofHours(1)), sessions.inState(SessionState.ACTIVE).single().end)
    }

    @Test
    fun `strict events over the limit are reported and skipped`() {
        rules.save(rule("hard", "#hard", strict = true), start)
        val result = sync(occurrence(tags = setOf("#hard"), length = Duration.ofHours(25)))
        assertEquals(listOf("u1"), result.skippedStrict)
        assertEquals(emptyList(), scheduled())
    }

    @Test
    fun `a strict event of exactly the limit is allowed`() {
        rules.save(rule("hard", "#hard", strict = true), start)
        assertEquals(emptyList(), sync(occurrence(tags = setOf("#hard"), length = Duration.ofHours(24))).skippedStrict)
    }

    @Test
    fun `nothing active means an idle policy`() {
        assertEquals(SessionPolicy.IDLE, scheduler.reconcile().policy)
    }

    @Test
    fun `a session activates at its start and completes at its end`() {
        sync(occurrence())
        val before = scheduler.reconcile()
        assertEquals(SessionPolicy.IDLE, before.policy)
        assertEquals(start.plus(Duration.ofHours(1)), before.nextTransition)

        clock.advance(Duration.ofHours(1))
        val during = scheduler.reconcile()
        assertEquals(setOf(instagram), during.policy.blockList.packages)
        assertEquals(start.plus(Duration.ofHours(2)), during.nextTransition)

        clock.advance(Duration.ofHours(1))
        val after = scheduler.reconcile()
        assertEquals(SessionPolicy.IDLE, after.policy)
        assertEquals(null, after.nextTransition)
        assertEquals(SessionState.COMPLETED, sessions.find(sessionId())?.state)
    }

    private fun sessionId() = "cal:u1:${start.plus(Duration.ofHours(1)).toEpochMilli()}"

    @Test
    fun `a session missed entirely is aborted not completed`() {
        sync(occurrence())
        clock.advance(Duration.ofHours(5))
        scheduler.reconcile()
        assertEquals(SessionState.ABORTED, sessions.find(sessionId())?.state)
    }

    @Test
    fun `overlapping sessions combine their policies`() {
        rules.save(
            rule("hard", "#hard", strict = true, domains = setOf(reddit), blockShortForm = true),
            start,
        )
        sync(occurrence(fromNow = Duration.ZERO), occurrence(uid = "u2", fromNow = Duration.ZERO, tags = setOf("#hard")))
        assertEquals(
            SessionPolicy(BlockList(setOf(instagram), setOf(reddit)), strict = true, blockShortForm = true),
            scheduler.reconcile().policy,
        )
    }

    @Test
    fun `active sessions survive a restart`() {
        sync(occurrence(fromNow = Duration.ZERO))
        scheduler.reconcile()
        val restarted = SessionScheduler(db, clock)
        assertEquals(setOf(instagram), restarted.reconcile().policy.blockList.packages)
    }

    @Test
    fun `manual sessions start at once and end on time`() {
        val started = scheduler.startManual(ManualRequest(setOf(instagram), emptySet()), Duration.ofMinutes(30))
        assertEquals(StartResult.Started, started)
        assertEquals(setOf(instagram), scheduler.reconcile().policy.blockList.packages)
        clock.advance(Duration.ofMinutes(30))
        assertEquals(SessionPolicy.IDLE, scheduler.reconcile().policy)
    }

    @Test
    fun `finished manual sessions clean up their private rule`() {
        scheduler.startManual(ManualRequest(setOf(instagram), emptySet()), Duration.ofMinutes(30))
        clock.advance(Duration.ofMinutes(30))
        scheduler.reconcile()
        assertEquals(listOf("focus"), rules.all().map { it.id })
    }

    @Test
    fun `manual sessions must have a positive length`() {
        assertEquals(
            StartResult.InvalidDuration,
            scheduler.startManual(ManualRequest(setOf(instagram), emptySet()), Duration.ZERO),
        )
    }

    @Test
    fun `strict manual sessions over the limit are refused`() {
        val request = ManualRequest(setOf(instagram), emptySet(), strict = true)
        assertEquals(StartResult.InvalidDuration, scheduler.startManual(request, Duration.ofHours(25)))
        assertEquals(StartResult.Started, scheduler.startManual(request, Duration.ofHours(24)))
    }

    @Test
    fun `normal sessions can be stopped early`() {
        scheduler.startManual(ManualRequest(setOf(instagram), emptySet()), Duration.ofMinutes(30))
        assertEquals(StopResult.Stopped, scheduler.stopActive())
        assertEquals(SessionPolicy.IDLE, scheduler.reconcile().policy)
    }

    @Test
    fun `strict sessions cannot be stopped early`() {
        scheduler.startManual(ManualRequest(setOf(instagram), emptySet(), strict = true), Duration.ofMinutes(30))
        assertEquals(StopResult.RefusedStrict, scheduler.stopActive())
        assertEquals(setOf(instagram), scheduler.reconcile().policy.blockList.packages)
    }

    @Test
    fun `stopping leaves strict sessions running when both are active`() {
        scheduler.startManual(ManualRequest(setOf(instagram), emptySet()), Duration.ofMinutes(30))
        scheduler.startManual(ManualRequest(setOf(pkg("com.other.app")), emptySet(), strict = true), Duration.ofMinutes(30))
        assertEquals(StopResult.RefusedStrict, scheduler.stopActive())
        assertEquals(2, sessions.inState(SessionState.ACTIVE).size)
    }

    @Test
    fun `a rule in a running strict session cannot be edited`() {
        rules.save(rule("hard", "#hard", strict = true, packages = setOf(instagram)), start)
        sync(occurrence(fromNow = Duration.ZERO, tags = setOf("#hard")))
        scheduler.reconcile()
        val loosened = rule("hard", "#hard", strict = false)
        assertEquals(RuleEdit.RefusedStrict, scheduler.saveRule(loosened))
        assertEquals(setOf(instagram), scheduler.reconcile().policy.blockList.packages)
        assertEquals(RuleEdit.RefusedStrict, scheduler.deleteRule("hard"))
    }

    @Test
    fun `rules can be edited outside a strict session`() {
        val changed = rule("focus", "#focus", packages = setOf(pkg("com.other.app")))
        assertEquals(RuleEdit.Saved, scheduler.saveRule(changed))
        assertEquals(changed, rules.find("focus"))
        assertEquals(RuleEdit.Saved, scheduler.deleteRule("focus"))
    }

    @Test
    fun `transitions run in one transaction`() {
        sync(occurrence(fromNow = Duration.ZERO))
        val first = scheduler.reconcile()
        val second = scheduler.reconcile()
        assertEquals(first, second)
    }

    @Test
    fun `the test hook can end a strict session`() {
        scheduler.startManual(ManualRequest(setOf(instagram), emptySet(), strict = true), Duration.ofMinutes(30))
        scheduler.endAllForTesting()
        assertEquals(SessionPolicy.IDLE, scheduler.reconcile().policy)
    }
}
