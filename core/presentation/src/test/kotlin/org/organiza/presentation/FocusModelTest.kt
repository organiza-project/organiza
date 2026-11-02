package org.organiza.presentation

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.Capability
import org.organiza.enforcement.BlockList
import org.organiza.enforcement.SessionPolicy
import org.organiza.storage.SessionSource
import org.organiza.storage.SessionState
import org.organiza.storage.StoredSession

class FocusModelTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-02T09:36:00Z")
    private val all = Capability.entries.toSet()

    private fun pkg(raw: String) = (PackageName.parse(raw) as Parsed.Valid).value
    private fun domain(raw: String) = (DomainName.parse(raw) as Parsed.Valid).value

    private fun session(
        id: String,
        start: String,
        end: String,
        state: SessionState,
        tag: String? = null,
    ) = SessionView(
        StoredSession(
            id,
            "r",
            if (tag == null) SessionSource.MANUAL else SessionSource.CALENDAR,
            state,
            null,
            Instant.parse("2026-11-02T${start}:00Z"),
            Instant.parse("2026-11-02T${end}:00Z"),
            null,
        ),
        tag,
        strict = false,
    )

    private val policy = SessionPolicy(
        BlockList(setOf(pkg("com.instagram.android"), pkg("com.zhiliaoapp.musically")), setOf(domain("reddit.com"))),
    )

    @Test
    fun `an active session leads with the time left and what is blocked`() {
        val model = FocusModel.build(
            now,
            zone,
            policy,
            listOf(session("a", "09:00", "11:00", SessionState.ACTIVE, "#deepwork")),
            all,
            tamperThisWeek = 0,
        )
        assertEquals("1 h 24 m", model.headline)
        assertEquals("Blocking 2 apps and 1 site until 11:00", model.subline)
        assertEquals(FocusState.ACTIVE, model.state)
    }

    @Test
    fun `when idle it names the next session or says none`() {
        val next = FocusModel.build(
            now,
            zone,
            SessionPolicy.IDLE,
            listOf(session("b", "14:00", "16:00", SessionState.SCHEDULED, "#focus")),
            all,
            0,
        )
        assertEquals("Off", next.headline)
        assertEquals("Nothing is blocked. Next: #focus at 14:00", next.subline)
        val none = FocusModel.build(now, zone, SessionPolicy.IDLE, emptyList(), all, 0)
        assertEquals("Nothing is blocked and nothing is scheduled today", none.subline)
    }

    @Test
    fun `a strict session says it cannot be stopped`() {
        val strict = FocusModel.build(
            now,
            zone,
            policy.copy(strict = true),
            listOf(session("a", "09:00", "11:00", SessionState.ACTIVE).copy(strict = true)),
            all,
            0,
        )
        assertEquals(FocusState.STRICT, strict.state)
        assertEquals("Blocking 2 apps and 1 site until 11:00. Strict: it cannot be stopped early", strict.subline)
    }

    @Test
    fun `the day strip places sessions between six and midnight`() {
        val model = FocusModel.build(
            now,
            zone,
            policy,
            listOf(
                session("done", "06:00", "07:30", SessionState.COMPLETED),
                session("a", "09:00", "11:00", SessionState.ACTIVE),
                session("b", "21:00", "23:59", SessionState.SCHEDULED),
            ),
            all,
            0,
        )
        val strip = model.strip
        assertEquals(LocalTime.of(6, 0), strip.from)
        assertEquals(listOf(0.0, 0.1667, 0.8333), strip.bars.map { "%.4f".format(it.start).toDouble() })
        assertEquals(listOf(BarKind.PAST, BarKind.NOW, BarKind.LATER), strip.bars.map { it.kind })
        assertEquals(0.2, "%.4f".format(strip.now).toDouble())
        assertEquals(listOf("06:00", "12:00", "18:00", "24:00"), strip.ticks.map { it.label })
    }

    @Test
    fun `an early session widens the strip instead of being cut`() {
        val model = FocusModel.build(
            now,
            zone,
            SessionPolicy.IDLE,
            listOf(session("early", "04:00", "05:00", SessionState.COMPLETED)),
            all,
            0,
        )
        assertEquals(LocalTime.of(4, 0), model.strip.from)
        assertEquals(0.0, model.strip.bars.single().start)
    }

    @Test
    fun `missing protection lists what to fix in order of importance`() {
        val model = FocusModel.build(
            now,
            zone,
            SessionPolicy.IDLE,
            emptyList(),
            setOf(Capability.DEVICE_ADMIN),
            0,
        )
        assertEquals(
            listOf(Protection.ACCESSIBILITY, Protection.VPN_CONSENT, Protection.USAGE),
            model.protection.filterNot { it.on }.map { it.kind },
        )
        assertEquals("3 protections are off", model.protectionSummary)
    }

    @Test
    fun `full protection and tamper events are stated plainly`() {
        val model = FocusModel.build(now, zone, SessionPolicy.IDLE, emptyList(), all, tamperThisWeek = 2)
        assertEquals("All protections are on", model.protectionSummary)
        assertEquals("2 tamper events this week", model.tamperLine)
        assertEquals("No tamper events this week", FocusModel.build(now, zone, SessionPolicy.IDLE, emptyList(), all, 0).tamperLine)
    }

    @Test
    fun `battery optimisation counts as a missing protection`() {
        val model = FocusModel.build(now, zone, SessionPolicy.IDLE, emptyList(), all, 0, batteryExempt = false)
        assertEquals(listOf(Protection.BATTERY), model.protection.filterNot { it.on }.map { it.kind })
    }
}
