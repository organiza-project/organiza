package org.organiza.relay

import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Key derivation and sealed envelopes for anything sent through a relay (ADR-0028). */
public object RelayCrypto {
    private const val HMAC = "HmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    public fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)



    public fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val extract = Mac.getInstance(HMAC).apply { init(SecretKeySpec(salt, HMAC)) }.doFinal(secret)
        val expand = Mac.getInstance(HMAC).apply { init(SecretKeySpec(extract, HMAC)) }
        val out = ByteArrayOutputStream()
        var block = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            expand.update(block)
            expand.update(info)
            expand.update(counter.toByte())
            block = expand.doFinal()
            out.write(block)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    public fun seal(key: ByteArray, plain: ByteArray, associated: ByteArray): ByteArray {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associated)
        return nonce + cipher.doFinal(plain)
    }

    /** Returns null for anything damaged, forged or meant for another group. */
    public fun open(key: ByteArray, sealed: ByteArray, associated: ByteArray): ByteArray? {
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) return null
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
            cipher.updateAAD(associated)
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        } catch (_: GeneralSecurityException) {
            null
        }
    }
}
