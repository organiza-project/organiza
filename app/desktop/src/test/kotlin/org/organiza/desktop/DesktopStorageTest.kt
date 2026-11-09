package org.organiza.desktop

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.organiza.app.StorageUnavailable
import org.organiza.storage.Migrations
import org.organiza.testing.FakeSecretStore

class DesktopStorageTest {
    private val file = Files.createTempDirectory("organiza").resolve("organiza.db")
    private val keyring = FakeSecretStore()

    @Test
    fun `the first start makes a key in the keyring and later starts reuse it`() {
        DesktopStorage.open(file, keyring).use { Migrations.migrate(it) }
        val key = assertNotNull(keyring.get(DesktopStorage.KEY))
        assertEquals(32, key.size)
        DesktopStorage.open(file, keyring).use { db ->
            assertEquals(listOf(Migrations.LATEST.toLong()), db.query("PRAGMA user_version") { it.long(0) })
        }
        assertContentEquals(key, keyring.get(DesktopStorage.KEY))
    }

    @Test
    fun `a database whose key is gone is left as it is`() {
        DesktopStorage.open(file, keyring).use { Migrations.migrate(it) }
        val before = file.readBytes()
        keyring.remove(DesktopStorage.KEY)
        assertFailsWith<StorageUnavailable> { DesktopStorage.open(file, keyring) }
        assertContentEquals(before, file.readBytes())
        assertEquals(null, keyring.get(DesktopStorage.KEY))
    }

    @Test
    fun `a wrong key never writes`() {
        DesktopStorage.open(file, keyring).use { Migrations.migrate(it) }
        val before = file.readBytes()
        keyring.put(DesktopStorage.KEY, ByteArray(32))
        assertFailsWith<StorageUnavailable> { DesktopStorage.open(file, keyring) }
        assertContentEquals(before, file.readBytes())
    }

    @Test
    fun `the file is not plain SQLite`() {
        DesktopStorage.open(file, keyring).use { Migrations.migrate(it) }
        assertFalse(String(file.readBytes().copyOf(16)).startsWith("SQLite format 3"))
        assert(file.exists())
    }
}
