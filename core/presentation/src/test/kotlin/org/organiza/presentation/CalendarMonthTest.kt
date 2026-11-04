package org.organiza.presentation

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarMonthTest {
    private val today = LocalDate.parse("2026-11-04")

    @Test
    fun `a month is whole weeks from monday, with the days around it marked`() {
        val month = CalendarMonth.build(YearMonth.of(2026, 11), today, selected = today, blocks = emptyMap())
        assertEquals("November 2026", month.title)
        assertEquals(6, month.weeks.size) // 1 November 2026 is a Sunday
        assertEquals(LocalDate.parse("2026-10-26"), month.weeks.first().first().date)
        assertEquals(LocalDate.parse("2026-12-06"), month.weeks.last().last().date)
        assertEquals(false, month.weeks.first().first().inMonth)
        assertEquals(30, month.weeks.flatten().count { it.inMonth })
    }

    @Test
    fun `today, the selected day and each day's blocks are marked`() {
        val picked = LocalDate.parse("2026-11-12")
        val month = CalendarMonth.build(YearMonth.of(2026, 11), today, picked, mapOf(picked to 2, today to 1))
        val days = month.weeks.flatten().associateBy { it.date }
        assertEquals(CalendarDay(today, inMonth = true, isToday = true, selected = false, blocks = 1), days[today])
        assertEquals(CalendarDay(picked, inMonth = true, isToday = false, selected = true, blocks = 2), days[picked])
    }

    @Test
    fun `the week of a day starts on its monday`() {
        assertEquals((2..8).map { LocalDate.of(2026, 11, it) }, CalendarMonth.weekOf(today))
    }
}
