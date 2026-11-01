package org.organiza.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.organiza.testing.JdbcDatabase

class MigrationsTest {
    private val db = JdbcDatabase()

    private fun version() = db.query("PRAGMA user_version") { it.long(0) }.single()

    private fun tables() =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name") { it.string(0) }

    @Test
    fun `splits statements around comments and quoted semicolons`() {
        val sql = "-- a comment; with a semicolon\nCREATE TABLE a (b TEXT DEFAULT 'x;y');\n\n-- end\nPRAGMA user_version = 1;\n"
        assertEquals(
            listOf("CREATE TABLE a (b TEXT DEFAULT 'x;y')", "PRAGMA user_version = 1"),
            Migrations.statements(sql),
        )
    }

    @Test
    fun `an empty database migrates to the latest version`() {
        assertEquals(MigrationResult.Migrated(0, Migrations.LATEST), Migrations.migrate(db))
        assertEquals(Migrations.LATEST.toLong(), version())
        assertEquals(true, "tamper_event" in tables())
    }

    @Test
    fun `migrating twice changes nothing`() {
        Migrations.migrate(db)
        assertEquals(MigrationResult.Current(Migrations.LATEST), Migrations.migrate(db))
    }

    @Test
    fun `version 1 data survives the upgrade to version 2`() {
        Migrations.migrate(db, target = 1)
        db.execute("INSERT INTO rule (id, name, created_at_ms, updated_at_ms) VALUES ('r', 'Focus', 0, 0)")
        Migrations.migrate(db)
        assertEquals(
            listOf("Focus|0"),
            db.query("SELECT name, block_short_form FROM rule") { "${it.string(0)}|${it.long(1)}" },
        )
    }

    @Test
    fun `a database from a newer app is refused untouched`() {
        db.execute("PRAGMA user_version = 99")
        assertEquals(MigrationResult.TooNew(99), Migrations.migrate(db))
        assertEquals(99L, version())
        assertEquals(emptyList(), tables())
    }

    @Test
    fun `a failing migration leaves the database as it was`() {
        db.execute("CREATE TABLE rule (id TEXT)")
        assertFailsWith<Exception> { Migrations.migrate(db) }
        assertEquals(0L, version())
        assertEquals(listOf<String?>("rule"), tables())
    }

    @Test
    fun `tamper events hold only capability and time`() {
        Migrations.migrate(db)
        assertEquals(
            setOf("id", "session_id", "capability", "at_ms"),
            db.query("SELECT name FROM pragma_table_info('tamper_event')") { it.string(0) }.toSet(),
        )
    }
}
