package org.organiza.presentation

import java.time.LocalDate
import java.time.temporal.ChronoUnit

public data class HabitInput(val id: String, val name: String, val created: LocalDate, val results: Map<LocalDate, Boolean>)

public enum class Cell { NOT_STARTED, MET, MISSED, UNMARKED_PAST, TODAY_OPEN }

public data class HabitRow(
    val id: String,
    val name: String,
    val cells: List<Cell>,
    val streak: Int,
    val met: Int,
    val days: Int,
)

/** Thirty days of every habit, ending today (ADR-0012). */
public data class HabitMatrix(val days: List<LocalDate>, val rows: List<HabitRow>, val finding: String) {
    public companion object {
        public const val DAYS: Int = 30

        public fun build(today: LocalDate, habits: List<HabitInput>): HabitMatrix {
            val days = (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
            val rows = habits.map { habit -> row(habit, days, today) }
            return HabitMatrix(days, rows, finding(today, habits, rows))
        }

        private fun row(habit: HabitInput, days: List<LocalDate>, today: LocalDate): HabitRow {
            val cells = days.map { day ->
                val result = habit.results[day]
                when {
                    day.isBefore(habit.created) -> Cell.NOT_STARTED
                    result == true -> Cell.MET
                    result == false -> Cell.MISSED
                    day == today -> Cell.TODAY_OPEN
                    else -> Cell.UNMARKED_PAST
                }
            }
            val firstCounted = maxOf(habit.created, days.first())
            return HabitRow(
                id = habit.id,
                name = habit.name,
                cells = cells,
                streak = streak(habit, today),
                met = days.count { !it.isBefore(firstCounted) && habit.results[it] == true },
                days = (ChronoUnit.DAYS.between(firstCounted, today) + 1).toInt().coerceAtLeast(0),
            )
        }

        /** Today counts once it is marked; until then the streak runs from yesterday. */
        private fun streak(habit: HabitInput, today: LocalDate): Int {
            var day = if (habit.results[today] == true) today else today.minusDays(1)
            var streak = 0
            while (habit.results[day] == true) {
                streak++
                day = day.minusDays(1)
            }
            return streak
        }

        private fun finding(today: LocalDate, habits: List<HabitInput>, rows: List<HabitRow>): String {
            if (habits.isEmpty()) return "No habits yet"
            val best = rows.filter { it.streak > 0 }.maxByOrNull { it.streak }
            val miss = habits.flatMap { habit -> habit.results.filterValues { !it }.keys.map { habit.name to it } }
                .maxByOrNull { it.second }
            val parts = listOfNotNull(
                best?.let { "${it.name}: ${it.streak}-day streak" },
                miss?.let { (name, day) -> "$name was missed ${relative(day, today)}" },
            )
            return parts.joinToString(". ").ifEmpty { "Nothing marked yet this month" }
        }

        private fun relative(day: LocalDate, today: LocalDate) = when (day) {
            today -> "today"
            today.minusDays(1) -> "yesterday"
            else -> "on ${Format.dayMonth(day)}"
        }
    }
}
