package org.organiza.sync

import java.io.IOException
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.organiza.ai.HttpTransport
import org.organiza.ai.post
import org.organiza.relay.Relay
import org.organiza.relay.RelayMessage
import org.organiza.relay.SecretStore
import org.organiza.domain.port.Clock

/** How this person's devices reach each other, whatever the backend (ADR-0015, ADR-0028). */
public interface SyncRepository {
    public val backend: BackendConfig

    /** The backend's blind relay, or null when everything stays on this device. */
    public fun relay(): Relay?
}

public object LocalSync : SyncRepository {
    override val backend: BackendConfig = BackendConfig.LocalOnly

    override fun relay(): Relay? = null
}

public class SupabaseSync(
    private val transport: HttpTransport,
    override val backend: BackendConfig.Supabase,
    private val auth: AuthRepository,
) : SyncRepository {
    override fun relay(): Relay = SupabaseRelay(transport, backend, auth)
}

/** The auth and sync pair for one backend choice. The app swaps the whole pair when the choice changes. */
public class Backend(public val auth: AuthRepository, public val sync: SyncRepository) {
    public companion object {
        public fun create(config: BackendConfig, transport: HttpTransport, vault: SecretStore, clock: Clock): Backend =
            when (config) {
                BackendConfig.LocalOnly -> Backend(LocalOnlyAuth, LocalSync)
                is BackendConfig.Supabase -> {
                    val auth = SupabaseAuth(transport, config, vault, clock)
                    Backend(auth, SupabaseSync(transport, config, auth))
                }
            }
    }
}

/**
 * The blind mailbox over the `arena_append` and `arena_fetch` functions in `supabase/migrations`. They keep
 * their Arena-era names so existing backends need no migration.
 */
public class SupabaseRelay(
    private val transport: HttpTransport,
    private val config: BackendConfig.Supabase,
    private val auth: AuthRepository,
) : Relay {
    override fun append(mailbox: String, data: ByteArray) {
        val body = buildJsonObject {
            put("p_mailbox", mailbox)
            put("p_data", Base64.getEncoder().encodeToString(data))
        }
        call("arena_append", body.toString())
    }

    override fun fetch(mailbox: String, after: Long): List<RelayMessage> {
        val body = buildJsonObject {
            put("p_mailbox", mailbox)
            put("p_after", after)
        }
        val reply = call("arena_fetch", body.toString())
        return try {
            (Json.parseToJsonElement(reply) as JsonArray).map { element ->
                val row = element as JsonObject
                RelayMessage(
                    (row["cursor"] as JsonPrimitive).longOrNull ?: throw IOException("bad cursor"),
                    Base64.getDecoder().decode((row["data"] as JsonPrimitive).content),
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

    private fun call(function: String, body: String): String {
        val token = auth.accessToken() ?: throw IOException("not signed in to the backend")
        val headers = mapOf(
            "apikey" to config.anonKey,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
        )
        val reply = transport.post("${config.url}/rest/v1/rpc/$function", headers, body)
        if (reply.status !in 200..299) throw IOException("backend refused $function: ${reply.status}")
        return reply.body
    }
}
