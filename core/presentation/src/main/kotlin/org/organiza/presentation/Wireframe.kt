package org.organiza.presentation

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.sqrt
import org.organiza.storage.HabitFrequency
import org.organiza.storage.HabitPlan
import org.organiza.storage.SessionState
import org.organiza.storage.StoredSession

/** How far a habit is through its current day, week or month (ADR-0018). */
public data class PeriodProgress(val done: Int, val target: Int, val doneToday: Boolean, val label: String) {
    val met: Boolean get() = done >= target
    val fraction: Double get() = (done.toDouble() / target).coerceAtMost(1.0)
}

public object HabitPeriods {
    /** Weeks start on Monday, months on the first. */
    public fun start(frequency: HabitFrequency, today: LocalDate): LocalDate = when (frequency) {
        HabitFrequency.DAILY -> today
        HabitFrequency.WEEKLY -> today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        HabitFrequency.MONTHLY -> today.withDayOfMonth(1)
    }

    public fun progress(plan: HabitPlan, results: Map<LocalDate, Boolean>, today: LocalDate): PeriodProgress {
        val from = start(plan.frequency, today)
        val done = results.count { (day, success) -> success && !day.isBefore(from) && !day.isAfter(today) }
        val doneToday = results[today] == true
        val label = when (plan.frequency) {
            HabitFrequency.DAILY -> if (doneToday) "Done today" else "Not done today"
            HabitFrequency.WEEKLY -> "$done of ${plan.target} this week"
            HabitFrequency.MONTHLY -> "$done of ${plan.target} this month"
        }
        return PeriodProgress(done, plan.target, doneToday, label)
    }
}

public data class HomeHabitInput(val id: String, val name: String, val plan: HabitPlan, val results: Map<LocalDate, Boolean>)

public data class HomeHabit(val id: String, val name: String, val progress: PeriodProgress)

/** The Home dial and its cards: how many of today's goals are met. */
public data class HomeModel(val completion: Double, val headline: String, val habits: List<HomeHabit>) {
    public companion object {
        public fun build(today: LocalDate, habits: List<HomeHabitInput>): HomeModel {
            val rows = habits.map { HomeHabit(it.id, it.name, HabitPeriods.progress(it.plan, it.results, today)) }
            // A daily habit counts once it is done today; a weekly or monthly one once its target is met.
            val met = rows.count { it.progress.met }
            val headline = if (rows.isEmpty()) "No habits yet" else "$met of ${rows.size} goals met"
            return HomeModel(if (rows.isEmpty()) 0.0 else met.toDouble() / rows.size, headline, rows)
        }
    }
}

public data class AgendaBlock(val id: String, val label: String, val time: String, val top: Double, val bottom: Double, val kind: BarKind)

/** One day as an hour grid from 06:00 to midnight. [now] is null on other days. */
public data class AgendaModel(val dayLabel: String, val fromHour: Int, val toHour: Int, val blocks: List<AgendaBlock>, val now: Double?) {
    public companion object {
        private const val FROM = 6
        private const val TO = 24

        public fun build(now: Instant, zone: ZoneId, day: LocalDate, sessions: List<SessionView>): AgendaModel {
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant()
            fun at(instant: Instant): Double {
                val hours = Duration.between(start, instant.coerceIn(start, end)).toMinutes() / 60.0
                return ((hours - FROM) / (TO - FROM)).coerceIn(0.0, 1.0)
            }
            fun clock(instant: Instant) = instant.atZone(zone).toLocalTime().toString().take(5)
            val blocks = sessions
                .filter { it.session.end > start && it.session.start < end }
                .sortedBy { it.session.start }
                .map { view ->
                    val kind = when (view.session.state) {
                        SessionState.ACTIVE -> BarKind.NOW
                        SessionState.SCHEDULED -> BarKind.LATER
                        else -> BarKind.PAST
                    }
                    AgendaBlock(
                        view.session.id,
                        view.tag?.let { "#${it.removePrefix("#")}" } ?: "Focus session",
                        "${clock(view.session.start)}–${clock(view.session.end)}",
                        at(view.session.start),
                        at(view.session.end),
                        kind,
                    )
                }
            val today = now.atZone(zone).toLocalDate() == day
            val label = when (day) {
                now.atZone(zone).toLocalDate() -> "Today"
                now.atZone(zone).toLocalDate().plusDays(1) -> "Tomorrow"
                else -> Format.dayMonth(day)
            }
            return AgendaModel(label, FROM, TO, blocks, if (today) at(now) else null)
        }
    }
}

/** Facts from this phone that earn XP. Nothing here is shared (ADR-0018). */
public data class ProgressFacts(
    val habitDaysMet: Int,
    val focusMinutes: Long,
    val stayedFocused: Int,
    val longestStreak: Int,
    val challengesJoined: Int,
    val challengesCompleted: Int,
)

public data class Badge(val id: String, val title: String, val detail: String, val earned: Boolean)

public data class ProgressionModel(
    val xp: Int,
    val level: Int,
    val title: String,
    val toNext: Int,
    val fraction: Double,
    val badges: List<Badge>,
)

public object Progression {
    /** Level n starts at 50 n (n - 1) XP, so each level takes 100 XP more than the last. */
    public fun level(xp: Int): Int = ((1 + sqrt(1 + 4 * xp / 50.0)) / 2).toInt().coerceAtLeast(1)

    public fun startOf(level: Int): Int = 50 * level * (level - 1)

    public fun titleFor(level: Int): String = when {
        level < 4 -> "Focus Novice"
        level < 8 -> "Focus Apprentice"
        level < 13 -> "Focus Keeper"
        level < 18 -> "Focus Adept"
        else -> "Focus Master"
    }

    public fun xp(facts: ProgressFacts): Int =
        facts.habitDaysMet * 10 + (facts.focusMinutes / 5).toInt() + facts.stayedFocused * 5 + facts.challengesCompleted * 100

    public fun badges(facts: ProgressFacts): List<Badge> = listOf(
        Badge("first-session", "First session", "Finish a focus session", facts.focusMinutes > 0),
        Badge("week-streak", "Seven days", "A 7-day habit streak", facts.longestStreak >= 7),
        Badge("month-streak", "Thirty days", "A 30-day habit streak", facts.longestStreak >= 30),
        Badge("held-the-line", "Held the line", "Stay focused 10 times", facts.stayedFocused >= 10),
        Badge("team-player", "Team player", "Join a challenge", facts.challengesJoined > 0),
        Badge("finisher", "Finisher", "Complete a challenge", facts.challengesCompleted > 0),
    )

    public fun build(facts: ProgressFacts): ProgressionModel = of(xp(facts), badges(facts))

    public fun of(totalXp: Int, badges: List<Badge> = emptyList()): ProgressionModel {
        val xp = totalXp.coerceAtLeast(0)
        val level = level(xp)
        val span = startOf(level + 1) - startOf(level)
        return ProgressionModel(
            xp,
            level,
            "Level $level · ${titleFor(level)}",
            startOf(level + 1) - xp,
            (xp - startOf(level)).toDouble() / span,
            badges,
        )
    }
}

public data class FocusDay(val label: String, val minutes: Long, val isToday: Boolean)

/** Finished focus time on each of the last seven days, for the Metrics chart. */
public data class FocusWeek(val days: List<FocusDay>, val total: String, val maxMinutes: Long) {
    public companion object {
        public fun build(today: LocalDate, zone: ZoneId, sessions: List<StoredSession>): FocusWeek {
            val days = (6 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                val from = day.atStartOfDay(zone).toInstant()
                val to = day.plusDays(1).atStartOfDay(zone).toInstant()
                val minutes = sessions
                    .filter { it.state == SessionState.COMPLETED || it.state == SessionState.ACTIVE }
                    .sumOf { session ->
                        val start = maxOf(session.start, from)
                        val end = minOf(session.finishedAt ?: session.end, session.end, to)
                        if (end > start) Duration.between(start, end).toMinutes() else 0
                    }
                FocusDay(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK), minutes, back == 0)
            }
            val total = days.sumOf { it.minutes }
            return FocusWeek(days, "${Format.duration(Duration.ofMinutes(total))} this week", days.maxOf { it.minutes })
        }
    }
}
