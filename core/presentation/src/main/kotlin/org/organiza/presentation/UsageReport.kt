package org.organiza.presentation

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One app's minutes on one day, with the category it declares. */
public data class AppMinutes(val label: String, val category: String, val minutes: Long)

public data class UsageBar(val label: String, val minutes: Long, val today: Boolean)

public data class CategoryShare(val category: String, val minutes: Long, val time: String, val fraction: Double)

public data class AppShare(val label: String, val category: String, val minutes: Long, val time: String, val fraction: Double)

/** The Metrics usage report, StayFree style: a bar a day over a range, then categories and apps by time. */
public data class UsageReport(
    val title: String,
    val total: String,
    val bars: List<UsageBar>,
    val categories: List<CategoryShare>,
    val apps: List<AppShare>,
) {
    public companion object {
        private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
        private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.UK)

        /** Longest range drawn, so a bar never gets thinner than a hairline. */
        public const val MAX_DAYS: Int = 62

        public fun build(days: Map<LocalDate, List<AppMinutes>>, from: LocalDate, to: LocalDate, today: LocalDate): UsageReport {
            val last = minOf(to, from.plusDays(MAX_DAYS - 1L))
            val range = (0..ChronoUnit.DAYS.between(from, last)).map { from.plusDays(it) }
            val all = range.flatMap { days[it].orEmpty() }
            val total = all.sumOf { it.minutes }
            val categories = all.groupBy { it.category }.map { (category, rows) -> category to rows.sumOf { it.minutes } }
                .filter { it.second > 0 }.sortedByDescending { it.second }
                .map { (category, minutes) -> CategoryShare(category, minutes, Format.usage(minutes, null), share(minutes, total)) }
            val apps = all.groupBy { it.label }.map { (label, rows) -> AppShare(label, rows.first().category, rows.sumOf { it.minutes }, "", 0.0) }
                .filter { it.minutes > 0 }.sortedByDescending { it.minutes }
                .map { it.copy(time = Format.usage(it.minutes, null), fraction = share(it.minutes, total)) }
            val title = when {
                from == last && from == today -> "Today"
                from == last && from == today.minusDays(1) -> "Yesterday"
                from == last -> from.format(DAY)
                else -> "${from.format(DAY)} – ${last.format(DAY)}"
            }
            val bars = range.map { day -> UsageBar(if (range.size <= 7) day.format(WEEKDAY) else day.dayOfMonth.toString(), days[day].orEmpty().sumOf { it.minutes }, day == today) }
            return UsageReport(title, Format.usage(total, null), bars, categories, apps)
        }

        private fun share(minutes: Long, total: Long): Double = if (total == 0L) 0.0 else minutes.toDouble() / total
    }
}
