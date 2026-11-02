package org.organiza.linux

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.organiza.storage.Migrations

class EncryptedDatabaseTest {
    private val file = Files.createTempDirectory("db").resolve("organiza.db")
    private val key = ByteArray(32) { (it * 7).toByte() }

    @Test
    fun `the file is encrypted and reopens with its key`() {
        EncryptedDatabase.open(file, key).use { db ->
            Migrations.migrate(db)
            db.execute("INSERT INTO rule (id, name, created_at_ms, updated_at_ms) VALUES ('r', 'ZZPLAINTEXT', 0, 0)")
        }
        val bytes = Files.readAllBytes(file)
        assertFalse(String(bytes, 0, 15, Charsets.ISO_8859_1) == "SQLite format 3")
        assertFalse("ZZPLAINTEXT" in String(bytes, Charsets.ISO_8859_1))
        EncryptedDatabase.open(file, key).use { db ->
            assertEquals(listOf("ZZPLAINTEXT"), db.query("SELECT name FROM rule") { it.string(0) })
        }
    }

    @Test
    fun `a wrong key is refused and the file is left alone`() {
        EncryptedDatabase.open(file, key).use { Migrations.migrate(it) }
        val before = Files.readAllBytes(file).toList()
        assertFailsWith<StorageUnavailable> { EncryptedDatabase.open(file, ByteArray(32) { 1 }) }
        assertEquals(before, Files.readAllBytes(file).toList())
    }

    @Test
    fun `foreign keys are enforced`() {
        EncryptedDatabase.open(file, key).use { db ->
            Migrations.migrate(db)
            assertFailsWith<Exception> {
                db.execute("INSERT INTO rule_target (rule_id, kind, value) VALUES ('missing', 'process', 'steam')")
            }
        }
    }

    @Test
    fun `keys load from a systemd credential as hex`() {
        val dir = Files.createTempDirectory("creds")
        Files.writeString(dir.resolve(DaemonKey.CREDENTIAL), "0f".repeat(32) + "\n")
        assertEquals(List(32) { 0x0f.toByte() }, DaemonKey.load(dir).toList())
        Files.writeString(dir.resolve(DaemonKey.CREDENTIAL), "short")
        assertFailsWith<StorageUnavailable> { DaemonKey.load(dir) }
        assertFailsWith<StorageUnavailable> { DaemonKey.load(null) }
    }
}
