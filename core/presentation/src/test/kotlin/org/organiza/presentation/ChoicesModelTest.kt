package org.organiza.presentation

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.ai.DistractionWindow
import org.organiza.domain.DomainName
import org.organiza.domain.OverrideReason
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget
import org.organiza.storage.InterventionChoice
import org.organiza.storage.StoredIntervention

class ChoicesModelTest {
    private val zone = ZoneOffset.UTC
    private val instagram = (PackageName.parse("com.instagram.android") as Parsed.Valid).value
    private val chrome = (PackageName.parse("com.android.chrome") as Parsed.Valid).value
    private val reddit = (DomainName.parse("reddit.com") as Parsed.Valid).value
    private val names = mapOf(instagram to "Instagram", chrome to "Chrome")

    private fun event(time: String, target: BlockTarget, overrode: Boolean = false, reason: OverrideReason? = null, note: String? = null) =
        StoredIntervention(
            sessionId = null,
            target = target,
            choice = if (overrode) InterventionChoice.OVERRODE else InterventionChoice.STAYED,
            reason = reason,
            note = note,
            phraseId = null,
            pause = Duration.ofSeconds(10),
            passMinutes = if (overrode) 5 else null,
            strict = false,
            reflected = false,
            at = Instant.parse("2026-11-02T${time}:00Z"),
        )

    private fun build(events: List<StoredIntervention>, windows: List<DistractionWindow> = emptyList()) =
        ChoicesModel.build(events, zone, { names[it] ?: it.value }, windows)

    @Test
    fun `a quiet day says so`() {
        assertEquals("No blocked attempts today", build(emptyList()).headline)
    }

    @Test
    fun `the headline counts focus kept against attempts`() {
        val model = build(
            listOf(
                event("09:00", BlockTarget.App(instagram)),
                event("09:30", BlockTarget.App(instagram)),
                event("21:15", BlockTarget.Site(chrome, reddit), overrode = true, reason = OverrideReason.WORK, note = "thread for a client"),
            ),
        )
        assertEquals("You stayed focused 2 of 3 times today", model.headline)
        assertEquals(2, model.stayed)
        assertEquals(1, model.overrode)
    }

    @Test
    fun `rows read newest first, with the reason for overrides`() {
        val model = build(
            listOf(
                event("09:00", BlockTarget.App(instagram)),
                event("21:15", BlockTarget.Site(chrome, reddit), overrode = true, reason = OverrideReason.WORK, note = "thread for a client"),
                event("22:00", BlockTarget.ShortForm(instagram), overrode = true, reason = OverrideReason.BOREDOM),
            ),
        )
        assertEquals(
            listOf(
                ChoiceRow("22:00", "Short videos in Instagram", "Opened for 5 min", "Boredom"),
                ChoiceRow("21:15", "reddit.com", "Opened for 5 min", "Work: thread for a client"),
                ChoiceRow("09:00", "Instagram", "Stayed focused", null),
            ),
            model.rows,
        )
    }

    @Test
    fun `one stayed attempt reads naturally`() {
        assertEquals("You stayed focused the 1 time today", build(listOf(event("09:00", BlockTarget.App(instagram)))).headline)
    }

    @Test
    fun `the guard is named for what it protects`() {
        val settings = (PackageName.parse("com.android.settings") as Parsed.Valid).value
        assertEquals("Organiza's settings", build(listOf(event("09:00", BlockTarget.Guard(settings)))).rows.single().target)
    }

    @Test
    fun `hard hours are listed from the local analysis`() {
        val model = build(emptyList(), listOf(DistractionWindow(21, 23, 10, 3), DistractionWindow(9, 10, 4, 0)))
        assertEquals(listOf("21:00 to 23:00: 10 attempts, 3 opened", "09:00 to 10:00: 4 attempts, none opened"), model.windows)
    }

    @Test
    fun `only the latest ten rows are shown`() {
        val events = (0 until 14).map { event("%02d:00".format(it + 6), BlockTarget.App(instagram)) }
        assertEquals(ChoicesModel.MAX_ROWS, build(events).rows.size)
        assertEquals("19:00", build(events).rows.first().time)
    }
}
