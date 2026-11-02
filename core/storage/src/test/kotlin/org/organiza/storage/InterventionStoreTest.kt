package org.organiza.storage

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.organiza.domain.DomainName
import org.organiza.domain.OverrideReason
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget
import org.organiza.testing.JdbcDatabase

class InterventionStoreTest {
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val store = InterventionStore(db)
    private val at = Instant.parse("2026-11-02T10:00:00Z")
    private val chrome = (PackageName.parse("com.android.chrome") as Parsed.Valid).value
    private val instagram = (PackageName.parse("com.instagram.android") as Parsed.Valid).value
    private val reddit = (DomainName.parse("reddit.com") as Parsed.Valid).value

    private val stayed = StoredIntervention(
        sessionId = null,
        target = BlockTarget.App(instagram),
        choice = InterventionChoice.STAYED,
        reason = null,
        note = null,
        phraseId = null,
        pause = Duration.ZERO,
        passMinutes = null,
        strict = false,
        reflected = false,
        at = at,
    )

    private val overrode = StoredIntervention(
        sessionId = null,
        target = BlockTarget.Site(chrome, reddit),
        choice = InterventionChoice.OVERRODE,
        reason = OverrideReason.WORK,
        note = "check a thread for work",
        phraseId = "s04",
        pause = Duration.ofSeconds(20),
        passMinutes = 5,
        strict = true,
        reflected = true,
        at = at.plusSeconds(60),
    )

    @Test
    fun `both outcomes round trip in time order`() {
        store.record(overrode)
        store.record(stayed)
        assertEquals(listOf(stayed, overrode), store.between(at, at.plusSeconds(3600)))
    }

    @Test
    fun `every target kind round trips`() {
        val targets = listOf(BlockTarget.App(instagram), BlockTarget.Site(chrome, reddit), BlockTarget.ShortForm(instagram), BlockTarget.Guard(chrome))
        targets.forEachIndexed { index, target -> store.record(stayed.copy(target = target, at = at.plusSeconds(index.toLong()))) }
        assertEquals(targets, store.between(at, at.plusSeconds(10)).map { it.target })
    }

    @Test
    fun `the window is half open`() {
        store.record(stayed)
        assertEquals(emptyList(), store.between(at.plusSeconds(1), at.plusSeconds(10)))
        assertEquals(emptyList(), store.between(at.minusSeconds(10), at))
    }

    @Test
    fun `overrides are counted from a given instant`() {
        store.record(stayed)
        store.record(overrode)
        store.record(overrode.copy(at = at.minusSeconds(86_400)))
        assertEquals(1, store.overridesSince(at))
    }

    @Test
    fun `an override without a reason is refused by the schema`() {
        assertFailsWith<Exception> { store.record(overrode.copy(reason = null)) }
    }

    @Test
    fun `a pass longer than fifteen minutes is refused by the schema`() {
        assertFailsWith<Exception> { store.record(overrode.copy(passMinutes = 60)) }
    }
}
