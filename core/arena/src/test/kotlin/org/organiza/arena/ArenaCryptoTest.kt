package org.organiza.arena

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaCryptoTest {
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `hkdf matches rfc 5869 test case 1`() {
        val okm = ArenaCrypto.hkdf(
            secret = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `ed25519 matches rfc 8032 test 1`() {
        val private = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val public = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val signature = ArenaCrypto.sign(private, ByteArray(0))
        assertEquals(
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
            signature.joinToString("") { "%02x".format(it) },
        )
        assertContentEquals(public, ArenaCrypto.publicKey(private))
        assertTrue(ArenaCrypto.verify(public, ByteArray(0), signature))
    }

    @Test
    fun `signatures fail on any change and never throw on junk`() {
        val private = ArenaCrypto.newPrivateKey()
        val public = ArenaCrypto.publicKey(private)
        val signature = ArenaCrypto.sign(private, "hello".toByteArray())
        assertTrue(ArenaCrypto.verify(public, "hello".toByteArray(), signature))
        assertFalse(ArenaCrypto.verify(public, "hellp".toByteArray(), signature))
        assertFalse(ArenaCrypto.verify(ByteArray(3), "hello".toByteArray(), signature))
        assertFalse(ArenaCrypto.verify(public, "hello".toByteArray(), ByteArray(5)))
    }

    @Test
    fun `envelopes round trip and reject tampering wrong keys and wrong groups`() {
        val key = ByteArray(32) { 1 }
        val sealed = ArenaCrypto.seal(key, "secret".toByteArray(), "group".toByteArray())
        assertContentEquals("secret".toByteArray(), ArenaCrypto.open(key, sealed, "group".toByteArray()))
        assertNull(ArenaCrypto.open(ByteArray(32) { 2 }, sealed, "group".toByteArray()))
        assertNull(ArenaCrypto.open(key, sealed, "other".toByteArray()))
        val flipped = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(ArenaCrypto.open(key, flipped, "group".toByteArray()))
        assertNull(ArenaCrypto.open(key, ByteArray(4), "group".toByteArray()))
    }

    @Test
    fun `sealing the same text twice looks different`() {
        val key = ByteArray(32) { 1 }
        val text = "same".toByteArray()
        assertNotEquals(
            ArenaCrypto.seal(key, text, ByteArray(0)).toList(),
            ArenaCrypto.seal(key, text, ByteArray(0)).toList(),
        )
    }

    @Test
    fun `group keys separate mailbox from encryption and depend on the secret`() {
        val first = GroupKeys(ByteArray(32) { 7 })
        val second = GroupKeys(ByteArray(32) { 8 })
        assertNotEquals(first.mailboxId, second.mailboxId)
        assertFalse(first.encryptionKey.joinToString("") { "%02x".format(it) }.startsWith(first.mailboxId.take(16)))
        assertEquals(64, first.mailboxId.length)
        assertEquals(32, first.encryptionKey.size)
    }

    @Test
    fun `commitments bind every field without ambiguous boundaries`() {
        val salt = ByteArray(32) { 3 }
        val base = Commitments.compute("c", "m", "2026-11-02", DayOutcome.SUCCESS, 1, salt, null)
        assertEquals(32, base.size)
        assertContentEquals(base, Commitments.compute("c", "m", "2026-11-02", DayOutcome.SUCCESS, 1, salt, null))
        val variants = listOf(
            Commitments.compute("cm", "", "2026-11-02", DayOutcome.SUCCESS, 1, salt, null),
            Commitments.compute("c", "m", "2026-11-03", DayOutcome.SUCCESS, 1, salt, null),
            Commitments.compute("c", "m", "2026-11-02", DayOutcome.FAILURE, 1, salt, null),
            Commitments.compute("c", "m", "2026-11-02", DayOutcome.SUCCESS, 2, salt, null),
            Commitments.compute("c", "m", "2026-11-02", DayOutcome.SUCCESS, 1, ByteArray(32) { 4 }, null),
            Commitments.compute("c", "m", "2026-11-02", DayOutcome.SUCCESS, 1, salt, ByteArray(32)),
        )
        variants.forEach { assertNotEquals(base.toList(), it.toList()) }
    }
}
