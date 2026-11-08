package org.organiza.sync

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.organiza.ai.UrlConnectionTransport
import org.organiza.arena.ArenaEngine
import org.organiza.arena.ChallengeRules
import org.organiza.arena.ChallengeStatus
import org.organiza.arena.CreateResult
import org.organiza.arena.JoinResult
import org.organiza.arena.SyncResult
import org.organiza.arena.Verification
import org.organiza.storage.HabitStore
import org.organiza.storage.Migrations
import org.organiza.testing.FakeClock
import org.organiza.testing.FakeEnforcementCapabilities
import org.organiza.testing.FakeSecretStore
import org.organiza.testing.FakeUsageHistory
import org.organiza.testing.JdbcDatabase

/**
 * Two phones run a one-day challenge through a Supabase backend over real HTTP: each signs in,
 * publishes its signed commitment, fetches the other's, then reveals and confirms (ADR-0011, ADR-0015).
 * Always runs against [LocalSupabase]; set ORGANIZA_E2E_SUPABASE_URL to run it against a real project.
 */
class ArenaSyncE2ETest {
    private val clock = FakeClock(Instant.now().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(12)))
    private val transport = UrlConnectionTransport()

    private inner class Phone(val name: String, config: BackendConfig.Supabase) {
        val db = JdbcDatabase().also { Migrations.migrate(it) }
        val habits = HabitStore(db).apply { add("h", "Private habit of $name", clock.now()) }
        val auth = SupabaseAuth(transport, config, FakeSecretStore(), clock)
        val sync = SupabaseSync(transport, config, auth)
        val engine = ArenaEngine(
            db, clock, ZoneOffset.UTC, FakeSecretStore(), FakeUsageHistory(), FakeEnforcementCapabilities(),
            allowInsecureRelay = config.url.startsWith("http://"),
        ) { url -> sync.relayFor(url) }

        fun signIn(email: String, password: String, signUpFirst: Boolean) {
            if (signUpFirst) assertTrue(auth.signUp(email, password) in setOf(AuthResult.SIGNED_IN, AuthResult.EMAIL_TAKEN), "$name sign up")
            assertEquals(AuthResult.SIGNED_IN, auth.signIn(email, password), "$name sign in")
        }
    }

    /** Sign in, create and join, close a met day, then sync until both phones confirm both results. */
    private fun runChallenge(config: BackendConfig.Supabase, alice: Pair<String, String>, bob: Pair<String, String>, signUp: Boolean) {
        val phones = listOf(Phone("alice", config), Phone("bob", config))
        val (a, b) = phones
        a.signIn(alice.first, alice.second, signUp)
        b.signIn(bob.first, bob.second, signUp)
        val relayUrl = checkNotNull(a.sync.defaultRelayUrl())
        assertIs<SupabaseArenaRelay>(a.sync.relayFor(relayUrl))

        val today = clock.now().atZone(ZoneOffset.UTC).toLocalDate()
        val created = a.engine.create("h", "One day of reading", today.toString(), ChallengeRules(lengthDays = 1), relayUrl, "alice")
        val invite = assertIs<CreateResult.Created>(created).invite
        assertEquals(JoinResult.Joined(invite.challengeId), b.engine.join(invite.encode(), "h", "bob"))

        phones.forEach { it.habits.mark("h", today, true) }
        clock.advance(Duration.ofDays(1))
        phones.forEach { it.engine.closeDays() }
        // Round one carries the joins and commitments; round two the reveals of the finished challenge.
        repeat(2) {
            phones.forEach { phone -> assertIs<SyncResult.Synced>(phone.engine.sync(invite.challengeId), phone.name) }
        }
        for (phone in phones) {
            val board = phone.engine.leaderboard(invite.challengeId)
            assertEquals(listOf("alice", "bob"), board.map { it.name }.sorted(), phone.name)
            assertTrue(board.all { it.successes == 1 && it.standing.status == ChallengeStatus.COMPLETED }, "${phone.name}: $board")
            assertTrue(board.all { it.verification == Verification.CONFIRMED }, "${phone.name}: $board")
        }
    }

    @Test
    fun `two phones confirm each other's results through a self-hosted supabase`() {
        LocalSupabase().use { backend ->
            val config = BackendConfig.Supabase(BackendKind.SELF_HOSTED, backend.url, backend.anonKey)
            runChallenge(config, "alice@example.org" to "correct horse 1", "bob@example.org" to "correct horse 2", signUp = true)

            val stored = backend.messages()
            assertEquals(setOf(backend.userId("alice@example.org"), backend.userId("bob@example.org")), stored.map { it.first }.toSet())
            assertEquals(1, stored.map { it.second.size }.toSet().size, "every message is padded to one size")
            for ((_, bytes) in stored) {
                val text = String(bytes, Charsets.ISO_8859_1)
                listOf("alice", "bob", "Private", "reading", "success", "commitment").forEach {
                    assertFalse(it in text, "$it is visible to the backend")
                }
            }
        }
    }

    @Test
    fun `a wrong password or a signed-out phone never reaches the relay`() {
        LocalSupabase().use { backend ->
            val config = BackendConfig.Supabase(BackendKind.SELF_HOSTED, backend.url, backend.anonKey)
            val phone = Phone("carol", config)
            assertEquals(AuthResult.SIGNED_IN, phone.auth.signUp("carol@example.org", "right password"))
            phone.auth.signOut()
            assertEquals(AuthResult.WRONG_CREDENTIALS, phone.auth.signIn("carol@example.org", "wrong password"))
            val created = phone.engine.create("h", "Title", clock.now().atZone(ZoneOffset.UTC).toLocalDate().toString(), ChallengeRules(1), backend.url, "carol")
            val id = assertIs<CreateResult.Created>(created).invite.challengeId
            assertEquals(SyncResult.RelayUnavailable, phone.engine.sync(id))
            assertEquals(emptyList(), backend.messages())
        }
    }

    /**
     * The same exchange against a real project, hosted or self-hosted. Needs two accounts:
     * ORGANIZA_E2E_SUPABASE_URL, ORGANIZA_E2E_SUPABASE_KEY (publishable or anon), ORGANIZA_E2E_ALICE and
     * ORGANIZA_E2E_BOB as email:password. ORGANIZA_E2E_SIGN_UP=1 creates the accounts first.
     */
    @Test
    fun `two phones confirm each other's results through a configured supabase project`() {
        val url = System.getenv("ORGANIZA_E2E_SUPABASE_URL")
        assumeTrue(url != null, "ORGANIZA_E2E_SUPABASE_URL is not set")
        val key = checkNotNull(System.getenv("ORGANIZA_E2E_SUPABASE_KEY")) { "ORGANIZA_E2E_SUPABASE_KEY is not set" }
        val check = BackendConfig.supabase(BackendKind.SELF_HOSTED, url!!, key, allowInsecure = true)
        val config = assertIs<BackendCheck.Valid>(check, "backend settings").config
        fun account(name: String): Pair<String, String> {
            val raw = checkNotNull(System.getenv(name)) { "$name is not set" }
            return raw.substringBefore(':') to raw.substringAfter(':')
        }
        runChallenge(config, account("ORGANIZA_E2E_ALICE"), account("ORGANIZA_E2E_BOB"), System.getenv("ORGANIZA_E2E_SIGN_UP") == "1")
    }
}
