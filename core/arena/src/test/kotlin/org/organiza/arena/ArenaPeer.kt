package org.organiza.arena

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.organiza.ai.UrlConnectionTransport
import org.organiza.domain.port.Capability
import org.organiza.domain.port.Clock
import org.organiza.domain.port.EnforcementCapabilities
import org.organiza.domain.port.UsageHistory
import org.organiza.storage.HabitStore
import org.organiza.storage.Migrations
import org.organiza.testing.FakeSecretStore
import org.organiza.testing.JdbcDatabase

/** A separate participant process. Modes: honest <relay> <invite>, or cheat <relay> <invite>. */
fun main(args: Array<String>) {
    val (mode, relayUrl, inviteText) = args
    val invite = checkNotNull(Invite.parse(inviteText, allowInsecure = true)) { "bad invite" }
    val relay = HttpArenaRelay(UrlConnectionTransport(), relayUrl)
    if (mode == "cheat") {
        val keys = GroupKeys(invite.secret)
        val key = ArenaCrypto.newPrivateKey()
        fun send(message: ArenaMessage) =
            relay.append(keys.mailboxId, ArenaCrypto.seal(keys.encryptionKey, Wire.encode(message), keys.associatedData))
        send(Messages.join(invite.challengeId, key, "Cheater"))
        send(Messages.proof(invite.challengeId, key, invite.startsOn, DayOutcome.SUCCESS, 99, ByteArray(32), null))
        println("peer: sent inflated streak")
        return
    }
    var now = Instant.now()
    // The app counts days in the device zone, which the emulator takes from this host.
    val zone = ZoneId.systemDefault()
    val db = JdbcDatabase().also { Migrations.migrate(it) }
    val habits = HabitStore(db).apply { add("h", "Peer private habit", now) }
    val engine = ArenaEngine(
        db,
        Clock { now },
        zone,
        FakeSecretStore(),
        UsageHistory { _, _, _ -> Duration.ZERO },
        EnforcementCapabilities { setOf(Capability.USAGE_STATS) },
        allowInsecureRelay = true,
    ) { relay }
    println("peer: " + engine.join(inviteText, "h", "Peer"))
    habits.mark("h", now.atZone(zone).toLocalDate(), true)
    now = now.plus(Duration.ofDays(1))
    engine.closeDays()
    println("peer: " + engine.sync(invite.challengeId))
    println("peer: " + engine.sync(invite.challengeId))
    engine.leaderboard(invite.challengeId).forEach {
        println("peer-board: ${it.name} status=${it.standing.status} successes=${it.successes} verification=${it.verification}")
    }
}
