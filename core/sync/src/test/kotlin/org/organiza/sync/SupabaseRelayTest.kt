package org.organiza.sync

import java.io.IOException
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.organiza.arena.HttpArenaRelay
import org.organiza.testing.FakeClock
import org.organiza.testing.FakeHttpTransport
import org.organiza.testing.FakeSecretStore

class SupabaseRelayTest {
    private val config = BackendConfig.Supabase(BackendKind.HOSTED, "https://abc.supabase.co", ANON_KEY)
    private val http = FakeHttpTransport()
    private val clock = FakeClock(Instant.parse("2026-11-02T10:00:00Z"))
    private val auth = SupabaseAuth(http, config, FakeSecretStore(), clock)
    private val relay = SupabaseArenaRelay(http, config, auth)
    private val mailbox = "a".repeat(64)

    private fun signIn() {
        http.respond(200, """{"access_token":"tok","expires_in":3600,"refresh_token":"r","user":{"id":"u","email":"sam@example.org"}}""")
        auth.signIn("sam@example.org", "pw")
    }

    @Test
    fun `append calls the rpc with the user's token`() {
        signIn()
        http.respond(200, "42")
        relay.append(mailbox, byteArrayOf(1, 2, 3))
        val request = http.requests.last()
        assertEquals("https://abc.supabase.co/rest/v1/rpc/arena_append", request.url)
        assertEquals("Bearer tok", request.headers["Authorization"])
        assertEquals(ANON_KEY, request.headers["apikey"])
        assertEquals("""{"p_mailbox":"$mailbox","p_data":"AQID"}""", request.body)
    }

    @Test
    fun `fetch decodes the rows in order`() {
        signIn()
        val first = Base64.getEncoder().encodeToString(byteArrayOf(9))
        http.respond(200, """[{"cursor":3,"data":"$first"},{"cursor":7,"data":"AQID"}]""")
        val messages = relay.fetch(mailbox, 2)
        assertEquals("""{"p_mailbox":"$mailbox","p_after":2}""", http.requests.last().body)
        assertEquals(listOf(3L, 7L), messages.map { it.cursor })
        assertContentEquals(byteArrayOf(1, 2, 3), messages[1].data)
    }

    @Test
    fun `without a session the relay is unavailable and nothing is sent`() {
        assertFailsWith<IOException> { relay.append(mailbox, byteArrayOf(1)) }
        assertEquals(0, http.requests.size)
    }

    @Test
    fun `refusals and bad replies are relay failures`() {
        signIn()
        http.respond(401, """{"message":"JWT expired"}""")
        assertFailsWith<IOException> { relay.append(mailbox, byteArrayOf(1)) }
        http.respond(200, """{"not":"a list"}""")
        assertFailsWith<IOException> { relay.fetch(mailbox, 0) }
        http.respond(200, """[{"cursor":"x","data":"AQID"}]""")
        assertFailsWith<IOException> { relay.fetch(mailbox, 0) }
    }

    @Test
    fun `the backend's own url routes to supabase, any other to a plain relay`() {
        val sync = SupabaseSync(http, config, auth)
        assertIs<SupabaseArenaRelay>(sync.relayFor("https://abc.supabase.co"))
        assertIs<HttpArenaRelay>(sync.relayFor("https://relay.example.org"))
        assertEquals("https://abc.supabase.co", sync.defaultRelayUrl())
    }

    @Test
    fun `local only always uses the relay in the invite`() {
        val sync = LocalSync(http)
        assertIs<HttpArenaRelay>(sync.relayFor("https://relay.example.org"))
        assertNull(sync.defaultRelayUrl())
    }

    @Test
    fun `one factory builds the matching pair for each choice`() {
        val local = Backend.create(BackendConfig.LocalOnly, http, FakeSecretStore(), clock)
        assertEquals(LocalOnlyAuth, local.auth)
        assertIs<LocalSync>(local.sync)
        val hosted = Backend.create(config, http, FakeSecretStore(), clock)
        assertIs<SupabaseAuth>(hosted.auth)
        assertIs<SupabaseSync>(hosted.sync)
    }
}
