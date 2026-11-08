package org.organiza.android.feature.sync

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import org.organiza.ai.UrlConnectionTransport
import org.organiza.android.BuildConfig
import org.organiza.android.OrganizaApp
import org.organiza.android.data.KeystoreSecretStore
import org.organiza.ui.AccountView
import org.organiza.sync.AuthResult
import org.organiza.sync.AuthState
import org.organiza.sync.Backend
import org.organiza.sync.BackendCheck
import org.organiza.sync.BackendConfig
import org.organiza.sync.BackendKind
import org.organiza.sync.BackendProblem

/**
 * Holds the backend choice and its auth and sync pair (ADR-0015). Screens and Arena only see
 * [backend], so switching between local only, hosted and self-hosted changes nothing else.
 */
class SyncController(private val app: OrganizaApp) {
    private val prefs = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val vault = KeystoreSecretStore(app)
    private val transport = UrlConnectionTransport()

    // Sign-in can take seconds, so it runs here and never on the storage or main thread.
    private val network = Executors.newSingleThreadExecutor()

    @Volatile private var busy = false

    /** The hosted project set at build time, or null when this build has none. */
    val hosted: BackendConfig.Supabase? = (
        BackendConfig.supabase(BackendKind.HOSTED, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, BuildConfig.DEBUG)
            as? BackendCheck.Valid
        )?.config

    @Volatile var backend: Backend = Backend.create(stored(), transport, vault, app.clock)
        private set

    fun view(): AccountView {
        val config = backend.sync.backend as? BackendConfig.Supabase
        return AccountView(
            kind = config?.kind,
            url = config?.url.orEmpty(),
            signedInAs = (backend.auth.state() as? AuthState.SignedIn)?.email,
            hostedAvailable = hosted != null,
            busy = busy,
        )
    }

    fun choose(kind: BackendKind?, url: String, key: String) {
        val config: BackendConfig = when (kind) {
            null -> BackendConfig.LocalOnly
            BackendKind.HOSTED -> hosted ?: return app.showMessage("The hosted backend is not available in this build")
            BackendKind.SELF_HOSTED -> when (val check = BackendConfig.supabase(kind, url, key, BuildConfig.DEBUG)) {
                is BackendCheck.Valid -> check.config
                is BackendCheck.Invalid -> return app.showMessage(problem(check.problem))
            }
        }
        onNetwork {
            backend.auth.signOut()
            save(config)
            backend = Backend.create(config, transport, vault, app.clock)
            app.showMessage(if (config == BackendConfig.LocalOnly) "Using this phone only" else "Backend saved. Sign in to sync")
        }
    }

    fun signIn(email: String, password: String) = onNetwork { app.showMessage(result(backend.auth.signIn(email, password))) }

    fun signUp(email: String, password: String) = onNetwork { app.showMessage(result(backend.auth.signUp(email, password))) }

    /** Deletes the account and its cloud data. The phone's own data stays (ADR-0020). */
    fun deleteAccount() = onNetwork { app.showMessage(result(backend.auth.deleteAccount())) }

    fun signOut() = onNetwork {
        backend.auth.signOut()
        app.showMessage("Signed out")
    }

    private fun onNetwork(work: () -> Unit) {
        busy = true
        app.requestUiRefresh()
        network.execute {
            try {
                work()
            } catch (failure: RuntimeException) {
                Log.e(TAG, "sync work failed: ${failure.javaClass.simpleName}")
                app.showMessage("Sync error")
            } finally {
                busy = false
                app.requestUiRefresh()
            }
        }
    }

    private fun stored(): BackendConfig {
        val kind = prefs.getString(KIND, null)?.let { runCatching { BackendKind.valueOf(it) }.getOrNull() }
        return when (kind) {
            null -> BackendConfig.LocalOnly
            BackendKind.HOSTED -> hosted ?: BackendConfig.LocalOnly
            BackendKind.SELF_HOSTED -> (
                BackendConfig.supabase(kind, prefs.getString(URL, "").orEmpty(), prefs.getString(KEY, "").orEmpty(), BuildConfig.DEBUG)
                    as? BackendCheck.Valid
                )?.config ?: BackendConfig.LocalOnly
        }
    }

    private fun save(config: BackendConfig) {
        val editor = prefs.edit().clear()
        if (config is BackendConfig.Supabase) {
            editor.putString(KIND, config.kind.name)
            // Only a self-hosted project is stored. The hosted one always comes from the build.
            if (config.kind == BackendKind.SELF_HOSTED) editor.putString(URL, config.url).putString(KEY, config.anonKey)
        }
        editor.apply()
    }

    private fun problem(problem: BackendProblem) = when (problem) {
        BackendProblem.MALFORMED_URL -> "That is not a project URL"
        BackendProblem.INSECURE_URL -> "The project URL must start with https://"
        BackendProblem.SECRET_KEY -> "That is a secret key. Use the anon or publishable key, never the service role key"
        BackendProblem.MALFORMED_KEY -> "That does not look like an anon or publishable key"
    }

    private fun result(result: AuthResult) = when (result) {
        AuthResult.SIGNED_IN -> "Signed in"
        AuthResult.CHECK_EMAIL -> "Check your email to confirm the account, then sign in"
        AuthResult.WRONG_CREDENTIALS -> "Wrong email or password"
        AuthResult.WEAK_PASSWORD -> "Choose a longer password"
        AuthResult.EMAIL_TAKEN -> "That email already has an account. Sign in instead"
        AuthResult.INVALID_EMAIL -> "That email address is not valid"
        AuthResult.RATE_LIMITED -> "Too many attempts. Try again in a few minutes"
        AuthResult.UNAVAILABLE -> "The backend could not be reached"
        AuthResult.REFUSED -> "The backend refused the request"
        AuthResult.NOT_SUPPORTED -> "Choose a backend first"
        AuthResult.DELETED -> "Account and cloud data deleted. Nothing of yours is left on the backend"
    }

    private companion object {
        const val TAG = "Organiza"
        const val PREFERENCES = "organiza_backend"
        const val KIND = "kind"
        const val URL = "url"
        const val KEY = "anon_key"
    }
}
