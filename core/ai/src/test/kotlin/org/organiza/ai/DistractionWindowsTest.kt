package org.organiza.ai

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DistractionWindowsTest {
    private val zone = ZoneId.of("Europe/London")

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-11-%02dT%02d:%02d:00Z".format(day, hour, minute))

    @Test
    fun `counts land in the local hour`() {
        val summer = ZoneId.of("Europe/Berlin")
        val counts = DistractionWindows.byHour(listOf(at(2, 20, 30)), summer)
        assertEquals(1, counts[21])
        assertEquals(24, counts.size)
    }

    @Test
    fun `the busiest hours become windows, adjacent hours merged`() {
        val attempts = (1..5).flatMap { day -> listOf(at(day, 21), at(day, 22, 10), at(day, 9)) } + at(3, 14)
        val windows = DistractionWindows.find(DistractionWindows.byHour(attempts, zone), DistractionWindows.byHour(listOf(at(1, 21)), zone))
        assertEquals(listOf(DistractionWindow(21, 23, attempts = 10, overrides = 1), DistractionWindow(9, 10, attempts = 5, overrides = 0)), windows)
    }

    @Test
    fun `quiet hours are not windows`() {
        val windows = DistractionWindows.find(DistractionWindows.byHour(listOf(at(1, 9), at(2, 9)), zone), List(24) { 0 })
        assertEquals(emptyList(), windows)
    }

    @Test
    fun `at most three windows are kept, busiest first`() {
        val attempts = listOf(1, 5, 9, 13, 17).flatMapIndexed { index, hour -> List(3 + index) { at(1, hour) } }
        val windows = DistractionWindows.find(DistractionWindows.byHour(attempts, zone), List(24) { 0 })
        assertEquals(listOf(17, 13, 9), windows.map { it.startHour })
    }

    @Test
    fun `a window that crosses midnight is named by its local hours`() {
        val attempts = (1..3).flatMap { day -> listOf(at(day, 23), at(day, 0, 20)) }
        val windows = DistractionWindows.find(DistractionWindows.byHour(attempts, zone), List(24) { 0 })
        assertEquals(listOf(DistractionWindow(23, 1, attempts = 6, overrides = 0)), windows)
        assertEquals("23:00 to 01:00", windows.single().label)
    }
}
