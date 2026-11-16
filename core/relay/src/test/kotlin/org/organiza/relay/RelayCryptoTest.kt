package org.organiza.relay

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayCryptoTest {
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `hkdf matches rfc 5869 test case 1`() {
        val okm = RelayCrypto.hkdf(
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
    fun `envelopes round trip and reject tampering wrong keys and wrong groups`() {
        val key = ByteArray(32) { 1 }
        val sealed = RelayCrypto.seal(key, "secret".toByteArray(), "group".toByteArray())
        assertContentEquals("secret".toByteArray(), RelayCrypto.open(key, sealed, "group".toByteArray()))
        assertNull(RelayCrypto.open(ByteArray(32) { 2 }, sealed, "group".toByteArray()))
        assertNull(RelayCrypto.open(key, sealed, "other".toByteArray()))
        val flipped = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(RelayCrypto.open(key, flipped, "group".toByteArray()))
        assertNull(RelayCrypto.open(key, ByteArray(4), "group".toByteArray()))
    }

    @Test
    fun `sealing the same text twice looks different`() {
        val key = ByteArray(32) { 1 }
        val text = "same".toByteArray()
        assertNotEquals(
            RelayCrypto.seal(key, text, ByteArray(0)).toList(),
            RelayCrypto.seal(key, text, ByteArray(0)).toList(),
        )
    }

}
