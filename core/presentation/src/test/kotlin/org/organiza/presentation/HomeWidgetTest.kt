package org.organiza.presentation

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.storage.HabitPlan

class HomeWidgetTest {
    private val today = LocalDate.parse("2026-11-27")
    private fun days(vararg back: Long) = back.associate { today.minusDays(it) to true }
    private fun widget(vararg habits: HomeHabitInput) = HomeWidget.build(today, habits.toList(), HomeModel.build(today, habits.toList(), 10), "1h 05m", "Focus at 14:00")

    @Test
    fun `open habits come first, with progress, screen time and the next block`() {
        val model = widget(
            HomeHabitInput("a", "Read", HabitPlan.DAILY, days(0)),
            HomeHabitInput("b", "Run", HabitPlan.DAILY, emptyMap()),
            HomeHabitInput("c", "Stretch", HabitPlan.DAILY, emptyMap()),
            HomeHabitInput("d", "Water", HabitPlan.DAILY, emptyMap()),
            HomeHabitInput("e", "Walk", HabitPlan.DAILY, emptyMap()),
        )
        assertEquals(listOf("Run", "Stretch", "Water", "Walk", "Read"), model.habits.map { it.name })
        assertEquals("20%", model.percent)
        assertEquals("1h 05m today", model.screenTime)
        assertEquals("Focus at 14:00", model.next)
    }

    @Test
    fun `the streak counts days in a row with a habit met, and the nudge fits the day`() {
        val keep = widget(HomeHabitInput("a", "Read", HabitPlan.DAILY, days(1, 2, 3)))
        assertEquals(3, keep.streak)
        assertEquals("Keep your 3-day streak: Read is still open today", keep.nudge)
        val kept = widget(HomeHabitInput("a", "Read", HabitPlan.DAILY, days(0, 1, 2, 3)))
        assertEquals(4, kept.streak)
        assertEquals("All done today. 4-day streak kept", kept.nudge)
        val start = widget(HomeHabitInput("a", "Read", HabitPlan.DAILY, days(2)))
        assertEquals(0, start.streak)
        assertEquals("Start a streak today: Read", start.nudge)
        assertEquals("Add a habit to start a streak", widget().nudge)
    }

    @Test
    fun `a reminder says nothing for a habit already done, and keeps the streak in mind otherwise`() {
        val day = widget(HomeHabitInput("a", "Read", HabitPlan.DAILY, days(0, 1)), HomeHabitInput("b", "Run", HabitPlan.DAILY, days(1)))
        assertEquals(null, ReminderText.of("habit-a", "Read", day))
        assertEquals("Keep your 2-day streak going. Mark it done in Organiza", ReminderText.of("habit-b", "Run", day))
        assertEquals("Time for this habit. Mark it done in Organiza", ReminderText.of("habit-b", "Run", null))
        assertEquals("A reminder from your notes", ReminderText.of("note-1", "Call the bank", day))
        // The evening check-in speaks only while something is open, in the streak widget's words.
        assertEquals("2-day streak. Still open: Run", ReminderText.of(ReminderText.EVENING, "Evening check-in", day))
        val allDone = widget(HomeHabitInput("a", "Read", HabitPlan.DAILY, days(0)))
        assertEquals(null, ReminderText.of(ReminderText.EVENING, "Evening check-in", allDone))
    }
}
