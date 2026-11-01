package org.organiza.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The supported RRULE subset. Generates dates only, so local time survives DST changes. */
internal class Recurrence private constructor(
    private val frequency: Frequency,
    private val interval: Long,
    private val count: Int?,
    private val until: Instant?,
    private val weekdays: List<Weekday>,
    private val monthDays: List<Int>,
) {
    private enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    private data class Weekday(val day: DayOfWeek, val ordinal: Int?)

    /** True once the zero-based occurrence [index] at [start] falls beyond COUNT or UNTIL. */
    fun isPast(index: Int, start: Instant): Boolean =
        (count != null && index >= count) || (until != null && start > until)

    /** Every candidate date on or after [first], bounded to keep hostile rules finite. */
    fun dates(first: LocalDate): Sequence<LocalDate> = generateSequence(0L) { it + 1 }
        .take(MAX_PERIODS)
        .flatMap { period -> period(first, period * interval) }
        .filter { it >= first }

    private fun period(first: LocalDate, offset: Long): List<LocalDate> = when (frequency) {
        Frequency.DAILY -> listOf(first.plusDays(offset))
        Frequency.WEEKLY -> {
            val monday = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset)
            val days = weekdays.map(Weekday::day).ifEmpty { listOf(first.dayOfWeek) }
            days.map { monday.plusDays(it.value - 1L) }.sorted()
        }
        Frequency.MONTHLY -> monthDates(first, YearMonth.from(first).plusMonths(offset))
        Frequency.YEARLY -> {
            val month = YearMonth.of(first.year, first.month).plusYears(offset)
            listOfNotNull(month.takeIf { first.dayOfMonth <= it.lengthOfMonth() }?.atDay(first.dayOfMonth))
        }
    }

    private fun monthDates(first: LocalDate, month: YearMonth): List<LocalDate> = when {
        weekdays.isNotEmpty() -> weekdays.flatMap { weekdayDates(month, it) }.sorted()
        monthDays.isNotEmpty() -> monthDays.mapNotNull { day ->
            val resolved = if (day > 0) day else month.lengthOfMonth() + day + 1
            month.takeIf { resolved in 1..month.lengthOfMonth() }?.atDay(resolved)
        }.sorted()
        first.dayOfMonth <= month.lengthOfMonth() -> listOf(month.atDay(first.dayOfMonth))
        else -> emptyList()
    }

    private fun weekdayDates(month: YearMonth, weekday: Weekday): List<LocalDate> {
        val all = generateSequence(month.atDay(1).with(TemporalAdjusters.nextOrSame(weekday.day))) { it.plusWeeks(1) }
            .takeWhile { YearMonth.from(it) == month }
            .toList()
        val ordinal = weekday.ordinal ?: return all
        return listOfNotNull(all.getOrNull(if (ordinal > 0) ordinal - 1 else all.size + ordinal))
    }

    companion object {
        private const val MAX_PERIODS = 100_000
        private const val MAX_INTERVAL = 10_000L
        private val SUPPORTED = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY")
        private val WEEKDAY = Regex("""([+-]?\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)""")
        private val DAY_NAMES = mapOf(
            "MO" to DayOfWeek.MONDAY,
            "TU" to DayOfWeek.TUESDAY,
            "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY,
            "SA" to DayOfWeek.SATURDAY,
            "SU" to DayOfWeek.SUNDAY,
        )

        /** Returns null when the rule uses anything outside the supported subset. */
        fun parse(value: String, parseUntil: (String) -> Instant): Recurrence? {
            val parts = value.trim().split(';').filter(String::isNotEmpty).associate { part ->
                part.substringBefore('=').uppercase(Locale.ROOT) to part.substringAfter('=', "")
            }
            if (!SUPPORTED.containsAll(parts.keys)) return null
            val frequency = Frequency.entries.firstOrNull { it.name == parts["FREQ"]?.uppercase(Locale.ROOT) } ?: return null
            val interval = (parts["INTERVAL"] ?: "1").toLongOrNull()?.takeIf { it in 1..MAX_INTERVAL } ?: return null
            val count = parts["COUNT"]?.let { raw -> raw.toIntOrNull()?.takeIf { it > 0 } ?: return null }
            val weekdays = parts["BYDAY"]?.split(',')?.map { parseWeekday(it) ?: return null }.orEmpty()
            val monthDays = parts["BYMONTHDAY"]?.split(',')?.map { raw ->
                raw.toIntOrNull()?.takeIf { it != 0 && it in -31..31 } ?: return null
            }.orEmpty()
            val weekdaysAllowed = frequency == Frequency.WEEKLY && weekdays.all { it.ordinal == null } ||
                frequency == Frequency.MONTHLY && monthDays.isEmpty()
            if (weekdays.isNotEmpty() && !weekdaysAllowed) return null
            if (monthDays.isNotEmpty() && frequency != Frequency.MONTHLY) return null
            return Recurrence(frequency, interval, count, parts["UNTIL"]?.let(parseUntil), weekdays, monthDays)
        }

        private fun parseWeekday(raw: String): Weekday? {
            val match = WEEKDAY.matchEntire(raw.trim().uppercase(Locale.ROOT)) ?: return null
            val ordinal = match.groupValues[1].takeIf(String::isNotEmpty)?.let { text ->
                text.toIntOrNull()?.takeIf { it != 0 && it in -5..5 } ?: return null
            }
            return Weekday(DAY_NAMES.getValue(match.groupValues[2]), ordinal)
        }
    }
}
