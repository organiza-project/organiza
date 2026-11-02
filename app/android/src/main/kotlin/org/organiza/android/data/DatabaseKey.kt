package org.organiza.android.data

import android.content.Context
import java.io.File
import java.security.SecureRandom

enum class StorageFailure { KEY_MISSING, KEY_UNREADABLE, DATABASE_UNREADABLE, DATABASE_TOO_NEW }

class StorageException(val failure: StorageFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/** A random database key, wrapped by a non-exportable Keystore key (ADR-0008). */
class DatabaseKey(context: Context, private val database: File) {
    private val wrapped = File(context.noBackupFilesDir, "database.key")
    private val sealer = KeystoreSealer("organiza_database_wrap")

    /** Returns the raw key. Never replaces a key while a database that needs it exists. */
    fun load(): ByteArray {
        if (wrapped.exists()) {
            try {
                return sealer.open(wrapped.readBytes())
            } catch (failure: SealedDataException) {
                throw StorageException(StorageFailure.KEY_UNREADABLE, failure)
            }
        }
        if (database.exists()) throw StorageException(StorageFailure.KEY_MISSING)
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        writeAtomically(wrapped, sealer.seal(key))
        return key
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}

/** Replaces [file] in one step, so a crash cannot leave half a key behind. */
internal fun writeAtomically(file: File, contents: ByteArray) {
    val temporary = File(file.path + ".tmp")
    temporary.writeBytes(contents)
    check(temporary.renameTo(file)) { "could not store ${file.name}" }
}
