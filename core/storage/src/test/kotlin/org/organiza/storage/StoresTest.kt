package org.organiza.storage

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.Capability
import org.organiza.testing.JdbcDatabase

class StoresTest {
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val rules = RuleStore(db)
    private val sessions = SessionStore(db)
    private val tamper = TamperStore(db)
    private val at = Instant.parse("2026-11-01T10:00:00Z")

    private fun pkg(raw: String) = (PackageName.parse(raw) as Parsed.Valid).value
    private fun domain(raw: String) = (DomainName.parse(raw) as Parsed.Valid).value

    private val rule = StoredRule(
        id = "r1",
        name = "Deep work",
        calendarTag = "#focus",
        strict = true,
        blockShortForm = true,
        enabled = true,
        packages = setOf(pkg("com.instagram.android")),
        domains = setOf(domain("reddit.com")),
    )

    private fun session(id: String = "s1", state: SessionState = SessionState.ACTIVE) = StoredSession(
        id = id,
        ruleId = "r1",
        source = SessionSource.MANUAL,
        state = state,
        calendarEventUid = null,
        start = at,
        end = at.plusSeconds(3600),
        finishedAt = null,
    )

    @Test
    fun `rules round trip with their targets`() {
        rules.save(rule, at)
        assertEquals(rule, rules.find("r1"))
        assertEquals(listOf(rule), rules.all())
    }

    @Test
    fun `saving replaces targets rather than merging`() {
        rules.save(rule, at)
        val changed = rule.copy(packages = setOf(pkg("com.other.app")), domains = emptySet())
        rules.save(changed, at)
        assertEquals(changed, rules.find("r1"))
    }

    @Test
    fun `missing rules are null`() {
        assertEquals(null, rules.find("nope"))
    }

    @Test
    fun `deleting a rule leaves session history`() {
        rules.save(rule, at)
        sessions.insert(session())
        rules.delete("r1")
        assertEquals(null, sessions.find("s1")?.ruleId)
    }

    @Test
    fun `sessions round trip`() {
        rules.save(rule, at)
        sessions.insert(session())
        assertEquals(session(), sessions.find("s1"))
    }

    @Test
    fun `sessions can be listed by state`() {
        rules.save(rule, at)
        sessions.insert(session("a", SessionState.ACTIVE))
        sessions.insert(session("b", SessionState.SCHEDULED))
        assertEquals(listOf("a"), sessions.inState(SessionState.ACTIVE).map { it.id })
    }

    @Test
    fun `finishing records state and time`() {
        rules.save(rule, at)
        sessions.insert(session())
        sessions.finish("s1", SessionState.COMPLETED, at.plusSeconds(10))
        val stored = sessions.find("s1")
        assertEquals(SessionState.COMPLETED, stored?.state)
        assertEquals(at.plusSeconds(10), stored?.finishedAt)
    }

    @Test
    fun `granted capabilities round trip and start unknown`() {
        rules.save(rule, at)
        sessions.insert(session())
        assertEquals(null, sessions.grantedCapabilities("s1"))
        sessions.saveGrantedCapabilities(setOf(Capability.VPN, Capability.ACCESSIBILITY))
        assertEquals(setOf(Capability.VPN, Capability.ACCESSIBILITY), sessions.grantedCapabilities("s1"))
        sessions.saveGrantedCapabilities(emptySet())
        assertEquals(emptySet(), sessions.grantedCapabilities("s1"))
    }

    @Test
    fun `tamper events persist in order`() {
        rules.save(rule, at)
        sessions.insert(session())
        tamper.record("s1", Capability.VPN, at)
        tamper.record(null, Capability.DEVICE_ADMIN, at.plusSeconds(5))
        assertEquals(
            listOf(
                StoredTamperEvent("s1", Capability.VPN, at),
                StoredTamperEvent(null, Capability.DEVICE_ADMIN, at.plusSeconds(5)),
            ),
            tamper.all(),
        )
    }
}
