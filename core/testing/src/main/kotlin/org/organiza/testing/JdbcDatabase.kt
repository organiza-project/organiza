package org.organiza.testing

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.organiza.storage.Database
import org.organiza.storage.Row

/** Runs the [Database] port on in-memory SQLite. Android runs it on SQLCipher (ADR-0008). */
public class JdbcDatabase : Database, AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    private var depth = 0

    init {
        connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
    }

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
                val row = JdbcRow(rows)
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

    override fun close() {
        connection.close()
    }

    private fun bind(statement: PreparedStatement, args: List<Any?>) {
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    }

    private class JdbcRow(private val rows: ResultSet) : Row {
        override fun string(column: Int): String? = rows.getString(column + 1)

        override fun long(column: Int): Long? = rows.getLong(column + 1).takeUnless { rows.wasNull() }
    }
}
