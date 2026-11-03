package org.organiza.arena

import java.nio.ByteBuffer
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Everything a member may ever send. There is no field for usage data (ADR-0011). */
public sealed interface ArenaMessage {
    public val member: String

    public data class Join(override val member: String, val name: String, val signature: String) : ArenaMessage

    public data class Proof(
        override val member: String,
        val date: String,
        val outcome: DayOutcome,
        val streak: Int,
        val commitment: ByteArray,
        val previous: ByteArray?,
        val signature: ByteArray,
    ) : ArenaMessage {
        override fun equals(other: Any?): Boolean = other is Proof && member == other.member && date == other.date &&
            outcome == other.outcome && streak == other.streak && commitment.contentEquals(other.commitment) &&
            previous.contentEquals(other.previous) && signature.contentEquals(other.signature)

        override fun hashCode(): Int = commitment.contentHashCode() + member.hashCode() * 31
    }

    /** Sent when a member's challenge ends, so the group can check their commitments. */
    public data class Reveal(override val member: String, val salts: Map<String, String>, val signature: String) : ArenaMessage

    /** A friendly nudge to one member on one day (ADR-0018). It carries no results or usage. */
    public data class Nudge(override val member: String, val to: String, val date: String, val signature: String) : ArenaMessage
}

public object Messages {
    private val JOIN_TAG = "organiza-arena-join-v1".toByteArray()
    private val PROOF_TAG = "organiza-arena-proof-v1".toByteArray()
    private val REVEAL_TAG = "organiza-arena-reveal-v1".toByteArray()
    private val NUDGE_TAG = "organiza-arena-nudge-v1".toByteArray()

    public fun memberId(privateKey: ByteArray): String = ArenaCrypto.publicKey(privateKey).toHex()

    public fun join(challengeId: String, privateKey: ByteArray, name: String): ArenaMessage.Join {
        val member = memberId(privateKey)
        val shortName = name.take(Wire.MAX_NAME_CHARS)
        val signature = ArenaCrypto.sign(privateKey, joinBytes(challengeId, member, shortName))
        return ArenaMessage.Join(member, shortName, signature.toHex())
    }

    public fun proof(
        challengeId: String,
        privateKey: ByteArray,
        date: String,
        outcome: DayOutcome,
        streak: Int,
        salt: ByteArray,
        previous: ByteArray?,
    ): ArenaMessage.Proof {
        val member = memberId(privateKey)
        val commitment = Commitments.compute(challengeId, member, date, outcome, streak, salt, previous)
        val unsigned = ArenaMessage.Proof(member, date, outcome, streak, commitment, previous, ByteArray(0))
        return unsigned.copy(signature = ArenaCrypto.sign(privateKey, proofBytes(challengeId, unsigned)))
    }

    public fun reveal(challengeId: String, privateKey: ByteArray, salts: List<Pair<String, ByteArray>>): ArenaMessage.Reveal {
        val member = memberId(privateKey)
        val hex = salts.associate { (date, salt) -> date to salt.toHex() }.toSortedMap()
        val signature = ArenaCrypto.sign(privateKey, revealBytes(challengeId, member, hex))
        return ArenaMessage.Reveal(member, hex, signature.toHex())
    }

    public fun nudge(challengeId: String, privateKey: ByteArray, to: String, date: String): ArenaMessage.Nudge {
        val member = memberId(privateKey)
        val signature = ArenaCrypto.sign(privateKey, nudgeBytes(challengeId, member, to, date))
        return ArenaMessage.Nudge(member, to, date, signature.toHex())
    }

    public fun verify(challengeId: String, message: ArenaMessage): Boolean {
        val key = message.member.hexToBytesOrNull() ?: return false
        return when (message) {
            is ArenaMessage.Join -> {
                val signature = message.signature.hexToBytesOrNull() ?: return false
                ArenaCrypto.verify(key, joinBytes(challengeId, message.member, message.name), signature)
            }
            is ArenaMessage.Proof -> ArenaCrypto.verify(key, proofBytes(challengeId, message), message.signature)
            is ArenaMessage.Reveal -> {
                val signature = message.signature.hexToBytesOrNull() ?: return false
                ArenaCrypto.verify(key, revealBytes(challengeId, message.member, message.salts), signature)
            }
            is ArenaMessage.Nudge -> {
                val signature = message.signature.hexToBytesOrNull() ?: return false
                ArenaCrypto.verify(key, nudgeBytes(challengeId, message.member, message.to, message.date), signature)
            }
        }
    }

    private fun nudgeBytes(challengeId: String, member: String, to: String, date: String) = lengthPrefixed(
        listOf(NUDGE_TAG, challengeId.toByteArray(), member.toByteArray(), to.toByteArray(), date.toByteArray()),
    )

    private fun joinBytes(challengeId: String, member: String, name: String) =
        lengthPrefixed(listOf(JOIN_TAG, challengeId.toByteArray(), member.toByteArray(), name.toByteArray()))

    private fun proofBytes(challengeId: String, proof: ArenaMessage.Proof) = lengthPrefixed(
        listOf(
            PROOF_TAG,
            challengeId.toByteArray(),
            proof.member.toByteArray(),
            proof.date.toByteArray(),
            proof.outcome.wire.toByteArray(),
            proof.streak.toString().toByteArray(),
            proof.commitment,
            proof.previous,
        ),
    )

    private fun revealBytes(challengeId: String, member: String, salts: Map<String, String>) = lengthPrefixed(
        listOf(REVEAL_TAG, challengeId.toByteArray(), member.toByteArray()) +
            salts.toSortedMap().flatMap { (date, salt) -> listOf(date.toByteArray(), salt.toByteArray()) },
    )
}

/** JSON with padding, so that message sizes reveal nothing about their content. */
public object Wire {
    public const val BLOCK_BYTES: Int = 4096
    public const val MAX_NAME_CHARS: Int = 40
    private const val LENGTH_BYTES = 4
    private const val HEX_32_BYTES = 64

    public fun encode(message: ArenaMessage): ByteArray {
        val json = when (message) {
            is ArenaMessage.Join -> buildJsonObject {
                put("type", "join")
                put("member", message.member)
                put("name", message.name)
                put("signature", message.signature)
            }
            is ArenaMessage.Proof -> buildJsonObject {
                put("type", "proof")
                put("member", message.member)
                put("date", message.date)
                put("outcome", message.outcome.wire)
                put("streak", message.streak)
                put("commitment", message.commitment.toHex())
                put("previous", message.previous?.toHex())
                put("signature", message.signature.toHex())
            }
            is ArenaMessage.Reveal -> buildJsonObject {
                put("type", "reveal")
                put("member", message.member)
                putJsonObject("salts") { message.salts.forEach { (date, salt) -> put(date, salt) } }
                put("signature", message.signature)
            }
            is ArenaMessage.Nudge -> buildJsonObject {
                put("type", "nudge")
                put("member", message.member)
                put("to", message.to)
                put("date", message.date)
                put("signature", message.signature)
            }
        }.toString().toByteArray()
        val used = LENGTH_BYTES + json.size
        val blocks = (used + BLOCK_BYTES - 1) / BLOCK_BYTES
        return ByteBuffer.allocate(blocks * BLOCK_BYTES).putInt(json.size).put(json).array()
    }

    /** Returns null for anything malformed, because this input comes from other people. */
    public fun decode(bytes: ByteArray): ArenaMessage? = runCatching {
        val body = unpad(bytes) ?: return null
        val json = Json.parseToJsonElement(body) as JsonObject
        val member = json.text("member")?.takeIf { it.length == HEX_32_BYTES } ?: return null
        when (json.text("type")) {
            "join" -> ArenaMessage.Join(
                member,
                json.text("name")?.take(MAX_NAME_CHARS) ?: return null,
                json.text("signature") ?: return null,
            )
            "proof" -> ArenaMessage.Proof(
                member,
                json.text("date") ?: return null,
                DayOutcome.entries.firstOrNull { it.wire == json.text("outcome") } ?: return null,
                (json["streak"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 } ?: return null,
                json.text("commitment")?.hexToBytesOrNull()?.takeIf { it.size == 32 } ?: return null,
                json.text("previous")?.let { it.hexToBytesOrNull()?.takeIf { bytes -> bytes.size == 32 } ?: return null },
                json.text("signature")?.hexToBytesOrNull() ?: return null,
            )
            "reveal" -> ArenaMessage.Reveal(
                member,
                (json["salts"] as? JsonObject)?.mapValues { (_, value) ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                } ?: return null,
                json.text("signature") ?: return null,
            )
            "nudge" -> ArenaMessage.Nudge(
                member,
                json.text("to")?.takeIf { it.length == HEX_32_BYTES } ?: return null,
                json.text("date") ?: return null,
                json.text("signature") ?: return null,
            )
            else -> null
        }
    }.getOrNull()

    internal fun unpadForTest(bytes: ByteArray): String = checkNotNull(unpad(bytes))

    private fun unpad(bytes: ByteArray): String? {
        if (bytes.size < LENGTH_BYTES) return null
        val length = ByteBuffer.wrap(bytes).int
        if (length <= 0 || length > bytes.size - LENGTH_BYTES) return null
        return String(bytes, LENGTH_BYTES, length)
    }

    private fun JsonObject.text(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** A shareable invitation. Holding one is the only requirement to join (ADR-0011). */
public data class Invite(
    val challengeId: String,
    val title: String,
    val startsOn: String,
    val rules: ChallengeRules,
    val relayUrl: String,
    val secret: ByteArray,
) {
    public fun encode(): String {
        val json = buildJsonObject {
            put("challenge", challengeId)
            put("title", title)
            put("startsOn", startsOn)
            put("days", rules.lengthDays)
            put("freezes", rules.freezeAllowance)
            put("maxFailures", rules.maxFailures)
            put("strict", rules.strict)
            put("relay", relayUrl)
            put("secret", secret.toHex())
        }.toString()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
    }

    public companion object {
        private const val PREFIX = "organiza-arena:1:"
        private const val MAX_DAYS = 365
        private const val SECRET_BYTES = 32

        /** [allowInsecure] is for tests and debug builds with a local relay. */
        public fun parse(text: String, allowInsecure: Boolean = false): Invite? {
            val trimmed = text.trim()
            if (!trimmed.startsWith(PREFIX)) return null
            return runCatching {
                val decoded = Base64.getUrlDecoder().decode(trimmed.removePrefix(PREFIX))
                fromJson(Json.parseToJsonElement(String(decoded)) as JsonObject, allowInsecure)
            }.getOrNull()
        }

        private fun fromJson(json: JsonObject, allowInsecure: Boolean): Invite? {
            fun text(name: String) = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun number(name: String) = (json[name] as? JsonPrimitive)?.intOrNull
            val rules = ChallengeRules(
                number("days")?.takeIf { it in 1..MAX_DAYS } ?: return null,
                number("freezes")?.takeIf { it >= 0 } ?: return null,
                number("maxFailures")?.takeIf { it >= 0 } ?: return null,
                (json["strict"] as? JsonPrimitive)?.booleanOrNull ?: return null,
            )
            val relay = text("relay") ?: return null
            val allowed = relay.startsWith("https://") || (allowInsecure && relay.startsWith("http://"))
            if (!allowed) return null
            val startsOn = text("startsOn") ?: return null
            try {
                LocalDate.parse(startsOn)
            } catch (_: DateTimeException) {
                return null
            }
            return Invite(
                text("challenge")?.takeIf(String::isNotBlank) ?: return null,
                text("title") ?: return null,
                startsOn,
                rules,
                relay,
                text("secret")?.hexToBytesOrNull()?.takeIf { it.size == SECRET_BYTES } ?: return null,
            )
        }
    }
}
