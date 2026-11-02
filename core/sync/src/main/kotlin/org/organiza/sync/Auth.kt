package org.organiza.sync

import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.organiza.ai.HttpReply
import org.organiza.ai.HttpTransport
import org.organiza.ai.post
import org.organiza.arena.SecretStore
import org.organiza.domain.port.Clock

public sealed interface AuthState {
    public data object SignedOut : AuthState

    public data class SignedIn(val email: String) : AuthState
}

public enum class AuthResult {
    SIGNED_IN,
    CHECK_EMAIL,
    WRONG_CREDENTIALS,
    WEAK_PASSWORD,
    EMAIL_TAKEN,
    INVALID_EMAIL,
    RATE_LIMITED,
    UNAVAILABLE,
    REFUSED,
    NOT_SUPPORTED,
}

/** Accounts for the chosen backend (ADR-0015). Calls block on the network, so keep them off the main thread. */
public interface AuthRepository {
    public fun state(): AuthState

    public fun signUp(email: String, password: String): AuthResult

    public fun signIn(email: String, password: String): AuthResult

    public fun signOut()

    /** A current access token, refreshed when it is about to expire, or null when signed out or offline. */
    public fun accessToken(): String?
}

public object LocalOnlyAuth : AuthRepository {
    override fun state(): AuthState = AuthState.SignedOut

    override fun signUp(email: String, password: String): AuthResult = AuthResult.NOT_SUPPORTED

    override fun signIn(email: String, password: String): AuthResult = AuthResult.NOT_SUPPORTED

    override fun signOut(): Unit = Unit

    override fun accessToken(): String? = null
}

/**
 * Supabase Auth over its REST API. The refresh token is kept in [vault], sealed on Android, and
 * the access token only in memory. A stored session belongs to one backend URL.
 */
public class SupabaseAuth(
    private val transport: HttpTransport,
    private val config: BackendConfig.Supabase,
    private val vault: SecretStore,
    private val clock: Clock,
) : AuthRepository {
    private class Session(val email: String, val refresh: String, val access: String?, val expires: Instant)

    private var session: Session? = loadStored()

    @Synchronized
    override fun state(): AuthState = session?.let { AuthState.SignedIn(it.email) } ?: AuthState.SignedOut

    @Synchronized
    override fun signUp(email: String, password: String): AuthResult = credentials("signup", email, password)

    @Synchronized
    override fun signIn(email: String, password: String): AuthResult =
        credentials("token?grant_type=password", email, password)

    @Synchronized
    override fun signOut() {
        val access = session?.access
        forget()
        if (access == null) return
        try {
            transport.post(auth("logout"), headers() + ("Authorization" to "Bearer $access"), "{}")
        } catch (_: IOException) {
            // The token expires on its own. Signing out locally must never depend on the network.
        }
    }

    @Synchronized
    override fun accessToken(): String? {
        val current = session ?: return null
        if (current.access != null && clock.now().isBefore(current.expires.minus(REFRESH_MARGIN))) return current.access
        val reply = try {
            transport.post(auth("token?grant_type=refresh_token"), headers(), buildJsonObject { put("refresh_token", current.refresh) }.toString())
        } catch (_: IOException) {
            return null
        }
        if (reply.status in SUCCESS) return keep(reply)?.access
        if (reply.status in 400..499) forget()
        return null
    }

    private fun credentials(path: String, email: String, password: String): AuthResult {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }.toString()
        val reply = try {
            transport.post(auth(path), headers(), body)
        } catch (_: IOException) {
            return AuthResult.UNAVAILABLE
        }
        if (reply.status !in SUCCESS) return failure(reply)
        val json = parse(reply.body) ?: return AuthResult.UNAVAILABLE
        // With email confirmation on, sign-up answers with the user and no session.
        if (json["access_token"] == null && json["id"] != null) return AuthResult.CHECK_EMAIL
        return if (keep(reply) != null) AuthResult.SIGNED_IN else AuthResult.UNAVAILABLE
    }

    private fun keep(reply: HttpReply): Session? {
        val json = parse(reply.body) ?: return null
        val access = json["access_token"].text() ?: return null
        val refresh = json["refresh_token"].text() ?: return null
        val lifetime = (json["expires_in"] as? JsonPrimitive)?.longOrNull ?: return null
        val email = ((json["user"] as? JsonObject)?.get("email")).text() ?: return null
        val fresh = Session(email, refresh, access, clock.now().plusSeconds(lifetime))
        session = fresh
        vault.put(VAULT_NAME, buildJsonObject {
            put("url", config.url)
            put("email", email)
            put("refresh", refresh)
        }.toString().toByteArray())
        return fresh
    }

    private fun forget() {
        session = null
        vault.remove(VAULT_NAME)
    }

    private fun loadStored(): Session? {
        val stored = vault.get(VAULT_NAME)?.decodeToString()?.let(::parse) ?: return null
        if (stored["url"].text() != config.url) return null
        val email = stored["email"].text() ?: return null
        val refresh = stored["refresh"].text() ?: return null
        return Session(email, refresh, access = null, expires = Instant.MIN)
    }

    private fun failure(reply: HttpReply): AuthResult {
        val json = parse(reply.body)
        val code = json?.get("error_code").text() ?: json?.get("error").text()
        return when {
            reply.status == 429 -> AuthResult.RATE_LIMITED
            code == "invalid_credentials" || code == "invalid_grant" -> AuthResult.WRONG_CREDENTIALS
            code == "email_not_confirmed" -> AuthResult.CHECK_EMAIL
            code == "weak_password" -> AuthResult.WEAK_PASSWORD
            code == "user_already_exists" || code == "email_exists" -> AuthResult.EMAIL_TAKEN
            code == "email_address_invalid" || code == "validation_failed" -> AuthResult.INVALID_EMAIL
            reply.status in 400..499 -> AuthResult.REFUSED
            else -> AuthResult.UNAVAILABLE
        }
    }

    private fun auth(path: String) = "${config.url}/auth/v1/$path"

    private fun headers() = mapOf("apikey" to config.anonKey, "Content-Type" to "application/json")

    private fun parse(body: String): JsonObject? = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val VAULT_NAME = "sync_session"
        val SUCCESS = 200..299
        val REFRESH_MARGIN: Duration = Duration.ofSeconds(60)
    }
}
