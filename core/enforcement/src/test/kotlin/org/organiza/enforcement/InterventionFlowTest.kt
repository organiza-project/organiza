package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.organiza.domain.OverrideReason
import org.organiza.domain.port.BlockTarget
import org.organiza.testing.FakeClock

class InterventionFlowTest {
    private val clock = FakeClock(Instant.parse("2026-11-02T10:00:00Z"))
    private val app = BlockTarget.App(pkg("com.instagram.android"))
    private val phrase = Phrase("p1", "I choose where my attention goes.")

    private fun flow(strict: Boolean = false, reflect: Boolean = false, target: BlockTarget = app, overrides: Int = 0) =
        InterventionFlow(target, strict, overrides, phrase, reflect, clock)

    private fun InterventionFlow.typeSlowly(text: String) {
        var typed = ""
        text.split(' ').forEach { word ->
            typed = if (typed.isEmpty()) word else "$typed $word"
            type(typed)
        }
    }

    @Test
    fun `it opens on the choice, and staying focused ends it`() {
        val flow = flow()
        assertEquals(InterventionStep.Choice, flow.step)
        assertEquals(InterventionOutcome.Stayed, flow.stayFocused())
        assertEquals(InterventionStep.Done, flow.step)
    }

    @Test
    fun `the pause cannot be skipped`() {
        val flow = flow()
        flow.beginOverride()
        assertEquals(InterventionStep.Pause(clock.now().plusSeconds(10)), flow.step)
        clock.advance(Duration.ofSeconds(9))
        flow.tick()
        assertIs<InterventionStep.Pause>(flow.step)
        assertFalse(flow.chooseReason(OverrideReason.WORK, ""))
        clock.advance(Duration.ofSeconds(1))
        flow.tick()
        assertEquals(InterventionStep.Reason, flow.step)
    }

    @Test
    fun `a full override grants the chosen minutes and records the details`() {
        val flow = flow(overrides = 1)
        flow.beginOverride()
        clock.advance(Duration.ofSeconds(20))
        flow.tick()
        assertTrue(flow.chooseReason(OverrideReason.WORK, "  reply to Sam  "))
        assertEquals(InterventionStep.Type, flow.step)
        flow.typeSlowly("I choose where my attention goes")
        assertTrue(flow.submitPhrase())
        assertEquals(InterventionStep.Length, flow.step)
        val outcome = flow.choose(5)
        assertEquals(
            InterventionOutcome.Overrode(5, OverrideReason.WORK, "reply to Sam", Duration.ofSeconds(20), reflected = false),
            outcome,
        )
        assertEquals(InterventionStep.Done, flow.step)
    }

    @Test
    fun `a wrong phrase is refused and the step stays`() {
        val flow = flow()
        flow.beginOverride()
        clock.advance(Duration.ofSeconds(10))
        flow.tick()
        flow.chooseReason(OverrideReason.BOREDOM, "")
        flow.typeSlowly("I choose where attention goes")
        assertFalse(flow.submitPhrase())
        assertEquals(InterventionStep.Type, flow.step)
    }

    @Test
    fun `pasting wipes what was typed`() {
        val flow = flow()
        flow.beginOverride()
        clock.advance(Duration.ofSeconds(10))
        flow.tick()
        flow.chooseReason(OverrideReason.HABIT, "")
        assertFalse(flow.type("I choose where my attention goes."))
        assertEquals("", flow.typed)
        assertFalse(flow.submitPhrase())
    }

    @Test
    fun `only the offered lengths can be chosen`() {
        val flow = flow()
        flow.beginOverride()
        clock.advance(Duration.ofSeconds(10))
        flow.tick()
        flow.chooseReason(OverrideReason.ERRAND, "")
        flow.typeSlowly("I choose where my attention goes")
        flow.submitPhrase()
        assertNull(flow.choose(60))
        assertEquals(InterventionStep.Length, flow.step)
        assertEquals(listOf(1, 5, 15), InterventionFlow.LENGTHS)
    }

    @Test
    fun `strict with AI shows the reflection before the phrase`() {
        val flow = flow(strict = true, reflect = true)
        flow.beginOverride()
        clock.advance(Duration.ofSeconds(30))
        flow.tick()
        flow.chooseReason(OverrideReason.COMMUNICATION, "need to answer a message")
        assertEquals(InterventionStep.Reflect(null), flow.step)
        assertFalse(flow.continueAfterReflection())
        flow.showReflection("A message can often wait 20 minutes.")
        assertEquals(InterventionStep.Reflect("A message can often wait 20 minutes."), flow.step)
        assertTrue(flow.continueAfterReflection())
        assertEquals(InterventionStep.Type, flow.step)
        flow.typeSlowly("I choose where my attention goes")
        flow.submitPhrase()
        assertEquals(true, (flow.choose(1) as InterventionOutcome.Overrode).reflected)
    }

    @Test
    fun `a failed reflection still lets the person continue`() {
        val flow = flow(strict = true, reflect = true)
        flow.beginOverride()
        clock.advance(Duration.ofSeconds(30))
        flow.tick()
        flow.chooseReason(OverrideReason.OTHER, "")
        flow.showReflection(null)
        assertEquals(InterventionStep.Reflect(InterventionFlow.LOCAL_REFLECTION), flow.step)
        assertTrue(flow.continueAfterReflection())
        flow.typeSlowly("I choose where my attention goes")
        flow.submitPhrase()
        assertEquals(false, (flow.choose(1) as InterventionOutcome.Overrode).reflected)
    }

    @Test
    fun `the settings guard offers no override`() {
        val flow = flow(target = BlockTarget.Guard(pkg("com.android.settings")))
        assertFalse(flow.canOverride)
        flow.beginOverride()
        assertEquals(InterventionStep.Choice, flow.step)
        assertEquals(InterventionOutcome.Stayed, flow.stayFocused())
    }

    @Test
    fun `staying focused is possible at every step`() {
        val flow = flow()
        flow.beginOverride()
        clock.advance(Duration.ofSeconds(10))
        flow.tick()
        flow.chooseReason(OverrideReason.WORK, "")
        assertEquals(InterventionOutcome.Stayed, flow.stayFocused())
        assertNull(flow.choose(5))
    }

    @Test
    fun `notes are cut to a sane length`() {
        val flow = flow()
        flow.beginOverride()
        clock.advance(Duration.ofSeconds(10))
        flow.tick()
        flow.chooseReason(OverrideReason.OTHER, "x".repeat(1000))
        flow.typeSlowly("I choose where my attention goes")
        flow.submitPhrase()
        assertEquals(InterventionFlow.MAX_NOTE, (flow.choose(1) as InterventionOutcome.Overrode).note.length)
    }
}
