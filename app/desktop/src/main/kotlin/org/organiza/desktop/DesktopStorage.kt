package org.organiza.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import org.organiza.arena.SecretStore
import org.organiza.storage.Database
import org.organiza.storage.Row

class StorageUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The desktop database (ADR-0024): SQLCipher, keyed from the login keyring. */
object DesktopStorage {
    const val KEY = "database-key"
    private const val KEY_BYTES = 32

    fun file(env: Map<String, String> = System.getenv()): Path =
        (env["XDG_DATA_HOME"]?.takeIf(String::isNotEmpty)?.let(Path::of) ?: Path.of(env["HOME"].orEmpty(), ".local/share"))
            .resolve("organiza/organiza.db")

    fun open(file: Path, keyring: SecretStore): EncryptedDatabase {
        val key = keyring.get(KEY) ?: run {
            // A new key for an existing file could only make it unreadable, so it is never made.
            if (Files.exists(file)) throw StorageUnavailable("The database key is missing from the keyring, so the database was left as it is.")
            ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes).also { keyring.put(KEY, it) }
        }
        Files.createDirectories(file.parent)
        return EncryptedDatabase.open(file, key)
    }
}

/** The login keyring through libsecret (ADR-0024). Secrets pass over stdin and stdout, never argv. */
class Keyring : SecretStore {
    override fun put(name: String, value: ByteArray) {
        run("store", name, value.toHex()) ?: throw StorageUnavailable("The keyring did not store the key.")
    }

    override fun get(name: String): ByteArray? = run("lookup", name, "")?.trim()?.takeIf(String::isNotEmpty)?.fromHex()

    override fun remove(name: String) {
        run("clear", name, "")
    }

    /** Returns stdout, or null when the helper failed. */
    private fun run(command: String, name: String, input: String): String? {
        val process = try {
            ProcessBuilder("python3", "-c", HELPER, command, name).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (failure: java.io.IOException) {
            throw StorageUnavailable("python3 is needed to reach the keyring.", failure)
        }
        process.outputStream.use { it.write(input.toByteArray()) }
        val out = process.inputStream.bufferedReader().readText()
        // Unlocking the keyring can wait on the person, so the limit is generous.
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroy()
            return null
        }
        return out.takeIf { process.exitValue() == 0 }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? =
        takeIf { it.length % 2 == 0 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()

    private companion object {
        val HELPER = """
            import sys, gi
            gi.require_version('Secret', '1')
            from gi.repository import Secret
            schema = Secret.Schema.new('org.organiza.Desktop', Secret.SchemaFlags.NONE, {'name': Secret.SchemaAttributeType.STRING})
            command, name = sys.argv[1], sys.argv[2]
            attributes = {'name': name}
            if command == 'store':
                ok = Secret.password_store_sync(schema, attributes, Secret.COLLECTION_DEFAULT, 'Organiza ' + name, sys.stdin.read().strip(), None)
                sys.exit(0 if ok else 1)
            elif command == 'lookup':
                print(Secret.password_lookup_sync(schema, attributes, None) or '')
            elif command == 'clear':
                Secret.password_clear_sync(schema, attributes, None)
        """.trimIndent()
    }
}

/** SQLCipher-format SQLite through the encrypting JDBC driver. A wrong key never writes. */
class EncryptedDatabase private constructor(private val connection: Connection) : Database, AutoCloseable {
    private var depth = 0

    override fun execute(sql: String, args: List<Any?>) {
        connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            statement.execute()
        }
    }

    override fun <T> query(sql: String, args: List<Any?>, map: (Row) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            statement.executeQuery().use { rows ->
                val row = ResultRow(rows)
                buildList { while (rows.next()) add(map(row)) }
            }
        }

    override fun <T> transaction(block: () -> T): T {
        if (depth > 0) return block()
        connection.autoCommit = false
        depth++
        try {
            val result = block()
            connection.commit()
            return result
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            depth--
            connection.autoCommit = true
        }
    }

    override fun close() = connection.close()

    private fun bind(statement: PreparedStatement, args: List<Any?>) {
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    }

    private class ResultRow(private val rows: ResultSet) : Row {
        override fun string(column: Int): String? = rows.getString(column + 1)

        override fun long(column: Int): Long? = rows.getLong(column + 1).takeUnless { rows.wasNull() }

        override fun blob(column: Int): ByteArray? = rows.getBytes(column + 1)
    }

    companion object {
        fun open(file: Path, key: ByteArray): EncryptedDatabase {
            val hex = key.joinToString("") { "%02x".format(it) }
            val connection = try {
                DriverManager.getConnection("jdbc:sqlite:file:$file?cipher=sqlcipher&legacy=4&hexkey=$hex")
            } catch (failure: SQLException) {
                throw StorageUnavailable("The database could not be opened.", failure)
            }
            try {
                // Reading first means a wrong key fails here, before anything could be written.
                connection.createStatement().use { it.executeQuery("SELECT count(*) FROM sqlite_master").close() }
                connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            } catch (failure: SQLException) {
                connection.close()
                throw StorageUnavailable("The database key does not match, so nothing was changed.", failure)
            }
            return EncryptedDatabase(connection)
        }
    }
}
