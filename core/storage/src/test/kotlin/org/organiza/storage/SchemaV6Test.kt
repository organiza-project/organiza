package org.organiza.storage

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockTarget
import org.organiza.testing.JdbcDatabase

class SchemaV6Test {
    private val db = JdbcDatabase().also { Migrations.migrate(it) }
    private val at = Instant.parse("2026-11-03T10:00:00Z")
    private val instagram = (PackageName.parse("com.instagram.android") as Parsed.Valid).value
    private val youtube = (PackageName.parse("com.google.android.youtube") as Parsed.Valid).value

    @Test
    fun `a habit keeps its frequency, target and reminder`() {
        val habits = HabitStore(db)
        habits.add("h1", "Run", HabitPlan(HabitFrequency.WEEKLY, target = 3, reminderMinute = 7 * 60 + 30), at)
        habits.add("h2", "Read", at)
        assertEquals(HabitPlan(HabitFrequency.WEEKLY, 3, 450), habits.plan("h1"))
        assertEquals(HabitPlan.DAILY, habits.plan("h2"))
        assertEquals(listOf("h1" to 450), habits.reminders().map { it.id to it.minute })
    }

    @Test
    fun `a plan can be changed and its reminder cleared`() {
        val habits = HabitStore(db)
        habits.add("h1", "Run", HabitPlan(HabitFrequency.MONTHLY, 10, 600), at)
        habits.setPlan("h1", HabitPlan(HabitFrequency.WEEKLY, 2, null))
        assertEquals(HabitPlan(HabitFrequency.WEEKLY, 2, null), habits.plan("h1"))
        assertEquals(emptyList(), habits.reminders())
    }

    @Test
    fun `targets and reminder times are bounded by the schema`() {
        val habits = HabitStore(db)
        assertFailsWith<Exception> { habits.add("x", "X", HabitPlan(HabitFrequency.WEEKLY, 0, null), at) }
        assertFailsWith<Exception> { habits.add("y", "Y", HabitPlan(HabitFrequency.DAILY, 1, 1440), at) }
    }

    @Test
    fun `daily limits are per app and can be removed`() {
        val limits = UsageLimitStore(db)
        limits.set(instagram, Duration.ofMinutes(30))
        limits.set(youtube, Duration.ofMinutes(60))
        limits.set(instagram, Duration.ofMinutes(45))
        assertEquals(mapOf(instagram to Duration.ofMinutes(45), youtube to Duration.ofMinutes(60)), limits.all())
        limits.remove(youtube)
        assertEquals(setOf(instagram), limits.all().keys)
        assertFailsWith<Exception> { limits.set(youtube, Duration.ofMinutes(1)) }
    }

    @Test
    fun `settings default when unset`() {
        val settings = SettingsStore(db)
        assertEquals(false, settings.flag(Setting.BLOCK_REELS))
        settings.setFlag(Setting.BLOCK_REELS, true)
        assertEquals(true, settings.flag(Setting.BLOCK_REELS))
        assertEquals(false, settings.flag(Setting.BLOCK_SHORTS))
    }

    @Test
    fun `a limit intervention round trips`() {
        val store = InterventionStore(db)
        store.record(
            StoredIntervention(null, BlockTarget.Limit(instagram), InterventionChoice.STAYED, null, null, null, Duration.ZERO, null, false, false, at),
        )
        assertEquals(BlockTarget.Limit(instagram), store.between(at, at.plusSeconds(1)).single().target)
    }

    @Test
    fun `version 5 interventions survive the upgrade to version 6`() {
        val old = JdbcDatabase().also { Migrations.migrate(it, target = 5) }
        old.execute(
            "INSERT INTO intervention_event (kind, app, outcome, strict, at_ms) VALUES ('app', 'com.instagram.android', 'stayed', 0, 5)",
        )
        old.execute("INSERT INTO habit (id, name, criterion_kind, created_at_ms) VALUES ('h', 'Old', 'manual', 0)")
        Migrations.migrate(old)
        assertEquals(listOf(1L), old.query("SELECT count(*) FROM intervention_event") { it.long(0) })
        assertEquals(HabitPlan.DAILY, HabitStore(old).plan("h"))
        assertEquals(6L, old.query("PRAGMA user_version") { it.long(0) }.single())
    }

    @Test
    fun `nudges are kept once per sender, receiver and day`() {
        db.execute("INSERT INTO habit (id, name, criterion_kind, created_at_ms) VALUES ('h', 'Read', 'manual', 0)")
        val arena = ArenaStore(db)
        arena.createChallenge(
            StoredChallenge("c", "h", "T", "2026-11-01", 30, 0, 0, false, "active", "b".repeat(64), "Me", "https://r.example", 0, false, false),
        )
        val day = LocalDate.parse("2026-11-03")
        assertEquals(true, arena.putNudge("c", "a".repeat(64), "b".repeat(64), day))
        assertEquals(false, arena.putNudge("c", "a".repeat(64), "b".repeat(64), day))
        assertEquals(listOf("a".repeat(64)), arena.nudgesTo("c", "b".repeat(64), day))
        assertEquals(true, arena.nudged("c", "a".repeat(64), "b".repeat(64), day))
        assertEquals(emptyList(), arena.nudgesTo("c", "b".repeat(64), day.plusDays(1)))
    }
}
