package org.organiza.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class StorageFailure { KEY_MISSING, KEY_UNREADABLE, DATABASE_UNREADABLE, DATABASE_TOO_NEW }

class StorageException(val failure: StorageFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/** A random database key, wrapped by a non-exportable Keystore key (ADR-0008). */
class DatabaseKey(context: Context, private val database: File) {
    private val wrapped = File(context.noBackupFilesDir, "database.key")

    /** Returns the raw key. Never replaces a key while a database that needs it exists. */
    fun load(): ByteArray {
        if (wrapped.exists()) return unwrap()
        if (database.exists()) throw StorageException(StorageFailure.KEY_MISSING)
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val sealed = cipher.doFinal(key)
        val contents = ByteBuffer.allocate(1 + cipher.iv.size + sealed.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(sealed)
            .array()
        val temporary = File(wrapped.path + ".tmp")
        temporary.writeBytes(contents)
        check(temporary.renameTo(wrapped)) { "could not store the database key" }
        return key
    }

    private fun unwrap(): ByteArray {
        try {
            val contents = wrapped.readBytes()
            val ivLength = contents[0].toInt()
            val spec = GCMParameterSpec(TAG_BITS, contents, 1, ivLength)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, wrappingKey(), spec) }
            return cipher.doFinal(contents, 1 + ivLength, contents.size - 1 - ivLength)
        } catch (failure: GeneralSecurityException) {
            throw StorageException(StorageFailure.KEY_UNREADABLE, failure)
        } catch (failure: IndexOutOfBoundsException) {
            throw StorageException(StorageFailure.KEY_UNREADABLE, failure)
        } catch (failure: IllegalArgumentException) {
            throw StorageException(StorageFailure.KEY_UNREADABLE, failure)
        }
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(WRAPPING_KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "organiza_database_wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val TAG_BITS = 128
        const val WRAPPING_KEY_BITS = 256
    }
}
