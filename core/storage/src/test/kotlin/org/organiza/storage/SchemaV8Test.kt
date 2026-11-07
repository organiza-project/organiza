package org.organiza.storage

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.organiza.testing.JdbcDatabase

class SchemaV8Test {
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val habits = HabitStore(db)
    private val sessions = SessionStore(db)
    private val at = Instant.parse("2026-11-07T10:00:00Z")
    private val today = LocalDate.parse("2026-11-07")

    @Test
    fun `a habit keeps its minute goal and linked app`() {
        val plan = HabitPlan(HabitFrequency.DAILY, 1, null, goalMinutes = 30, linkedPackage = "com.amazon.kindle")
        habits.add("h1", "Read", plan, at)
        assertEquals(plan, habits.plan("h1"))
        habits.setPlan("h1", plan.copy(goalMinutes = null, linkedPackage = null))
        assertEquals(HabitPlan.DAILY, habits.plan("h1"))
    }

    @Test
    fun `logged minutes add up and mark the day once the goal is met`() {
        habits.add("h1", "Read", HabitPlan(HabitFrequency.DAILY, 1, null, goalMinutes = 60), at)
        assertEquals(20, habits.logMinutes("h1", today, 20))
        assertEquals(emptyMap(), habits.results("h1", today, today.plusDays(1)))
        assertEquals(60, habits.logMinutes("h1", today, 40))
        assertEquals(mapOf(today to true), habits.results("h1", today, today.plusDays(1)))
        assertEquals(60, habits.loggedMinutes("h1", today))
    }

    @Test
    fun `app minutes count towards the goal without being stored`() {
        habits.add("h1", "Read", HabitPlan(HabitFrequency.DAILY, 1, null, goalMinutes = 45, linkedPackage = "com.amazon.kindle"), at)
        habits.logMinutes("h1", today, 15)
        assertEquals(35, habits.settle("h1", today, appMinutes = 20))
        assertEquals(emptyMap(), habits.results("h1", today, today.plusDays(1)))
        assertEquals(45, habits.settle("h1", today, appMinutes = 30))
        assertEquals(mapOf(today to true), habits.results("h1", today, today.plusDays(1)))
        assertEquals(15, habits.loggedMinutes("h1", today))
    }

    @Test
    fun `logging minutes on a habit without a goal marks it done`() {
        habits.add("h1", "Walk", at)
        habits.logMinutes("h1", today, 10)
        assertEquals(mapOf(today to true), habits.results("h1", today, today.plusDays(1)))
    }

    @Test
    fun `a session keeps its tier, standard by default`() {
        val base = StoredSession("s1", null, SessionSource.MANUAL, SessionState.ACTIVE, null, at, at.plusSeconds(60), null)
        sessions.insert(base)
        sessions.insert(base.copy(id = "s2", tier = FocusTier.MONITOR))
        assertEquals(listOf(FocusTier.STANDARD, FocusTier.MONITOR), sessions.inState(SessionState.ACTIVE).map { it.tier })
    }

    @Test
    fun `goals and logs are bounded by the schema`() {
        assertFailsWith<Exception> { habits.add("h1", "Read", HabitPlan(HabitFrequency.DAILY, 1, null, goalMinutes = 0), at) }
        habits.add("h2", "Read", at)
        assertFailsWith<Exception> { habits.logMinutes("h2", today, 2000) }
    }
}
