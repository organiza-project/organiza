package org.organiza.sync

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.organiza.arena.ArenaCrypto
import org.organiza.arena.ArenaRelay

/**
 * A secret this person's devices share, and what it derives (ADR-0028). The relay learns only
 * [mailboxId]; the secret moves between devices as [code], copied by the person like an invite.
 */
public class DeviceLink private constructor(private val secret: ByteArray) {
    private val salt = "organiza-devices-v1".toByteArray()
    public val mailboxId: String = ArenaCrypto.hkdf(secret, salt, "mailbox".toByteArray(), KEY_BYTES).joinToString("") { "%02x".format(it) }
    private val key = ArenaCrypto.hkdf(secret, salt, "envelope".toByteArray(), KEY_BYTES)
    private val associated = salt + mailboxId.toByteArray()

    public val code: String get() = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret)

    /** Today's minutes, padded to whole kilobytes so the relay cannot tell how many apps there are. */
    internal fun seal(report: DeviceReport): ByteArray {
        val entries = report.minutes.entries.sortedByDescending { it.value }.toMutableList()
        var json = encode(report, entries)
        // The relay takes at most 8 KB; the least used apps are left out first.
        while (json.size > MAX_PLAIN && entries.isNotEmpty()) json = encode(report, entries.also { it.removeAt(it.lastIndex) })
        val padded = json + ByteArray((BLOCK - json.size % BLOCK) % BLOCK) { ' '.code.toByte() }
        return ArenaCrypto.seal(key, padded, associated)
    }

    /** Null for anything damaged, from another link, or not a report. */
    internal fun open(data: ByteArray): DeviceReport? {
        val plain = ArenaCrypto.open(key, data, associated) ?: return null
        return try {
            val root = Json.parseToJsonElement(plain.decodeToString()) as JsonObject
            DeviceReport(
                (root["device"] as JsonPrimitive).content,
                LocalDate.parse((root["day"] as JsonPrimitive).content),
                (root["minutes"] as JsonObject).mapValues { (_, value) -> (value as JsonPrimitive).longOrNull ?: 0 },
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ClassCastException) {
            null
        } catch (_: NullPointerException) {
            null
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun encode(report: DeviceReport, entries: List<Map.Entry<String, Long>>): ByteArray = buildJsonObject {
        put("device", report.device)
        put("day", report.day.toString())
        putJsonObject("minutes") { entries.forEach { (app, minutes) -> put(app, minutes) } }
    }.toString().toByteArray()

    public companion object {
        private const val PREFIX = "organiza-devices:"
        private const val KEY_BYTES = 32
        private const val BLOCK = 1024
        private const val MAX_PLAIN = 7 * BLOCK

        public fun create(): DeviceLink = DeviceLink(ArenaCrypto.randomBytes(KEY_BYTES))

        /** The link a code from another device carries, or null when it is not one. */
        public fun fromCode(code: String): DeviceLink? {
            val text = code.trim().takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return null
            val secret = try {
                Base64.getUrlDecoder().decode(text)
            } catch (_: IllegalArgumentException) {
                return null
            }
            return secret.takeIf { it.size == KEY_BYTES }?.let(::DeviceLink)
        }
    }
}

/** One device's minutes per app for one day, apps named in lower case so every OS agrees. */
public data class DeviceReport(val device: String, val day: LocalDate, val minutes: Map<String, Long>)

/**
 * The exchange through a relay (ADR-0028): this device posts its day, and keeps the latest day of
 * every other device. Reports carry the whole day, so a missed one is mended by the next.
 * [cursor] is where the last pull stopped; the caller keeps it between runs.
 */
public class DeviceUsage(
    private val link: DeviceLink,
    private val self: String,
    private val relay: ArenaRelay,
    cursor: Long = 0,
) {
    public var cursor: Long = cursor
        private set

    /** The latest report of each other device for each day, yesterday and later. */
    private val latest = mutableMapOf<Pair<String, LocalDate>, DeviceReport>()

    /** Throws [java.io.IOException] when the relay cannot be reached. */
    @Synchronized
    public fun post(day: LocalDate, minutes: Map<String, Long>) {
        val report = DeviceReport(self, day, minutes.entries.groupBy({ key(it.key) }, { it.value }).mapValues { it.value.sum() })
        relay.append(link.mailboxId, link.seal(report))
    }

    /** Reads every report after [cursor]. Throws [java.io.IOException] when the relay cannot be reached. */
    @Synchronized
    public fun pull() {
        while (true) {
            val messages = relay.fetch(link.mailboxId, cursor)
            if (messages.isEmpty()) return
            for (message in messages) {
                cursor = maxOf(cursor, message.cursor)
                val report = link.open(message.data)?.takeIf { it.device != self } ?: continue
                // Messages come in the order they were sent, so each replaces the last of its device and day.
                latest[report.device to report.day] = report
            }
            // Devices in other time zones may still be on yesterday; anything older is done with.
            val oldest = latest.keys.maxOfOrNull { it.second }?.minusDays(1)
            if (oldest != null) latest.keys.removeAll { it.second.isBefore(oldest) }
        }
    }

    /** Minutes of [app], by its name, on the other devices on [day]. */
    @Synchronized
    public fun minutes(app: String, day: LocalDate): Long = latest.values.filter { it.day == day }.sumOf { it.minutes[key(app)] ?: 0 }

    /** How many other devices reported [day]. */
    @Synchronized
    public fun devices(day: LocalDate): Int = latest.values.count { it.day == day }

    private fun key(app: String) = app.trim().lowercase()
}
