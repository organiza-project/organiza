package org.organiza.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.testing.JdbcDatabase

class SchemaV14Test {
    private val db = JdbcDatabase()

    @Test
    fun `upgrading keeps the notes and starts an empty calendar`() {
        Migrations.migrate(db, 13)
        db.execute("INSERT INTO note (id, body, created_at_ms) VALUES ('a', 'Call the dentist', 1)")
        Migrations.migrate(db)
        assertEquals(1, NoteStore(db).count())
        assertEquals(emptyList(), EventStore(db).between(java.time.Instant.EPOCH, java.time.Instant.parse("3000-01-01T00:00:00Z")))
    }
}
