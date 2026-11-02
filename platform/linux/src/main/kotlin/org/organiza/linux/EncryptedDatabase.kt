package org.organiza.linux

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import org.organiza.storage.Database
import org.organiza.storage.Row

class StorageUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The database key as a systemd credential, sealed by systemd-creds (ADR-0013). */
object DaemonKey {
    const val CREDENTIAL = "organiza-db-key"
    private const val KEY_BYTES = 32

    fun load(credentialsDirectory: Path?): ByteArray {
        val file = credentialsDirectory?.resolve(CREDENTIAL)
            ?: throw StorageUnavailable("No credentials directory. Run organiza under its systemd unit.")
        if (!Files.exists(file)) throw StorageUnavailable("The $CREDENTIAL credential is missing.")
        val hex = Files.readString(file).trim()
        if (hex.length != KEY_BYTES * 2 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) {
            throw StorageUnavailable("The $CREDENTIAL credential is not a 64-character hex key.")
        }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
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
