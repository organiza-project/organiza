package org.organiza.sync

import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.organiza.testing.FakeClock
import org.organiza.testing.FakeHttpTransport
import org.organiza.testing.FakeSecretStore

class SupabaseAuthTest {
    private val config = BackendConfig.Supabase(BackendKind.HOSTED, "https://abc.supabase.co", ANON_KEY)
    private val http = FakeHttpTransport()
    private val vault = FakeSecretStore()
    private val clock = FakeClock(Instant.parse("2026-11-02T10:00:00Z"))
    private val auth = SupabaseAuth(http, config, vault, clock)

    private fun session(access: String = "access-1", refresh: String = "refresh-1", expires: Long = 3600) =
        """{"access_token":"$access","token_type":"bearer","expires_in":$expires,"refresh_token":"$refresh",""" +
            """"user":{"id":"7b0c","email":"sam@example.org"}}"""

    @Test
    fun `signing in stores the session and reports who is signed in`() {
        http.respond(200, session())
        assertEquals(AuthResult.SIGNED_IN, auth.signIn(" sam@example.org ", "correct horse"))
        val request = http.requests.single()
        assertEquals("https://abc.supabase.co/auth/v1/token?grant_type=password", request.url)
        assertEquals(ANON_KEY, request.headers["apikey"])
        assertEquals("""{"email":"sam@example.org","password":"correct horse"}""", request.body)
        assertEquals(AuthState.SignedIn("sam@example.org"), auth.state())
        assertEquals("access-1", auth.accessToken())
        assertTrue(vault.names.isNotEmpty())
    }

    @Test
    fun `wrong credentials and unconfirmed emails are told apart`() {
        http.respond(400, """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""")
        assertEquals(AuthResult.WRONG_CREDENTIALS, auth.signIn("sam@example.org", "nope"))
        http.respond(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}""")
        assertEquals(AuthResult.WRONG_CREDENTIALS, auth.signIn("sam@example.org", "nope"))
        http.respond(400, """{"code":400,"error_code":"email_not_confirmed","msg":"Email not confirmed"}""")
        assertEquals(AuthResult.CHECK_EMAIL, auth.signIn("sam@example.org", "pw"))
        assertEquals(AuthState.SignedOut, auth.state())
    }

    @Test
    fun `signing up without a session asks the person to confirm their email`() {
        http.respond(200, """{"id":"7b0c","email":"sam@example.org","confirmation_sent_at":"2026-11-02T10:00:00Z"}""")
        assertEquals(AuthResult.CHECK_EMAIL, auth.signUp("sam@example.org", "a long passphrase"))
        assertEquals("https://abc.supabase.co/auth/v1/signup", http.requests.single().url)
        assertEquals(AuthState.SignedOut, auth.state())
    }

    @Test
    fun `signing up with confirmations off signs straight in`() {
        http.respond(200, session())
        assertEquals(AuthResult.SIGNED_IN, auth.signUp("sam@example.org", "a long passphrase"))
    }

    @Test
    fun `sign-up problems map to plain results`() {
        http.respond(422, """{"code":422,"error_code":"weak_password","msg":"Password should be at least 6 characters."}""")
        assertEquals(AuthResult.WEAK_PASSWORD, auth.signUp("sam@example.org", "a"))
        http.respond(422, """{"code":422,"error_code":"user_already_exists","msg":"User already registered"}""")
        assertEquals(AuthResult.EMAIL_TAKEN, auth.signUp("sam@example.org", "a long passphrase"))
        http.respond(400, """{"code":400,"error_code":"email_address_invalid","msg":"Email address is invalid"}""")
        assertEquals(AuthResult.INVALID_EMAIL, auth.signUp("sam", "a long passphrase"))
        http.respond(429, """{"code":429,"error_code":"over_email_send_rate_limit","msg":"rate limit"}""")
        assertEquals(AuthResult.RATE_LIMITED, auth.signUp("sam@example.org", "a long passphrase"))
        http.respond(503, "")
        assertEquals(AuthResult.UNAVAILABLE, auth.signUp("sam@example.org", "a long passphrase"))
        http.fail(IOException("offline"))
        assertEquals(AuthResult.UNAVAILABLE, auth.signUp("sam@example.org", "a long passphrase"))
    }

    @Test
    fun `an expiring token is refreshed and the new session kept`() {
        http.respond(200, session(expires = 3600))
        auth.signIn("sam@example.org", "pw")
        clock.advance(Duration.ofMinutes(59).plusSeconds(30))
        http.respond(200, session(access = "access-2", refresh = "refresh-2"))
        assertEquals("access-2", auth.accessToken())
        val refresh = http.requests.last()
        assertEquals("https://abc.supabase.co/auth/v1/token?grant_type=refresh_token", refresh.url)
        assertEquals("""{"refresh_token":"refresh-1"}""", refresh.body)
    }

    @Test
    fun `a stored session survives a restart and refreshes on first use`() {
        http.respond(200, session())
        auth.signIn("sam@example.org", "pw")
        val restarted = SupabaseAuth(http, config, vault, clock)
        assertEquals(AuthState.SignedIn("sam@example.org"), restarted.state())
        http.respond(200, session(access = "access-3", refresh = "refresh-3"))
        assertEquals("access-3", restarted.accessToken())
    }

    @Test
    fun `a revoked refresh token signs the person out`() {
        http.respond(200, session(expires = 1))
        auth.signIn("sam@example.org", "pw")
        clock.advance(Duration.ofMinutes(5))
        http.respond(400, """{"code":400,"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token"}""")
        assertNull(auth.accessToken())
        assertEquals(AuthState.SignedOut, auth.state())
        assertTrue(vault.names.isEmpty())
    }

    @Test
    fun `being offline keeps the session for later`() {
        http.respond(200, session(expires = 1))
        auth.signIn("sam@example.org", "pw")
        clock.advance(Duration.ofMinutes(5))
        http.fail(IOException("offline"))
        assertNull(auth.accessToken())
        assertEquals(AuthState.SignedIn("sam@example.org"), auth.state())
    }

    @Test
    fun `a session from another backend is ignored`() {
        http.respond(200, session())
        auth.signIn("sam@example.org", "pw")
        val other = SupabaseAuth(http, config.copy(url = "https://other.supabase.co"), vault, clock)
        assertEquals(AuthState.SignedOut, other.state())
    }

    @Test
    fun `signing out forgets the session even when the server cannot be reached`() {
        http.respond(200, session())
        auth.signIn("sam@example.org", "pw")
        http.fail(IOException("offline"))
        auth.signOut()
        assertEquals(AuthState.SignedOut, auth.state())
        assertTrue(vault.names.isEmpty())
        assertEquals("Bearer access-1", http.requests.last().headers["Authorization"])
    }

    @Test
    fun `a malformed success reply is not a session`() {
        http.respond(200, """{"access_token":5}""")
        assertEquals(AuthResult.UNAVAILABLE, auth.signIn("sam@example.org", "pw"))
    }

    @Test
    fun `local only has no accounts`() {
        assertEquals(AuthResult.NOT_SUPPORTED, LocalOnlyAuth.signIn("sam@example.org", "pw"))
        assertEquals(AuthState.SignedOut, LocalOnlyAuth.state())
        assertNull(LocalOnlyAuth.accessToken())
    }

    @Test
    fun `deleting the account calls the backend function and forgets the session`() {
        http.respond(200, session())
        auth.signIn("sam@example.org", "correct horse")
        http.respond(204, "")
        assertEquals(AuthResult.DELETED, auth.deleteAccount())
        val request = http.requests.last()
        assertEquals("https://abc.supabase.co/rest/v1/rpc/delete_account", request.url)
        assertEquals("Bearer access-1", request.headers["Authorization"])
        assertEquals(AuthState.SignedOut, auth.state())
        assertTrue(vault.names.isEmpty())
    }

    @Test
    fun `a deletion that does not reach the backend keeps the session`() {
        http.respond(200, session())
        auth.signIn("sam@example.org", "correct horse")
        http.respond(500, "")
        assertEquals(AuthResult.UNAVAILABLE, auth.deleteAccount())
        http.fail(IOException("offline"))
        assertEquals(AuthResult.UNAVAILABLE, auth.deleteAccount())
        assertEquals(AuthState.SignedIn("sam@example.org"), auth.state())
        assertEquals(AuthResult.NOT_SUPPORTED, LocalOnlyAuth.deleteAccount())
    }
}
