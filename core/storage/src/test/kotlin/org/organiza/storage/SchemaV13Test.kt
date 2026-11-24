package org.organiza.storage

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.testing.JdbcDatabase

class SchemaV13Test {
    private val db = JdbcDatabase()

    @Test
    fun `notes written before titles keep their text and have no title`() {
        Migrations.migrate(db, 12)
        db.execute("INSERT INTO note (id, body, subject, created_at_ms, remind_at_ms) VALUES ('a', 'Call the dentist', 'Errands', 1, NULL)")
        Migrations.migrate(db)
        assertEquals(listOf(Note("a", "Call the dentist", "Errands", Instant.ofEpochMilli(1), null, null)), NoteStore(db).all())
    }
}
