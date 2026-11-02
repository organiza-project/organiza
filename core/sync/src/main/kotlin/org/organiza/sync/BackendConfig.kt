package org.organiza.sync

import java.net.URI
import java.net.URISyntaxException
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public enum class BackendKind { HOSTED, SELF_HOSTED }

/** Where accounts and Arena messages go (ADR-0015). Local data never depends on it. */
public sealed interface BackendConfig {
    public data object LocalOnly : BackendConfig

    /** A Supabase project. [anonKey] is public by design, but it still never prints. */
    public data class Supabase(val kind: BackendKind, val url: String, val anonKey: String) : BackendConfig {
        override fun toString(): String = "Supabase(kind=$kind, url=$url, anonKey=redacted)"
    }

    public companion object {
        private val LOOPBACK = setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]")

        /** Checks what a person typed. [allowInsecure] lets debug builds reach a local stack over http. */
        public fun supabase(kind: BackendKind, url: String, anonKey: String, allowInsecure: Boolean): BackendCheck {
            val address = url.trim().trimEnd('/')
            val parsed = try {
                URI(address)
            } catch (_: URISyntaxException) {
                return BackendCheck.Invalid(BackendProblem.MALFORMED_URL)
            }
            val wellFormed = parsed.scheme in setOf("https", "http") && !parsed.host.isNullOrEmpty() &&
                parsed.rawQuery == null && parsed.rawFragment == null && parsed.rawUserInfo == null
            if (!wellFormed) return BackendCheck.Invalid(BackendProblem.MALFORMED_URL)
            if (parsed.scheme == "http" && !(allowInsecure && parsed.host in LOOPBACK)) {
                return BackendCheck.Invalid(BackendProblem.INSECURE_URL)
            }
            val key = anonKey.trim()
            return when (val problem = keyProblem(key)) {
                null -> BackendCheck.Valid(Supabase(kind, address, key))
                else -> BackendCheck.Invalid(problem)
            }
        }

        /** Accepts publishable and anon keys only. A secret or service-role key bypasses row-level security. */
        private fun keyProblem(key: String): BackendProblem? {
            if (key.startsWith("sb_secret_")) return BackendProblem.SECRET_KEY
            if (key.startsWith("sb_publishable_")) {
                return if (key.length > "sb_publishable_".length) null else BackendProblem.MALFORMED_KEY
            }
            val parts = key.split('.')
            if (parts.size != 3) return BackendProblem.MALFORMED_KEY
            val role = try {
                val payload = Json.parseToJsonElement(Base64.getUrlDecoder().decode(parts[1]).decodeToString())
                ((payload as? JsonObject)?.get("role") as? JsonPrimitive)?.content
            } catch (_: IllegalArgumentException) {
                null
            }
            return when (role) {
                "anon" -> null
                "service_role" -> BackendProblem.SECRET_KEY
                else -> BackendProblem.MALFORMED_KEY
            }
        }
    }
}

public enum class BackendProblem { MALFORMED_URL, INSECURE_URL, SECRET_KEY, MALFORMED_KEY }

public sealed interface BackendCheck {
    public data class Valid(val config: BackendConfig.Supabase) : BackendCheck

    public data class Invalid(val problem: BackendProblem) : BackendCheck
}
