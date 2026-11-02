package org.organiza.arena

import java.time.DateTimeException
import java.time.LocalDate

public enum class Verification {
    /** Signed and consistent so far. Salts have not been revealed yet. */
    SIGNED,

    /** The member's reveal matched every commitment, so nothing was rewritten. */
    CONFIRMED,

    /** Something does not add up, so this member's results should not be believed. */
    INVALID,
}

public data class Penalty(val date: String, val outcome: DayOutcome)

public data class MemberStanding(
    val memberId: String,
    val name: String,
    val standing: Standing,
    val successes: Int,
    val verification: Verification,
    val penalties: List<Penalty>,
)

/** Works out a member's standing from their signed messages, trusting nothing else (ADR-0011). */
public object MemberView {
    public fun compute(
        challengeId: String,
        rules: ChallengeRules,
        startsOn: String,
        memberId: String,
        name: String,
        proofs: List<ArenaMessage.Proof>,
        reveal: ArenaMessage.Reveal?,
        conflict: Boolean,
    ): MemberStanding {
        val start = parseDate(startsOn)
        var standing = Standing.START
        var previous: ByteArray? = null
        var successes = 0
        var invalid = conflict || start == null
        val penalties = mutableListOf<Penalty>()
        val accepted = mutableListOf<ArenaMessage.Proof>()
        val ordered = proofs.sortedBy { it.date }

        if (ordered.any { it.member != memberId || !Messages.verify(challengeId, it) }) invalid = true
        if (start != null && ordered.any { parseDate(it.date)?.isBefore(start) != false }) invalid = true

        if (!invalid && start != null) {
            for (proof in ordered) {
                // A later day arriving before an earlier one waits until the gap is filled.
                if (parseDate(proof.date) != start.plusDays(accepted.size.toLong())) break
                val next = ChallengeMachine.step(rules, standing, proof.outcome)
                val chained = proof.previous.contentEquals(previous)
                if (next == null || !chained || next.streak != proof.streak) {
                    invalid = true
                    break
                }
                standing = next
                previous = proof.commitment
                accepted += proof
                if (proof.outcome == DayOutcome.SUCCESS) successes++ else penalties += Penalty(proof.date, proof.outcome)
            }
        }

        val verification = when {
            invalid -> Verification.INVALID
            reveal == null -> Verification.SIGNED
            revealMatches(challengeId, memberId, accepted, reveal) -> Verification.CONFIRMED
            else -> Verification.INVALID
        }
        return MemberStanding(memberId, name, standing, successes, verification, penalties)
    }

    private fun revealMatches(
        challengeId: String,
        memberId: String,
        proofs: List<ArenaMessage.Proof>,
        reveal: ArenaMessage.Reveal,
    ): Boolean {
        if (reveal.member != memberId || !Messages.verify(challengeId, reveal)) return false
        return proofs.all { proof ->
            val salt = reveal.salts[proof.date]?.hexToBytesOrNull() ?: return false
            val expected = Commitments.compute(
                challengeId,
                memberId,
                proof.date,
                proof.outcome,
                proof.streak,
                salt,
                proof.previous,
            )
            expected.contentEquals(proof.commitment)
        }
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeException) {
        null
    }
}

public object Leaderboard {
    /** Invalid members rank last, then forfeited ones. Ties break the same way on every device. */
    public fun rank(members: List<MemberStanding>): List<MemberStanding> = members.sortedWith(
        compareBy<MemberStanding> { it.verification == Verification.INVALID }
            .thenBy { it.standing.status == ChallengeStatus.FORFEITED }
            .thenByDescending { it.successes }
            .thenByDescending { it.standing.streak }
            .thenBy { it.standing.failures }
            .thenBy { it.memberId },
    )
}
