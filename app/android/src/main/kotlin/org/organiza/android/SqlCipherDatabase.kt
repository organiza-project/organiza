package org.organiza.android

import android.database.Cursor
import android.database.sqlite.SQLiteException
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.organiza.storage.Database
import org.organiza.storage.Row

/** Runs the [Database] port on SQLCipher. Android's counterpart to the JDBC test driver. */
class SqlCipherDatabase private constructor(private val db: SQLiteDatabase) : Database {
    override fun execute(sql: String, args: List<Any?>) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.toTypedArray())
    }

    override fun <T> query(sql: String, args: List<Any?>, map: (Row) -> T): List<T> =
        db.rawQuery(sql, *args.toTypedArray()).use { cursor ->
            val row = CursorRow(cursor)
            buildList { while (cursor.moveToNext()) add(map(row)) }
        }

    override fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    private class CursorRow(private val cursor: Cursor) : Row {
        override fun string(column: Int): String? = if (cursor.isNull(column)) null else cursor.getString(column)

        override fun long(column: Int): Long? = if (cursor.isNull(column)) null else cursor.getLong(column)
    }

    companion object {
        fun open(file: File, key: ByteArray): SqlCipherDatabase {
            System.loadLibrary("sqlcipher")
            try {
                // A wrong key looks like corruption, and the default handler responds by
                // deleting the file. This handler keeps it, and the caller reports the error.
                val db = SQLiteDatabase.openOrCreateDatabase(file, key, null, { _, _ -> }, null)
                db.setForeignKeyConstraintsEnabled(true)
                return SqlCipherDatabase(db).also { it.query("SELECT count(*) FROM sqlite_master") { row -> row.long(0) } }
            } catch (failure: SQLiteException) {
                throw StorageException(StorageFailure.DATABASE_UNREADABLE, failure)
            }
        }
    }
}
