package org.organiza.arena

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0 || any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/** Writes fields so that no two different field lists can produce the same bytes. */
internal fun lengthPrefixed(fields: List<ByteArray?>): ByteArray {
    val out = ByteArrayOutputStream()
    for (field in fields) {
        out.write(ByteBuffer.allocate(4).putInt(field?.size ?: -1).array())
        field?.let(out::write)
    }
    return out.toByteArray()
}

public object ArenaCrypto {
    private const val HMAC = "HmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32
    private const val SIGNATURE_BYTES = 64
    private val random = SecureRandom()

    public fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    public fun newPrivateKey(): ByteArray = randomBytes(KEY_BYTES)

    public fun publicKey(privateKey: ByteArray): ByteArray =
        Ed25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded

    public fun sign(privateKey: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    /** Returns false, never throws, for keys and signatures that came from a peer. */
    public fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != KEY_BYTES || signature.size != SIGNATURE_BYTES) return false
        return try {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

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

/** Everything derived from an invite's secret. The relay only ever learns [mailboxId]. */
public class GroupKeys(secret: ByteArray) {
    private val salt = "organiza-arena-v1".toByteArray()

    public val mailboxId: String = ArenaCrypto.hkdf(secret, salt, "mailbox".toByteArray(), 32).toHex()
    public val encryptionKey: ByteArray = ArenaCrypto.hkdf(secret, salt, "envelope".toByteArray(), 32)
    internal val associatedData: ByteArray = salt + mailboxId.toByteArray()
}

public object Commitments {
    private val TAG = "organiza-arena-commitment-v1".toByteArray()

    public const val SALT_BYTES: Int = 32

    /** ADR-0006: a hash commitment, which shows a result was not rewritten, not that it was true. */
    public fun compute(
        challengeId: String,
        memberId: String,
        date: String,
        outcome: DayOutcome,
        streak: Int,
        salt: ByteArray,
        previous: ByteArray?,
    ): ByteArray = MessageDigest.getInstance("SHA-256").digest(
        lengthPrefixed(
            listOf(
                TAG,
                challengeId.toByteArray(),
                memberId.toByteArray(),
                date.toByteArray(),
                outcome.wire.toByteArray(),
                streak.toString().toByteArray(),
                salt,
                previous,
            ),
        ),
    )
}
