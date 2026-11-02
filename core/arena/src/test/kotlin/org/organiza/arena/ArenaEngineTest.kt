package org.organiza.arena

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.Capability
import org.organiza.storage.ArenaStore
import org.organiza.storage.HabitCriterion
import org.organiza.storage.HabitKind
import org.organiza.storage.HabitStore
import org.organiza.storage.Migrations
import org.organiza.storage.TamperStore
import org.organiza.testing.FakeArenaRelay
import org.organiza.testing.FakeClock
import org.organiza.testing.FakeEnforcementCapabilities
import org.organiza.testing.FakeSecretStore
import org.organiza.testing.FakeUsageHistory
import org.organiza.testing.JdbcDatabase

class ArenaEngineTest {
    private val day0 = LocalDate.of(2026, 11, 2)
    private val noon = day0.atTime(12, 0).toInstant(ZoneOffset.UTC)
    private val clock = FakeClock(noon)
    private val relay = FakeArenaRelay()
    private val insta = (PackageName.parse("com.instagram.android") as Parsed.Valid).value

    private inner class Device(val name: String) {
        val db = JdbcDatabase().also { Migrations.migrate(it) }
        val habits = HabitStore(db).apply { add("h", "Secret habit $name", noon) }
        val tamper = TamperStore(db)
        val usage = FakeUsageHistory()
        val capabilities = FakeEnforcementCapabilities()
        val secrets = FakeSecretStore()
        val store = ArenaStore(db)
        val engine = newEngine()

        fun newEngine() = ArenaEngine(db, clock, ZoneOffset.UTC, secrets, usage, capabilities, allowInsecureRelay = false) { relay }

        fun markMet(offset: Long) = habits.mark("h", day0.plusDays(offset), true)
    }

    private val alice = Device("alice")
    private val bob = Device("bob")
    private val rules = ChallengeRules(lengthDays = 3, freezeAllowance = 1, maxFailures = 0, strict = true)

    private fun create(rules: ChallengeRules = this.rules, device: Device = alice): Invite =
        (device.engine.create("h", "Title text", day0.toString(), rules, "https://relay.example", device.name) as CreateResult.Created).invite

    private fun nextDay(days: Long = 1) = clock.advance(Duration.ofDays(days))

    private fun twoMembers(rules: ChallengeRules = this.rules): String {
        val invite = create(rules)
        assertEquals(JoinResult.Joined(invite.challengeId), bob.engine.join(invite.encode(), "h", "bob"))
        return invite.challengeId
    }

    @Test
    fun `today is never closed and closing twice changes nothing`() {
        val id = twoMembers()
        alice.engine.closeDays()
        assertEquals(emptyList(), alice.store.logs(id))
        alice.markMet(0)
        nextDay()
        alice.engine.closeDays()
        alice.engine.closeDays()
        assertEquals(listOf(day0.toString()), alice.store.logs(id).map { it.date })
        assertEquals(1, alice.store.logs(id).single().streak)
    }

    @Test
    fun `days missed while the phone was off are all closed`() {
        val id = twoMembers(rules.copy(lengthDays = 5, maxFailures = 5))
        nextDay(3)
        alice.engine.closeDays()
        assertEquals(listOf("frozen", "failure", "failure"), alice.store.logs(id).map { it.outcome })
    }

    @Test
    fun `a clock moved backwards reopens nothing`() {
        val id = twoMembers()
        alice.markMet(0)
        nextDay()
        alice.engine.closeDays()
        clock.advance(Duration.ofDays(-5))
        alice.engine.closeDays()
        assertEquals(1, alice.store.logs(id).size)
    }

    @Test
    fun `changing a habit after the day closed changes nothing`() {
        val id = twoMembers()
        nextDay()
        alice.engine.closeDays()
        assertEquals("frozen", alice.store.logs(id).single().outcome)
        alice.markMet(0)
        alice.engine.closeDays()
        assertEquals("frozen", alice.store.logs(id).single().outcome)
    }

    @Test
    fun `tampering in a strict challenge fails the day and forfeits when no failures are allowed`() {
        val id = twoMembers()
        alice.markMet(0)
        alice.tamper.record(null, Capability.VPN, noon)
        nextDay()
        alice.engine.closeDays()
        assertEquals("failure", alice.store.logs(id).single().outcome)
        assertEquals("forfeited", alice.store.challenge(id)?.state)
        nextDay()
        alice.engine.closeDays()
        assertEquals(1, alice.store.logs(id).size)
    }

    @Test
    fun `screen time criteria read local usage`() {
        alice.habits.add("limit", "Under an hour", HabitCriterion(HabitKind.SCREEN_TIME_UNDER, insta.value, 3_600_000), noon)
        alice.usage.record(insta, noon, Duration.ofMinutes(30))
        val invite = (alice.engine.create("limit", "t", day0.toString(), rules, "https://r.example", "a") as CreateResult.Created).invite
        nextDay()
        alice.engine.closeDays()
        assertEquals("success", alice.store.logs(invite.challengeId).single().outcome)
    }

    @Test
    fun `going over a screen time limit misses the day`() {
        alice.habits.add("limit", "Under an hour", HabitCriterion(HabitKind.SCREEN_TIME_UNDER, insta.value, 3_600_000), noon)
        alice.usage.record(insta, noon, Duration.ofMinutes(90))
        val invite = (alice.engine.create("limit", "t", day0.toString(), rules, "https://r.example", "a") as CreateResult.Created).invite
        nextDay()
        alice.engine.closeDays()
        assertEquals("frozen", alice.store.logs(invite.challengeId).single().outcome)
    }

    @Test
    fun `zero launches means no time in the app`() {
        alice.habits.add("none", "No Instagram", HabitCriterion(HabitKind.ZERO_LAUNCHES, insta.value, null), noon)
        val invite = (alice.engine.create("none", "t", day0.toString(), rules, "https://r.example", "a") as CreateResult.Created).invite
        nextDay()
        alice.engine.closeDays()
        assertEquals("success", alice.store.logs(invite.challengeId).single().outcome)
    }

    @Test
    fun `without usage permission an automatic habit cannot be met`() {
        alice.capabilities.revoke(Capability.USAGE_STATS)
        alice.habits.add("none", "No Instagram", HabitCriterion(HabitKind.ZERO_LAUNCHES, insta.value, null), noon)
        val invite = (alice.engine.create("none", "t", day0.toString(), rules, "https://r.example", "a") as CreateResult.Created).invite
        nextDay()
        alice.engine.closeDays()
        assertEquals("frozen", alice.store.logs(invite.challengeId).single().outcome)
    }

    @Test
    fun `two members see each other after syncing`() {
        val id = twoMembers()
        alice.markMet(0)
        nextDay()
        alice.engine.closeDays()
        bob.engine.closeDays()
        assertTrue(alice.engine.sync(id) is SyncResult.Synced)
        assertTrue(bob.engine.sync(id) is SyncResult.Synced)
        assertTrue(alice.engine.sync(id) is SyncResult.Synced)
        for (device in listOf(alice, bob)) {
            val board = device.engine.leaderboard(id)
            assertEquals(listOf("alice", "bob"), board.map { it.name }, device.name)
            assertEquals(1, board[0].successes)
            assertEquals(listOf(Penalty(day0.toString(), DayOutcome.FROZEN)), board[1].penalties)
            assertTrue(board.all { it.verification == Verification.SIGNED })
        }
    }

    @Test
    fun `the relay sees only same sized ciphertext`() {
        val id = twoMembers()
        alice.markMet(0)
        nextDay()
        alice.engine.closeDays()
        bob.engine.closeDays()
        alice.engine.sync(id)
        bob.engine.sync(id)
        assertEquals(1, relay.stored.size)
        val stored = relay.stored.values.single()
        assertEquals(4, stored.size)
        assertEquals(1, stored.map { it.size }.toSet().size)
        for (bytes in stored) {
            val text = String(bytes, Charsets.ISO_8859_1)
            listOf("alice", "Secret", "Title", "success", "frozen", "commitment").forEach {
                assertFalse(it in text, "$it visible to the relay")
            }
        }
        assertFalse(relay.stored.keys.single() == id)
    }

    @Test
    fun `a finished challenge reveals and confirms for everyone`() {
        val short = rules.copy(lengthDays = 1)
        val id = twoMembers(short)
        alice.markMet(0)
        bob.markMet(0)
        nextDay()
        alice.engine.closeDays()
        bob.engine.closeDays()
        repeat(2) {
            alice.engine.sync(id)
            bob.engine.sync(id)
        }
        for (device in listOf(alice, bob)) {
            val board = device.engine.leaderboard(id)
            assertEquals(2, board.size)
            assertTrue(board.all { it.verification == Verification.CONFIRMED }, "$board")
            assertTrue(board.all { it.standing.status == ChallengeStatus.COMPLETED })
        }
    }

    @Test
    fun `an active member reveals nothing`() {
        val id = twoMembers()
        nextDay()
        alice.engine.closeDays()
        alice.engine.sync(id)
        bob.engine.sync(id)
        assertEquals(null, bob.store.reveal(id, alice.store.challenge(id)?.memberId.orEmpty()))
    }

    @Test
    fun `a relay outage queues results and loses nothing`() {
        val id = twoMembers()
        alice.markMet(0)
        nextDay()
        alice.engine.closeDays()
        relay.offline = true
        assertEquals(SyncResult.RelayUnavailable, alice.engine.sync(id))
        assertEquals(false, alice.store.logs(id).single().synced)
        relay.offline = false
        assertTrue(alice.engine.sync(id) is SyncResult.Synced)
        assertEquals(true, alice.store.logs(id).single().synced)
        bob.engine.sync(id)
        assertEquals(1, bob.engine.leaderboard(id).first { it.name == "alice" }.successes)
    }

    @Test
    fun `sync resumes from its cursor after a restart`() {
        val id = twoMembers()
        alice.engine.sync(id)
        bob.engine.sync(id)
        val cursor = bob.store.challenge(id)?.syncCursor
        assertTrue((cursor ?: 0) > 0)
        val received = (bob.newEngine().sync(id) as SyncResult.Synced).received
        assertEquals(0, received)
    }

    @Test
    fun `garbage forged and foreign messages are rejected without harm`() {
        val id = twoMembers()
        val invite = checkNotNull(Invite.parse(alice.engine.invite(id)!!))
        val keys = GroupKeys(invite.secret)
        relay.append(keys.mailboxId, ByteArray(10))
        relay.append(keys.mailboxId, ByteArray(5000))
        // Encrypted correctly, but with a signature that does not match its content.
        val mallory = Fixtures.member("mallory")
        val forged = Messages.proof(id, mallory.privateKey, day0.toString(), DayOutcome.FAILURE, 0, ByteArray(32), null)
            .copy(outcome = DayOutcome.SUCCESS, streak = 1)
        relay.append(keys.mailboxId, ArenaCrypto.seal(keys.encryptionKey, Wire.encode(forged), keys.associatedData))
        // A different group's message.
        val other = GroupKeys(ByteArray(32) { 1 })
        relay.append(keys.mailboxId, ArenaCrypto.seal(other.encryptionKey, Wire.encode(forged), other.associatedData))
        val result = bob.engine.sync(id) as SyncResult.Synced
        assertEquals(4, result.rejected)
        assertFalse(bob.engine.leaderboard(id).any { it.memberId == mallory.id })
    }

    @Test
    fun `results outside the challenge window are rejected so a peer cannot flood storage`() {
        val id = twoMembers()
        val keys = GroupKeys(checkNotNull(Invite.parse(alice.engine.invite(id)!!)).secret)
        val mallory = Fixtures.member("mallory")
        for (date in listOf("2030-01-01", "2020-01-01")) {
            val proof = Messages.proof(id, mallory.privateKey, date, DayOutcome.SUCCESS, 1, ByteArray(32), null)
            relay.append(keys.mailboxId, ArenaCrypto.seal(keys.encryptionKey, Wire.encode(proof), keys.associatedData))
        }
        assertEquals(2, (bob.engine.sync(id) as SyncResult.Synced).rejected)
        assertEquals(emptyList(), bob.store.entries(id, mallory.id))
    }

    @Test
    fun `a cheating peer is marked invalid and ranked last`() {
        val id = twoMembers()
        val keys = GroupKeys(checkNotNull(Invite.parse(alice.engine.invite(id)!!)).secret)
        val cheat = Fixtures.member("cheat")
        fun send(message: ArenaMessage) =
            relay.append(keys.mailboxId, ArenaCrypto.seal(keys.encryptionKey, Wire.encode(message), keys.associatedData))
        send(Messages.join(id, cheat.privateKey, "cheat"))
        send(Messages.proof(id, cheat.privateKey, day0.toString(), DayOutcome.SUCCESS, 99, ByteArray(32), null))
        nextDay()
        bob.markMet(0)
        bob.engine.closeDays()
        bob.engine.sync(id)
        val board = bob.engine.leaderboard(id)
        assertEquals("cheat", board.last().name)
        assertEquals(Verification.INVALID, board.last().verification)
    }

    @Test
    fun `two signed answers for one day are flagged`() {
        val id = twoMembers()
        val keys = GroupKeys(checkNotNull(Invite.parse(alice.engine.invite(id)!!)).secret)
        val liar = Fixtures.member("liar")
        for (outcome in listOf(DayOutcome.FAILURE, DayOutcome.SUCCESS)) {
            val streak = if (outcome == DayOutcome.SUCCESS) 1 else 0
            val proof = Messages.proof(id, liar.privateKey, day0.toString(), outcome, streak, ArenaCrypto.randomBytes(32), null)
            relay.append(keys.mailboxId, ArenaCrypto.seal(keys.encryptionKey, Wire.encode(proof), keys.associatedData))
        }
        bob.engine.sync(id)
        assertEquals(Verification.INVALID, bob.engine.leaderboard(id).first { it.memberId == liar.id }.verification)
    }

    @Test
    fun `a peer cannot publish under another members identity`() {
        val id = twoMembers()
        alice.engine.sync(id)
        val aliceId = alice.store.challenge(id)?.memberId.orEmpty()
        val keys = GroupKeys(checkNotNull(Invite.parse(alice.engine.invite(id)!!)).secret)
        val mallory = Fixtures.member("mallory")
        val fake = Messages.join(id, mallory.privateKey, "mallory").copy(member = aliceId)
        relay.append(keys.mailboxId, ArenaCrypto.seal(keys.encryptionKey, Wire.encode(fake), keys.associatedData))
        bob.engine.sync(id)
        assertEquals("alice", bob.store.members(id)[aliceId])
    }

    @Test
    fun `joining is refused when it should be`() {
        val invite = create()
        assertEquals(JoinResult.InvalidInvite, bob.engine.join("nonsense", "h", "bob"))
        assertEquals(JoinResult.UnknownHabit, bob.engine.join(invite.encode(), "missing", "bob"))
        assertEquals(JoinResult.Joined(invite.challengeId), bob.engine.join(invite.encode(), "h", "bob"))
        assertEquals(JoinResult.AlreadyJoined, bob.engine.join(invite.encode(), "h", "bob"))
        val late = create()
        nextDay()
        val carol = Device("carol")
        assertEquals(JoinResult.TooLate, carol.engine.join(late.encode(), "h", "carol"))
    }

    @Test
    fun `creating is refused when it should be`() {
        val bad = alice.engine.create("h", "t", day0.toString(), rules.copy(lengthDays = 0), "https://r.example", "a")
        assertEquals(CreateResult.InvalidRules, bad)
        assertEquals(CreateResult.InsecureRelay, alice.engine.create("h", "t", day0.toString(), rules, "http://r.example", "a"))
        assertEquals(CreateResult.UnknownHabit, alice.engine.create("nope", "t", day0.toString(), rules, "https://r.example", "a"))
        assertEquals(CreateResult.InvalidRules, alice.engine.create("h", "t", day0.minusDays(1).toString(), rules, "https://r.example", "a"))
    }

    @Test
    fun `hostile challenge ids cannot reach the secret store as paths`() {
        val invite = create().copy(challengeId = "../../../etc/passwd")
        bob.engine.join(invite.encode(), "h", "bob")
        assertTrue(bob.secrets.names.isNotEmpty())
        assertTrue(bob.secrets.names.all { name -> name.all { it.isLetterOrDigit() || it == '-' } }, "${bob.secrets.names}")
    }

    @Test
    fun `lost secrets are reported and nothing is lost`() {
        val id = twoMembers()
        nextDay()
        alice.secrets.clear()
        assertEquals(SyncResult.SecretsMissing, alice.engine.sync(id))
        alice.engine.closeDays()
        assertEquals(emptyList(), alice.store.logs(id))
        assertNotNull(alice.store.challenge(id))
    }

    @Test
    fun `the signing key and group secret never reach the database`() {
        val id = twoMembers()
        val secret = checkNotNull(alice.secrets.get(alice.secrets.names.first { it.endsWith("group") }))
        val hex = secret.joinToString("") { "%02x".format(it) }
        val rows = alice.db.query("SELECT * FROM challenge WHERE id = ?", listOf(id)) { row ->
            (0..14).joinToString(",") { row.string(it).orEmpty() }
        }.single()
        assertFalse(hex in rows)
    }
}
