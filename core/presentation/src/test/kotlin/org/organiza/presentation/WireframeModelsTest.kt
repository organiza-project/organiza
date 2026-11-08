package org.organiza.presentation

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.organiza.storage.HabitFrequency
import org.organiza.storage.HabitPlan
import org.organiza.storage.SessionSource
import org.organiza.storage.SessionState
import org.organiza.storage.StoredSession

class WireframeModelsTest {
    private val today = LocalDate.parse("2026-11-04") // a Wednesday
    private val zone = ZoneOffset.UTC

    private fun met(vararg days: String) = days.associate { LocalDate.parse(it) to true }

    @Test
    fun `a weekly habit counts met days since monday`() {
        val plan = HabitPlan(HabitFrequency.WEEKLY, 3, null)
        val progress = HabitPeriods.progress(plan, met("2026-11-01", "2026-11-02", "2026-11-04"), today)
        assertEquals(PeriodProgress(done = 2, target = 3, doneToday = true, label = "2 of 3 this week"), progress)
        assertEquals(false, progress.met)
    }

    @Test
    fun `a monthly habit counts from the first, and a daily one is today alone`() {
        val monthly = HabitPeriods.progress(HabitPlan(HabitFrequency.MONTHLY, 4, null), met("2026-10-31", "2026-11-01", "2026-11-03"), today)
        assertEquals("2 of 4 this month", monthly.label)
        val daily = HabitPeriods.progress(HabitPlan.DAILY, met("2026-11-03"), today)
        assertEquals(PeriodProgress(0, 1, false, "Not done today"), daily)
        assertEquals("Done today", HabitPeriods.progress(HabitPlan.DAILY, met("2026-11-04"), today).label)
    }

    @Test
    fun `the dial counts goals met today, a met weekly target included`() {
        val home = HomeModel.build(
            today,
            listOf(
                HomeHabitInput("a", "Read", HabitPlan.DAILY, met("2026-11-04")),
                HomeHabitInput("b", "Stretch", HabitPlan.DAILY, emptyMap()),
                HomeHabitInput("c", "Run", HabitPlan(HabitFrequency.WEEKLY, 2, null), met("2026-11-02", "2026-11-03")),
            ),
        )
        assertEquals(2.0 / 3, home.completion, 1e-9)
        assertEquals("2 of 3 goals met", home.headline)
        assertEquals(listOf("Read", "Stretch", "Run"), home.habits.map { it.name })
    }

    @Test
    fun `an empty home says what to do`() {
        val home = HomeModel.build(today, emptyList())
        assertEquals(0.0, home.completion)
        assertEquals("No habits yet", home.headline)
    }

    @Test
    fun `the agenda places sessions on the hour grid with a now line`() {
        val now = Instant.parse("2026-11-04T10:30:00Z")
        fun session(id: String, from: String, to: String, state: SessionState) = SessionView(
            StoredSession(id, null, SessionSource.MANUAL, state, null, Instant.parse("2026-11-04T${from}:00Z"), Instant.parse("2026-11-04T${to}:00Z"), null),
            "deepwork",
            false,
        )
        val agenda = AgendaModel.build(
            now,
            zone,
            today,
            listOf(session("s1", "09:00", "11:00", SessionState.ACTIVE), session("s2", "15:00", "16:30", SessionState.SCHEDULED)),
        )
        assertEquals(6, agenda.fromHour)
        assertEquals((10.5 - 6) / 18, agenda.now!!, 1e-9)
        val first = agenda.blocks.first()
        assertEquals("#deepwork", first.label)
        assertEquals("09:00–11:00", first.time)
        assertEquals(BarKind.NOW, first.kind)
        assertEquals(3.0 / 18, first.top, 1e-9)
        assertEquals(BarKind.LATER, agenda.blocks.last().kind)
        assertEquals(null, AgendaModel.build(now, zone, today.plusDays(1), emptyList()).now)
    }

    @Test
    fun `xp comes from local results and sets the level and title`() {
        val model = Progression.build(ProgressFacts(habitDaysMet = 60, focusMinutes = 1500, stayedFocused = 40, longestStreak = 9, challengesJoined = 1, challengesCompleted = 0))
        assertEquals(60 * 10 + 1500 / 5 + 40 * 5, model.xp)
        assertEquals(7, model.level)
        assertEquals("Level 7 · Novice", model.title)
        assertEquals("Initiate at level 10 · 1,150 XP to go", model.rankLine)
        assertTrue(model.fraction in 0.0..1.0)
        assertEquals(setOf("first-session", "week-streak", "held-the-line", "team-player"), model.badges.filter { it.earned }.map { it.id }.toSet())
    }

    @Test
    fun `levels grow further apart`() {
        assertEquals(1, Progression.level(0))
        assertEquals(2, Progression.level(50))
        assertEquals(3, Progression.level(150))
        assertEquals(9, Progression.level(2249))
        assertEquals(10, Progression.level(2250))
        assertEquals(50, Progression.level(61250))
    }

    @Test
    fun `ranks change every ten levels, then at thirty and fifty`() {
        assertEquals(
            listOf("Novice", "Novice", "Initiate", "Initiate", "Focus Adept", "Stoic Master", "Stoic Master", "Zen Grandmaster", "Zen Grandmaster"),
            listOf(1, 9, 10, 19, 20, 30, 49, 50, 80).map { Rank.of(it).title },
        )
        assertEquals(Rank.FOCUS_ADEPT, Rank.of(12).next)
        assertEquals(null, Rank.ZEN_GRANDMASTER.next)
        assertEquals("Highest rank reached", Progression.of(Progression.startOf(55)).rankLine)
        assertEquals("Stoic Master at level 30 · 50 XP to go", Progression.of(Progression.startOf(30) - 50).rankLine)
    }

    @Test
    fun `the weekly chart sums finished focus per day`() {
        fun done(day: String, minutes: Long) = StoredSession(
            day, null, SessionSource.MANUAL, SessionState.COMPLETED, null,
            Instant.parse("${day}T09:00:00Z"), Instant.parse("${day}T09:00:00Z").plus(Duration.ofMinutes(minutes)), null,
        )
        val week = FocusWeek.build(today, zone, listOf(done("2026-11-04", 90), done("2026-11-02", 30), done("2026-10-20", 500)))
        assertEquals(7, week.days.size)
        assertEquals(90L, week.days.last().minutes)
        assertEquals("Wed", week.days.last().label)
        assertEquals("2 h 00 m this week", week.total)
        assertEquals(90L, week.maxMinutes)
    }
}
