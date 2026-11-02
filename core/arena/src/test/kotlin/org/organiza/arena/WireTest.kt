package org.organiza.arena

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class WireTest {
    private val member = Fixtures.member("alice")
    private val proof = member.proof("2026-11-02", DayOutcome.SUCCESS, 1, null)

    @Test
    fun `messages round trip`() {
        val join = member.join()
        assertEquals(join, Wire.decode(Wire.encode(join)))
        assertEquals(proof.message, Wire.decode(Wire.encode(proof.message)))
        val reveal = member.reveal(listOf(proof))
        assertEquals(reveal, Wire.decode(Wire.encode(reveal)))
    }

    @Test
    fun `a daily proof carries exactly the allowed fields`() {
        val plain = Wire.encode(proof.message)
        val json = Json.parseToJsonElement(Wire.unpadForTest(plain)).jsonObject
        assertEquals(
            setOf("type", "member", "date", "outcome", "streak", "commitment", "previous", "signature"),
            json.keys,
        )
    }

    @Test
    fun `every message pads to the same size so outcomes cannot be told apart`() {
        val sizes = listOf(
            Wire.encode(member.join()),
            Wire.encode(member.proof("2026-11-02", DayOutcome.SUCCESS, 1, null).message),
            Wire.encode(member.proof("2026-11-02", DayOutcome.FAILURE, 0, ByteArray(32)).message),
            Wire.encode(member.proof("2026-11-02", DayOutcome.FROZEN, 12, ByteArray(32)).message),
        ).map { it.size }.toSet()
        assertEquals(setOf(Wire.BLOCK_BYTES), sizes)
    }

    @Test
    fun `a reveal for a long challenge still fits one block`() {
        var previous: ByteArray? = null
        val proofs = (1..365).map { day ->
            member.proof("2026-%03d".format(day), DayOutcome.SUCCESS, day, previous).also { previous = it.commitment }
        }
        assertTrue(Wire.encode(member.reveal(proofs)).size % Wire.BLOCK_BYTES == 0)
    }

    @Test
    fun `names are limited`() {
        assertEquals(Wire.MAX_NAME_CHARS, Fixtures.member("x".repeat(500)).join().name.length)
    }

    @Test
    fun `junk never decodes and never throws`() {
        assertNull(Wire.decode(ByteArray(0)))
        assertNull(Wire.decode(ByteArray(Wire.BLOCK_BYTES)))
        assertNull(Wire.decode("""{"type":"proof"}""".toByteArray()))
        val random = Random(5)
        val valid = Wire.encode(proof.message)
        repeat(5_000) {
            Wire.decode(random.nextBytes(random.nextInt(0, 200)))
            val mutated = valid.copyOf()
            repeat(4) { mutated[random.nextInt(minOf(300, mutated.size))] = random.nextInt().toByte() }
            Wire.decode(mutated)
        }
    }

    @Test
    fun `invites round trip and refuse anything unsafe`() {
        val invite = Invite(
            challengeId = "c1",
            title = "No Reels",
            startsOn = "2026-11-02",
            rules = ChallengeRules(30, 2, 1, strict = true),
            relayUrl = "https://relay.example",
            secret = ByteArray(32) { 9 },
        )
        val parsed = Invite.parse(invite.encode())
        assertEquals(invite.challengeId, parsed?.challengeId)
        assertEquals(invite.rules, parsed?.rules)
        assertEquals(invite.relayUrl, parsed?.relayUrl)
        assertEquals(invite.secret.toList(), parsed?.secret?.toList())
        assertNull(Invite.parse("not an invite"))
        assertNull(Invite.parse("organiza-arena:1:@@@"))
        assertNull(Invite.parse(invite.copy(relayUrl = "http://relay.example").encode()))
        assertNull(Invite.parse(invite.copy(secret = ByteArray(5)).encode()))
        assertNull(Invite.parse(invite.copy(rules = ChallengeRules(0, 0, 0, false)).encode()))
        assertEquals("http://10.0.2.2:1", Invite.parse(invite.copy(relayUrl = "http://10.0.2.2:1").encode(), allowInsecure = true)?.relayUrl)
    }
}
