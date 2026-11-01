package org.organiza.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Thrown when sealed bytes are damaged or the Keystore key is gone. */
class SealedDataException(cause: Throwable) : Exception(cause)

/** AES-256-GCM with a non-exportable Android Keystore key (ADR-0008, ADR-0009). */
class KeystoreSealer(private val alias: String) {
    fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + cipher.iv.size + sealed.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(sealed)
            .array()
    }

    fun open(contents: ByteArray): ByteArray {
        try {
            val ivLength = contents[0].toInt()
            val spec = GCMParameterSpec(TAG_BITS, contents, 1, ivLength)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), spec) }
            return cipher.doFinal(contents, 1 + ivLength, contents.size - 1 - ivLength)
        } catch (failure: GeneralSecurityException) {
            throw SealedDataException(failure)
        } catch (failure: IndexOutOfBoundsException) {
            throw SealedDataException(failure)
        } catch (failure: IllegalArgumentException) {
            throw SealedDataException(failure)
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
