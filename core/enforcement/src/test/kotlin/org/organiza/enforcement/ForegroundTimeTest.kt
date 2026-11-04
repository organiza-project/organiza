package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.enforcement.UsageEvent.Paused
import org.organiza.enforcement.UsageEvent.Resumed
import org.organiza.enforcement.UsageEvent.ScreenOff

class ForegroundTimeTest {
    private val insta = pkg("com.instagram.android")
    private val yt = pkg("com.google.android.youtube")
    private val t0 = Instant.parse("2026-11-04T10:00:00Z")

    private fun at(minutes: Long) = t0.plus(Duration.ofMinutes(minutes))

    private fun used(events: List<UsageEvent>, from: Instant = t0, to: Instant = at(600)) = ForegroundTime.perApp(events, from, to)

    @Test
    fun `a resume and its pause count once`() {
        assertEquals(mapOf(insta to Duration.ofMinutes(5)), used(listOf(Resumed(insta, "Main", at(0)), Paused(insta, "Main", at(5)))))
    }

    @Test
    fun `a stop after the pause adds nothing`() {
        // The old reader counted every stop from the start of the day.
        val events = listOf(Resumed(insta, "Main", at(1)), Paused(insta, "Main", at(3)), UsageEvent.Stopped(insta, "Main", at(4)))
        assertEquals(Duration.ofMinutes(2), used(events)[insta])
    }

    @Test
    fun `moving between activities of one app is one stretch`() {
        val events = listOf(
            Resumed(insta, "Feed", at(0)),
            Paused(insta, "Feed", at(4)),
            Resumed(insta, "Story", at(4)),
            UsageEvent.Stopped(insta, "Feed", at(5)),
            Paused(insta, "Story", at(9)),
        )
        assertEquals(Duration.ofMinutes(9), used(events)[insta])
    }

    @Test
    fun `turning the screen off ends what is open`() {
        val events = listOf(Resumed(yt, "Watch", at(0)), ScreenOff(at(20)), Resumed(insta, "Main", at(30)), Paused(insta, "Main", at(31)))
        assertEquals(mapOf(yt to Duration.ofMinutes(20), insta to Duration.ofMinutes(1)), used(events))
    }

    @Test
    fun `time is clipped to the window, and an app still open counts up to its end`() {
        val events = listOf(Resumed(insta, "Main", at(-30)), Paused(insta, "Main", at(10)), Resumed(yt, "Watch", at(50)))
        assertEquals(mapOf(insta to Duration.ofMinutes(10), yt to Duration.ofMinutes(10)), used(events, to = at(60)))
    }

    @Test
    fun `a pause with no resume before it counts nothing`() {
        assertEquals(emptyMap(), used(listOf(Paused(insta, "Main", at(5)))))
    }

    @Test
    fun `events out of order are sorted first`() {
        val events = listOf(Paused(insta, "Main", at(5)), Resumed(insta, "Main", at(2)))
        assertEquals(Duration.ofMinutes(3), used(events)[insta])
    }
}
