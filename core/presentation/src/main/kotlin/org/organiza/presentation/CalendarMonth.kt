package org.organiza.presentation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

public data class CalendarDay(val date: LocalDate, val inMonth: Boolean, val isToday: Boolean, val selected: Boolean, val blocks: Int)

/** A month as whole weeks from Monday, for the calendar's month view (ADR-0019). */
public data class CalendarMonth(val title: String, val weeks: List<List<CalendarDay>>) {
    public companion object {
        public fun build(month: YearMonth, today: LocalDate, selected: LocalDate, blocks: Map<LocalDate, Int>): CalendarMonth {
            val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { date ->
                CalendarDay(date, YearMonth.from(date) == month, date == today, date == selected, blocks[date] ?: 0)
            }
            val title = "${month.month.getDisplayName(TextStyle.FULL, Locale.UK)} ${month.year}"
            return CalendarMonth(title, days.chunked(7).toList())
        }

        public fun weekOf(day: LocalDate): List<LocalDate> {
            val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return (0L until 7L).map(monday::plusDays)
        }
    }
}
