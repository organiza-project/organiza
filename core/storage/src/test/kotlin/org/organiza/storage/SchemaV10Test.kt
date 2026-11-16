package org.organiza.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.organiza.testing.JdbcDatabase

class SchemaV10Test {
    private val db = JdbcDatabase()

    private fun tables(): Set<String> = db.query("SELECT name FROM sqlite_master WHERE type = 'table'") { it.string(0)!! }.toSet()

    @Test
    fun `arena tables are dropped and everything else stays`() {
        Migrations.migrate(db, 9)
        db.execute("INSERT INTO habit (id, name, criterion_kind, created_at_ms) VALUES ('h', 'Read', 'manual', 0)")
        db.execute("INSERT INTO challenge (id, habit_id, title, starts_on, length_days, state) VALUES ('c', 'h', 'Thirty days', '2026-11-01', 30, 'active')")
        Migrations.migrate(db)
        val left = tables()
        listOf("challenge", "challenge_log", "arena_member", "arena_entry", "arena_conflict", "arena_reveal", "arena_nudge")
            .forEach { assertTrue(it !in left, "$it is still there") }
        assertEquals(listOf("Read"), db.query("SELECT name FROM habit") { it.string(0) })
    }
}
