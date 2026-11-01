package org.organiza.storage

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaV1Test {
    private lateinit var db: Connection

    @BeforeTest
    fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        val sql = checkNotNull(javaClass.getResource("/org/organiza/storage/schema/v1.sql")) {
            "schema v1 resource is missing"
        }.readText()
        db.createStatement().use { it.executeUpdate(sql) }
    }

    @AfterTest
    fun close() = db.close()

    private fun exec(sql: String) {
        db.createStatement().use { it.execute(sql) }
    }

    private fun column(sql: String): List<String> = db.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            buildList { while (rows.next()) add(rows.getString(1)) }
        }
    }

    private fun assertRejected(sql: String) {
        assertFailsWith<SQLException>(sql) { exec(sql) }
    }

    private fun insertRule(id: String = "r1") =
        exec("INSERT INTO rule (id, name, created_at_ms, updated_at_ms) VALUES ('$id', 'Focus', 0, 0)")

    private fun insertHabit(id: String = "h1") = exec(
        "INSERT INTO habit (id, name, criterion_kind, created_at_ms) VALUES ('$id', 'Read', 'manual', 0)",
    )

    private fun insertChallenge(id: String = "c1") {
        insertHabit()
        exec(
            "INSERT INTO challenge (id, habit_id, title, starts_on, length_days, freeze_allowance, state) " +
                "VALUES ('$id', 'h1', 'No Reels', '2026-11-01', 30, 2, 'active')",
        )
    }

    private fun blob(size: Int) = "x'" + "ab".repeat(size) + "'"

    private fun challengeLog(date: String = "2026-11-01", salt: Int = 32, commitment: Int = 32, outcome: String = "success") =
        "INSERT INTO challenge_log (challenge_id, local_date, outcome, streak, salt, commitment) " +
            "VALUES ('c1', '$date', '$outcome', 1, ${blob(salt)}, ${blob(commitment)})"

    @Test
    fun `creates exactly the specified tables at version 1`() {
        assertEquals(
            listOf("challenge", "challenge_log", "habit", "habit_log", "rule", "rule_target", "session"),
            column("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"),
        )
        assertEquals(listOf("1"), column("PRAGMA user_version"))
    }

    @Test
    fun `all tables are strict`() {
        assertEquals(
            listOf("0"),
            column(
                "SELECT count(*) FROM pragma_table_list WHERE schema = 'main' AND type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND strict = 0",
            ),
        )
    }

    @Test
    fun `rule tags must start with a hash`() {
        assertRejected(
            "INSERT INTO rule (id, name, calendar_tag, created_at_ms, updated_at_ms) VALUES ('r', 'n', 'focus', 0, 0)",
        )
    }

    @Test
    fun `rule targets cascade with their rule and restrict their kind`() {
        insertRule()
        exec("INSERT INTO rule_target (rule_id, kind, value) VALUES ('r1', 'package', 'com.instagram.android')")
        assertRejected("INSERT INTO rule_target (rule_id, kind, value) VALUES ('r1', 'url', 'https://x.com/a')")
        assertRejected("INSERT INTO rule_target (rule_id, kind, value) VALUES ('nope', 'domain', 'x.com')")
        exec("DELETE FROM rule WHERE id = 'r1'")
        assertEquals(listOf("0"), column("SELECT count(*) FROM rule_target"))
    }

    @Test
    fun `sessions must end after they start`() {
        assertRejected(
            "INSERT INTO session (id, source, state, starts_at_ms, ends_at_ms) VALUES ('s', 'manual', 'active', 10, 10)",
        )
    }

    @Test
    fun `session history survives deleting its rule`() {
        insertRule()
        exec(
            "INSERT INTO session (id, rule_id, source, state, starts_at_ms, ends_at_ms) " +
                "VALUES ('s', 'r1', 'manual', 'completed', 0, 10)",
        )
        exec("DELETE FROM rule WHERE id = 'r1'")
        assertEquals(listOf("1"), column("SELECT count(*) FROM session WHERE rule_id IS NULL"))
    }

    @Test
    fun `calendar occurrences are unique so sync is idempotent`() {
        val occurrence = "INSERT INTO session (id, source, state, calendar_event_uid, starts_at_ms, ends_at_ms) " +
            "VALUES ('%s', 'calendar', 'scheduled', 'uid-1', %d, 100)"
        exec(occurrence.format("s1", 0))
        assertRejected(occurrence.format("s2", 0))
        exec(occurrence.format("s3", 50))
    }

    @Test
    fun `calendar sessions need an event uid`() {
        assertRejected(
            "INSERT INTO session (id, source, state, starts_at_ms, ends_at_ms) VALUES ('s', 'calendar', 'scheduled', 0, 1)",
        )
    }

    @Test
    fun `habit logs hold one result per local day`() {
        insertHabit()
        val log = "INSERT INTO habit_log (habit_id, local_date, success) VALUES ('h1', '2026-11-01', 1)"
        exec(log)
        assertRejected(log)
        assertRejected("INSERT INTO habit_log (habit_id, local_date, success) VALUES ('h1', '2026-11-02', 2)")
        assertRejected("INSERT INTO habit_log (habit_id, local_date, success) VALUES ('h1', 'Nov 3', 1)")
    }

    @Test
    fun `challenge logs need a 32 byte salt and commitment`() {
        insertChallenge()
        exec(challengeLog())
        assertRejected(challengeLog(date = "2026-11-02", salt = 16))
        assertRejected(challengeLog(date = "2026-11-03", commitment = 31))
    }

    @Test
    fun `challenge log outcomes are restricted`() {
        insertChallenge()
        exec(challengeLog(outcome = "frozen"))
        assertRejected(challengeLog(date = "2026-11-02", outcome = "maybe"))
    }

    @Test
    fun `challenge_log carries no usage data`() {
        assertEquals(
            setOf(
                "challenge_id", "local_date", "outcome", "streak", "salt",
                "commitment", "previous_commitment", "signature", "synced_at_ms",
            ),
            column("SELECT name FROM pragma_table_info('challenge_log')").toSet(),
        )
    }
}
