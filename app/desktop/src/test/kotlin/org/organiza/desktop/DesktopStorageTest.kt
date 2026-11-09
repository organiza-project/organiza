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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.organiza.app.OrganizaCore
import org.organiza.app.StorageUnavailable
import org.organiza.arena.SecretStore
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

    private fun core(secrets: SecretStore): OrganizaCore {
        val home = file.parent
        val env = mapOf("HOME" to home.toString(), "XDG_DATA_HOME" to "$home/data", "XDG_CONFIG_HOME" to "$home/config")
        return OrganizaCore(DesktopPlatform(secrets, emptyList(), env, file, Autostart(home.resolve("config")))).also { it.start() }
    }

    /** Waits for the work queued on the storage thread so far, and the snapshot it published. */
    private fun settle(core: OrganizaCore) {
        val done = CountDownLatch(1)
        core.onStorageThread { done.countDown() }
        done.await(5, TimeUnit.SECONDS)
        javax.swing.SwingUtilities.invokeAndWait {}
    }

    @Test
    fun `a keyring that cannot be reached shows a storage problem`() {
        val core = core(object : SecretStore {
            override fun put(name: String, value: ByteArray) = throw StorageUnavailable("python3 is needed to reach the keyring.")
            override fun get(name: String): ByteArray? = throw StorageUnavailable("python3 is needed to reach the keyring.")
            override fun remove(name: String) = throw StorageUnavailable("python3 is needed to reach the keyring.")
        })
        core.requestUiRefresh()
        settle(core)
        assertNotNull(core.uiState.value.storageProblem)
    }

    @Test
    fun `the Groq key is read from the keyring once, not on every refresh`() {
        val reads = mutableListOf<String>()
        val core = core(object : SecretStore by keyring {
            override fun get(name: String): ByteArray? = keyring.get(name).also { reads += name }
        })
        repeat(3) { core.requestUiRefresh() }
        settle(core)
        assertEquals(1, reads.count { it == "groq-key" })
    }
}
