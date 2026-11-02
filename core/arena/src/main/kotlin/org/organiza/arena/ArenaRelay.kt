package org.organiza.arena

import java.io.IOException
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.organiza.ai.HttpTransport
import org.organiza.ai.post

public class RelayMessage(public val cursor: Long, public val data: ByteArray)

/** A blind mailbox. It holds opaque bytes and has no keys (ADR-0011). */
public interface ArenaRelay {
    /** Throws [IOException] when the relay cannot be reached. */
    public fun append(mailbox: String, data: ByteArray)

    /** Throws [IOException] when the relay cannot be reached. */
    public fun fetch(mailbox: String, after: Long): List<RelayMessage>
}

/** Key storage for challenge secrets. Android: sealed by a Keystore key. */
public interface SecretStore {
    public fun put(name: String, value: ByteArray)

    public fun get(name: String): ByteArray?
}

/** Speaks to `scripts/arena-relay.py` or anything else with the same small protocol. */
public class HttpArenaRelay(private val transport: HttpTransport, baseUrl: String) : ArenaRelay {
    private val base = baseUrl.trimEnd('/')

    override fun append(mailbox: String, data: ByteArray) {
        val reply = transport.post(url(mailbox, "append"), mapOf("Content-Type" to "application/octet-stream"), data)
        if (reply.status !in SUCCESS) throw IOException("relay refused the message: ${reply.status}")
    }

    override fun fetch(mailbox: String, after: Long): List<RelayMessage> {
        val reply = transport.post(url(mailbox, "fetch"), mapOf("Content-Type" to "application/json"), """{"after":$after}""")
        if (reply.status !in SUCCESS) throw IOException("relay refused the fetch: ${reply.status}")
        return try {
            val messages = (Json.parseToJsonElement(reply.body) as JsonObject)["messages"] as JsonArray
            messages.map { element ->
                val message = element as JsonObject
                RelayMessage(
                    (message["cursor"] as JsonPrimitive).longOrNull ?: throw IOException("bad cursor"),
                    Base64.getDecoder().decode((message["data"] as JsonPrimitive).content),
                )
            }
        } catch (failure: IllegalArgumentException) {
            throw IOException("relay reply could not be read", failure)
        } catch (failure: ClassCastException) {
            throw IOException("relay reply could not be read", failure)
        } catch (failure: NullPointerException) {
            throw IOException("relay reply could not be read", failure)
        }
    }

    private fun url(mailbox: String, action: String) = "$base/v1/mailbox/$mailbox/$action"

    private companion object {
        val SUCCESS = 200..299
    }
}
