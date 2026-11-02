package org.organiza.arena

import java.io.IOException
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.Capability
import org.organiza.domain.port.Clock
import org.organiza.domain.port.EnforcementCapabilities
import org.organiza.domain.port.UsageHistory
import org.organiza.storage.ArenaStore
import org.organiza.storage.Database
import org.organiza.storage.EntryResult
import org.organiza.storage.HabitCriterion
import org.organiza.storage.HabitKind
import org.organiza.storage.HabitStore
import org.organiza.storage.StoredChallenge
import org.organiza.storage.StoredDayLog
import org.organiza.storage.StoredEntry
import org.organiza.storage.TamperStore

public sealed interface CreateResult {
    public data class Created(val invite: Invite) : CreateResult

    public data object InvalidRules : CreateResult

    public data object InsecureRelay : CreateResult

    public data object UnknownHabit : CreateResult
}

public sealed interface JoinResult {
    public data class Joined(val challengeId: String) : JoinResult

    public data object InvalidInvite : JoinResult

    public data object UnknownHabit : JoinResult

    public data object AlreadyJoined : JoinResult

    /** The first day has already ended, and that day cannot be judged fairly in hindsight. */
    public data object TooLate : JoinResult
}

public sealed interface SyncResult {
    public data class Synced(val published: Int, val received: Int, val rejected: Int) : SyncResult

    public data object RelayUnavailable : SyncResult

    public data object SecretsMissing : SyncResult

    public data object UnknownChallenge : SyncResult
}

/** Runs challenges on this device and exchanges only signed results with the group (ADR-0011). */
public class ArenaEngine(
    private val db: Database,
    private val clock: Clock,
    private val zone: ZoneId,
    private val secrets: SecretStore,
    private val usage: UsageHistory,
    private val capabilities: EnforcementCapabilities,
    private val allowInsecureRelay: Boolean,
    private val relayFor: (String) -> ArenaRelay,
) {
    private val store = ArenaStore(db)
    private val habits = HabitStore(db)
    private val tamper = TamperStore(db)

    private fun today(): LocalDate = clock.now().atZone(zone).toLocalDate()

    public fun create(
        habitId: String,
        title: String,
        startsOn: String,
        rules: ChallengeRules,
        relayUrl: String,
        displayName: String,
    ): CreateResult {
        val start = parseDate(startsOn)
        val rulesOk = rules.lengthDays in 1..MAX_DAYS && rules.freezeAllowance >= 0 && rules.maxFailures >= 0
        if (start == null || start.isBefore(today()) || !rulesOk) return CreateResult.InvalidRules
        val secure = relayUrl.startsWith("https://") || (allowInsecureRelay && relayUrl.startsWith("http://"))
        if (!secure) return CreateResult.InsecureRelay
        if (habits.criterion(habitId) == null) return CreateResult.UnknownHabit
        val invite = Invite(UUID.randomUUID().toString(), title, startsOn, rules, relayUrl, ArenaCrypto.randomBytes(SECRET_BYTES))
        enroll(invite, habitId, displayName)
        return CreateResult.Created(invite)
    }

    public fun join(inviteText: String, habitId: String, displayName: String): JoinResult {
        val invite = Invite.parse(inviteText, allowInsecureRelay) ?: return JoinResult.InvalidInvite
        if (habits.criterion(habitId) == null) return JoinResult.UnknownHabit
        if (store.challenge(invite.challengeId) != null) return JoinResult.AlreadyJoined
        val start = parseDate(invite.startsOn) ?: return JoinResult.InvalidInvite
        if (today().isAfter(start)) return JoinResult.TooLate
        enroll(invite, habitId, displayName)
        return JoinResult.Joined(invite.challengeId)
    }

    private fun enroll(invite: Invite, habitId: String, displayName: String) {
        val signing = ArenaCrypto.newPrivateKey()
        secrets.put(secretName(invite.challengeId, GROUP), invite.secret)
        secrets.put(secretName(invite.challengeId, SIGNING), signing)
        store.createChallenge(
            StoredChallenge(
                id = invite.challengeId,
                habitId = habitId,
                title = invite.title,
                startsOn = invite.startsOn,
                lengthDays = invite.rules.lengthDays,
                freezeAllowance = invite.rules.freezeAllowance,
                maxFailures = invite.rules.maxFailures,
                strict = invite.rules.strict,
                state = STATE_ACTIVE,
                memberId = Messages.memberId(signing),
                displayName = displayName.take(Wire.MAX_NAME_CHARS),
                relayUrl = invite.relayUrl,
                syncCursor = 0,
                joinSent = false,
                revealSent = false,
            ),
        )
    }

    /** The invite to share again. It contains the group secret, so treat it like a password. */
    public fun invite(challengeId: String): String? {
        val challenge = store.challenge(challengeId) ?: return null
        val secret = secrets.get(secretName(challengeId, GROUP)) ?: return null
        return Invite(challenge.id, challenge.title, challenge.startsOn, rules(challenge), challenge.relayUrl, secret).encode()
    }

    public fun challenges(): List<StoredChallenge> = store.allChallenges()

    /** Closes every finished day. Closed days never change, so results cannot be rewritten. */
    public fun closeDays() {
        for (challenge in store.allChallenges()) {
            if (challenge.state != STATE_ACTIVE) continue
            val signing = secrets.get(secretName(challenge.id, SIGNING)) ?: continue
            db.transaction { closeDays(challenge, signing) }
        }
    }

    private fun closeDays(challenge: StoredChallenge, signing: ByteArray) {
        val rules = rules(challenge)
        val start = parseDate(challenge.startsOn) ?: return
        val logs = store.logs(challenge.id)
        var standing = ChallengeMachine.replay(rules, logs.mapNotNull { outcome(it.outcome) }) ?: return
        var previous = logs.lastOrNull()?.commitment
        var date = start.plusDays(logs.size.toLong())
        while (date.isBefore(today()) && standing.status == ChallengeStatus.ACTIVE) {
            val outcome = ChallengeMachine.outcomeFor(rules, standing, evaluate(challenge, rules, date))
            val salt = ArenaCrypto.randomBytes(Commitments.SALT_BYTES)
            val next = ChallengeMachine.apply(rules, standing, outcome)
            val proof = Messages.proof(challenge.id, signing, date.toString(), outcome, next.streak, salt, previous)
            store.addLog(
                challenge.id,
                StoredDayLog(proof.date, outcome.wire, next.streak, salt, proof.commitment, previous, proof.signature, false),
            )
            standing = next
            previous = proof.commitment
            date = date.plusDays(1)
        }
        when (standing.status) {
            ChallengeStatus.ACTIVE -> Unit
            ChallengeStatus.COMPLETED -> store.setState(challenge.id, STATE_COMPLETED)
            ChallengeStatus.FORFEITED -> store.setState(challenge.id, STATE_FORFEITED)
        }
    }

    private fun evaluate(challenge: StoredChallenge, rules: ChallengeRules, date: LocalDate): DayResult {
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        if (rules.strict && tamper.all().any { it.at >= from && it.at < to }) return DayResult.TAMPERED
        val criterion = habits.criterion(challenge.habitId) ?: return DayResult.MISSED
        val met = when (criterion.kind) {
            HabitKind.MANUAL -> habits.results(challenge.habitId, date, date.plusDays(1))[date] == true
            HabitKind.SCREEN_TIME_UNDER -> {
                val limit = criterion.limitMillis
                val used = usedTime(criterion, from, to)
                limit != null && used != null && used < Duration.ofMillis(limit)
            }
            HabitKind.ZERO_LAUNCHES -> usedTime(criterion, from, to) == Duration.ZERO
        }
        return if (met) DayResult.MET else DayResult.MISSED
    }

    /** Null when usage cannot be read: without the permission every reading would look like zero. */
    private fun usedTime(criterion: HabitCriterion, from: java.time.Instant, to: java.time.Instant): Duration? {
        if (Capability.USAGE_STATS !in capabilities.granted()) return null
        val app = (PackageName.parse(criterion.target.orEmpty()) as? Parsed.Valid)?.value ?: return null
        return usage.foregroundTime(app, from, to)
    }

    /** Publishes what is queued and reads what is new. A failure leaves everything queued. */
    public fun sync(challengeId: String): SyncResult {
        val challenge = store.challenge(challengeId) ?: return SyncResult.UnknownChallenge
        val secret = secrets.get(secretName(challengeId, GROUP)) ?: return SyncResult.SecretsMissing
        val signing = secrets.get(secretName(challengeId, SIGNING)) ?: return SyncResult.SecretsMissing
        val keys = GroupKeys(secret)
        val relay = relayFor(challenge.relayUrl)
        return try {
            val published = publish(challenge, signing, keys, relay)
            val (received, rejected) = receive(challenge, keys, relay)
            SyncResult.Synced(published, received, rejected)
        } catch (_: IOException) {
            SyncResult.RelayUnavailable
        }
    }

    private fun publish(challenge: StoredChallenge, signing: ByteArray, keys: GroupKeys, relay: ArenaRelay): Int {
        var published = 0
        fun send(message: ArenaMessage) {
            relay.append(keys.mailboxId, ArenaCrypto.seal(keys.encryptionKey, Wire.encode(message), keys.associatedData))
            published++
        }
        if (!challenge.joinSent) {
            send(Messages.join(challenge.id, signing, challenge.displayName))
            store.markJoinSent(challenge.id)
        }
        val logs = store.logs(challenge.id)
        for (log in logs.filterNot { it.synced }) {
            send(proofFrom(challenge.memberId, log) ?: continue)
            store.markSynced(challenge.id, log.date, clock.now().toEpochMilli())
        }
        if (challenge.state != STATE_ACTIVE && !challenge.revealSent) {
            send(Messages.reveal(challenge.id, signing, logs.map { it.date to it.salt }))
            store.markRevealSent(challenge.id)
        }
        return published
    }

    private fun receive(challenge: StoredChallenge, keys: GroupKeys, relay: ArenaRelay): Pair<Int, Int> {
        var cursor = challenge.syncCursor
        var received = 0
        var rejected = 0
        repeat(MAX_PAGES) {
            val batch = relay.fetch(keys.mailboxId, cursor)
            if (batch.isEmpty()) return received to rejected
            for (item in batch) {
                val plain = ArenaCrypto.open(keys.encryptionKey, item.data, keys.associatedData)
                val message = plain?.let(Wire::decode)
                when {
                    message == null || !Messages.verify(challenge.id, message) -> rejected++
                    message.member == challenge.memberId -> Unit
                    accept(challenge, message) -> received++
                    else -> rejected++
                }
                cursor = maxOf(cursor, item.cursor)
            }
            store.setCursor(challenge.id, cursor)
        }
        return received to rejected
    }

    /** Stores a message whose signature has already been checked. */
    private fun accept(challenge: StoredChallenge, message: ArenaMessage): Boolean {
        val known = store.members(challenge.id)
        if (message.member !in known && known.size >= MAX_MEMBERS) return false
        when (message) {
            is ArenaMessage.Join -> store.putMember(challenge.id, message.member, cleanName(message.name), replaceName = true)
            is ArenaMessage.Proof -> {
                if (!inWindow(challenge, message.date)) return false
                store.putMember(challenge.id, message.member, "Member " + message.member.take(6), replaceName = false)
                val entry = StoredEntry(
                    message.member,
                    message.date,
                    message.outcome.wire,
                    message.streak,
                    message.commitment,
                    message.previous,
                    message.signature,
                )
                if (store.putEntry(challenge.id, entry) == EntryResult.DUPLICATE) return true
            }
            is ArenaMessage.Reveal -> store.putReveal(challenge.id, message.member, Wire.encode(message))
        }
        return true
    }

    /** Bounds storage: a peer can only ever fill the challenge's own days. */
    private fun inWindow(challenge: StoredChallenge, date: String): Boolean {
        val day = parseDate(date) ?: return false
        val start = parseDate(challenge.startsOn) ?: return false
        val inChallenge = !day.isBefore(start) && day.isBefore(start.plusDays(challenge.lengthDays.toLong()))
        // A day later than tomorrow has not ended anywhere on Earth, so it cannot be a real result.
        return inChallenge && !day.isAfter(today().plusDays(1))
    }

    private fun cleanName(name: String) = name.filterNot(Char::isISOControl).ifBlank { "Member" }

    /** Everyone's standing, recomputed from signed results on every call. */
    public fun leaderboard(challengeId: String): List<MemberStanding> {
        val challenge = store.challenge(challengeId) ?: return emptyList()
        val rules = rules(challenge)
        val own = store.logs(challengeId)
        val signing = secrets.get(secretName(challengeId, SIGNING))
        val ownReveal = signing?.takeIf { challenge.revealSent }
            ?.let { key -> Messages.reveal(challengeId, key, own.map { it.date to it.salt }) }
        val self = MemberView.compute(
            challengeId,
            rules,
            challenge.startsOn,
            challenge.memberId,
            challenge.displayName,
            own.mapNotNull { proofFrom(challenge.memberId, it) },
            ownReveal,
            conflict = false,
        )
        val others = store.members(challengeId).map { (memberId, name) ->
            MemberView.compute(
                challengeId,
                rules,
                challenge.startsOn,
                memberId,
                name,
                store.entries(challengeId, memberId).mapNotNull(::proofFrom),
                store.reveal(challengeId, memberId)?.let(Wire::decode) as? ArenaMessage.Reveal,
                store.hasConflict(challengeId, memberId),
            )
        }
        return Leaderboard.rank(others + self)
    }

    private fun proofFrom(memberId: String, log: StoredDayLog): ArenaMessage.Proof? {
        val outcome = outcome(log.outcome) ?: return null
        return ArenaMessage.Proof(memberId, log.date, outcome, log.streak, log.commitment, log.previous, log.signature)
    }

    private fun proofFrom(entry: StoredEntry): ArenaMessage.Proof? {
        val outcome = outcome(entry.outcome) ?: return null
        return ArenaMessage.Proof(entry.memberId, entry.date, outcome, entry.streak, entry.commitment, entry.previous, entry.signature)
    }

    private fun outcome(wire: String) = DayOutcome.entries.firstOrNull { it.wire == wire }

    private fun rules(challenge: StoredChallenge) =
        ChallengeRules(challenge.lengthDays, challenge.freezeAllowance, challenge.maxFailures, challenge.strict)

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeException) {
        null
    }

    /** Challenge IDs come from invites, so they are hashed before they can name anything on disk. */
    private fun secretName(challengeId: String, kind: String): String =
        MessageDigest.getInstance("SHA-256").digest(challengeId.toByteArray()).toHex() + "-" + kind

    private companion object {
        const val STATE_ACTIVE = "active"
        const val STATE_COMPLETED = "completed"
        const val STATE_FORFEITED = "forfeited"
        const val GROUP = "group"
        const val SIGNING = "signing"
        const val SECRET_BYTES = 32
        const val MAX_DAYS = 365
        const val MAX_MEMBERS = 100
        const val MAX_PAGES = 20
    }
}
