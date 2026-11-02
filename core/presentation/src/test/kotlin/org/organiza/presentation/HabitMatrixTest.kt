package org.organiza.presentation

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HabitMatrixTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun `thirty days end today and today is the last column`() {
        val matrix = HabitMatrix.build(today, emptyList())
        assertEquals(30, matrix.days.size)
        assertEquals(today, matrix.days.last())
        assertEquals(today.minusDays(29), matrix.days.first())
    }

    @Test
    fun `cells show met missed open and before the habit existed`() {
        val habit = HabitInput(
            id = "h",
            name = "Read",
            created = today.minusDays(3),
            results = mapOf(today.minusDays(3) to true, today.minusDays(2) to false),
        )
        val row = HabitMatrix.build(today, listOf(habit)).rows.single()
        assertEquals(
            listOf(Cell.MET, Cell.MISSED, Cell.UNMARKED_PAST, Cell.TODAY_OPEN),
            row.cells.takeLast(4),
        )
        assertEquals(Cell.NOT_STARTED, row.cells.first())
    }

    @Test
    fun `the streak counts back from today or yesterday`() {
        val results = (1..4).associate { today.minusDays(it.toLong()) to true }
        val open = HabitInput("h", "Read", today.minusDays(10), results)
        assertEquals(4, HabitMatrix.build(today, listOf(open)).rows.single().streak)
        val doneToday = open.copy(results = results + (today to true))
        assertEquals(5, HabitMatrix.build(today, listOf(doneToday)).rows.single().streak)
        val broken = open.copy(results = results + (today.minusDays(2) to false))
        assertEquals(1, HabitMatrix.build(today, listOf(broken)).rows.single().streak)
    }

    @Test
    fun `the counts cover only days the habit existed`() {
        val habit = HabitInput("h", "Read", today.minusDays(4), mapOf(today.minusDays(1) to true))
        val row = HabitMatrix.build(today, listOf(habit)).rows.single()
        assertEquals(1, row.met)
        assertEquals(5, row.days)
    }

    @Test
    fun `the finding names the longest streak and the latest miss`() {
        val read = HabitInput("a", "Read", today.minusDays(9), (1..3).associate { today.minusDays(it.toLong()) to true })
        val stretch = HabitInput("b", "Stretch", today.minusDays(9), mapOf(today.minusDays(1) to false))
        assertEquals("Read: 3-day streak. Stretch was missed yesterday", HabitMatrix.build(today, listOf(read, stretch)).finding)
        assertEquals("No habits yet", HabitMatrix.build(today, emptyList()).finding)
    }
}
