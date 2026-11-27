package org.organiza.presentation

import java.time.LocalDate
import kotlin.math.roundToInt

/** [open] while the habit's goal for its period is not met; a weekly one met by Wednesday is not open on Thursday. */
public data class WidgetHabit(val id: String, val name: String, val doneToday: Boolean, val open: Boolean = !doneToday)

/**
 * What the home screen widgets show (ADR-0039): today's progress, the habits still open first, screen
 * time, the next focus block, and a streak with a nudge in the spirit of a language app's reminders.
 */
public data class HomeWidget(
    val percent: String,
    val headline: String,
    val habits: List<WidgetHabit>,
    val screenTime: String,
    val next: String?,
    val streak: Int,
    val nudge: String,
) {
    public companion object {
        /** [streak] is [currentStreak] over every habit's whole history; Home's own window is too short for it. */
        public fun build(today: LocalDate, home: HomeModel, screenTime: String, next: String?, streak: Int): HomeWidget {
            val habits = home.habits.map { WidgetHabit(it.id, it.name, it.progress.doneToday, !it.progress.met) }.sortedBy { !it.open }
            val metToday = home.habits.any { it.progress.doneToday }
            val open = habits.firstOrNull { it.open }
            val nudge = when {
                habits.isEmpty() -> "Add a habit to start a streak"
                open == null -> "All done today. $streak-day streak kept"
                streak > 0 && !metToday -> "Keep your $streak-day streak: ${open.name} is still open today"
                streak > 0 -> "$streak-day streak. Still open: ${open.name}"
                else -> "Start a streak today: ${open.name}"
            }
            return HomeWidget("${(home.completion * 100).roundToInt()}%", home.headline, habits, "$screenTime today", next, streak, nudge)
        }

        /** Days in a row, up to today, with any habit met. Today still counts until it ends, so an open today starts from yesterday. */
        public fun currentStreak(today: LocalDate, results: List<Map<LocalDate, Boolean>>): Int {
            val metOn = { day: LocalDate -> results.any { it[day] == true } }
            val from = if (metOn(today)) today else today.minusDays(1)
            return generateSequence(from) { it.minusDays(1) }.takeWhile(metOn).count()
        }
    }
}

/**
 * What a reminder says, or null when it should stay quiet (ADR-0039): never for a habit already done today,
 * a word about the streak when there is one, and an evening check-in only while something is still open.
 */
public object ReminderText {
    public const val EVENING: String = "evening"
    public const val NOTE: String = "note-"

    public fun of(id: String, name: String, today: HomeWidget?): String? = when {
        id.startsWith(NOTE) -> "A reminder from your notes"
        id == EVENING -> today?.takeIf { widget -> widget.habits.any { it.open } }?.nudge
        today?.habits?.any { it.name == name && !it.open } == true -> null
        today != null && today.streak > 0 -> "Keep your ${today.streak}-day streak going. Mark it done in Organiza"
        else -> "Time for this habit. Mark it done in Organiza"
    }
}
