package org.organiza.arena

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberViewTest {
    private val rules = ChallengeRules(lengthDays = 5, freezeAllowance = 1, maxFailures = 1, strict = false)
    private val start = "2026-11-02"
    private val alice = Fixtures.member("alice")
    private val bob = Fixtures.member("bob")

    private fun date(offset: Int) = java.time.LocalDate.parse(start).plusDays(offset.toLong()).toString()

    private fun chain(member: TestMember, vararg outcomes: Pair<DayOutcome, Int>): List<TestMember.SignedProof> {
        var previous: ByteArray? = null
        return outcomes.mapIndexed { day, (outcome, streak) ->
            member.proof(date(day), outcome, streak, previous).also { previous = it.commitment }
        }
    }

    private fun view(
        member: TestMember,
        proofs: List<ArenaMessage.Proof>,
        reveal: ArenaMessage.Reveal? = null,
        conflict: Boolean = false,
    ) = MemberView.compute(Fixtures.CHALLENGE, rules, start, member.id, member.name, proofs, reveal, conflict)

    @Test
    fun `honest signed results give the right standing`() {
        val proofs = chain(alice, DayOutcome.SUCCESS to 1, DayOutcome.FROZEN to 1, DayOutcome.SUCCESS to 2)
        val result = view(alice, proofs.map { it.message })
        assertEquals(Verification.SIGNED, result.verification)
        assertEquals(Standing(ChallengeStatus.ACTIVE, 2, 3, 1, 0), result.standing)
        assertEquals(listOf(Penalty(date(1), DayOutcome.FROZEN)), result.penalties)
    }

    @Test
    fun `no results yet is a fresh start not a failure`() {
        val result = view(alice, emptyList())
        assertEquals(Verification.SIGNED, result.verification)
        assertEquals(Standing.START, result.standing)
    }

    @Test
    fun `a reveal that matches confirms the results`() {
        val proofs = chain(alice, DayOutcome.SUCCESS to 1, DayOutcome.SUCCESS to 2)
        assertEquals(Verification.CONFIRMED, view(alice, proofs.map { it.message }, alice.reveal(proofs)).verification)
    }

    @Test
    fun `a reveal that does not match marks the member invalid`() {
        val proofs = chain(alice, DayOutcome.SUCCESS to 1)
        val wrongSalt = Messages.reveal(Fixtures.CHALLENGE, alice.privateKey, listOf(date(0) to ByteArray(32)))
        assertEquals(Verification.INVALID, view(alice, proofs.map { it.message }, wrongSalt).verification)
    }

    @Test
    fun `a reveal missing a day marks the member invalid`() {
        val proofs = chain(alice, DayOutcome.SUCCESS to 1, DayOutcome.SUCCESS to 2)
        val partial = alice.reveal(proofs.take(1))
        assertEquals(Verification.INVALID, view(alice, proofs.map { it.message }, partial).verification)
    }

    @Test
    fun `an inflated streak is caught`() {
        val proofs = chain(alice, DayOutcome.SUCCESS to 1, DayOutcome.SUCCESS to 9)
        assertEquals(Verification.INVALID, view(alice, proofs.map { it.message }).verification)
    }

    @Test
    fun `more freezes than allowed are caught`() {
        val proofs = chain(alice, DayOutcome.FROZEN to 0, DayOutcome.FROZEN to 0)
        assertEquals(Verification.INVALID, view(alice, proofs.map { it.message }).verification)
    }

    @Test
    fun `a broken chain is caught`() {
        val first = alice.proof(date(0), DayOutcome.SUCCESS, 1, null)
        val second = alice.proof(date(1), DayOutcome.SUCCESS, 2, ByteArray(32) { 1 })
        assertEquals(Verification.INVALID, view(alice, listOf(first.message, second.message)).verification)
    }

    @Test
    fun `someone elses signature is not accepted`() {
        val forged = bob.proof(date(0), DayOutcome.SUCCESS, 1, null).message.copy(member = alice.id)
        assertEquals(Verification.INVALID, view(alice, listOf(forged)).verification)
    }

    @Test
    fun `a changed outcome breaks the signature`() {
        val honest = alice.proof(date(0), DayOutcome.FAILURE, 0, null).message
        assertEquals(Verification.INVALID, view(alice, listOf(honest.copy(outcome = DayOutcome.SUCCESS, streak = 1))).verification)
    }

    @Test
    fun `two signed answers for a day mark the member invalid`() {
        val proofs = chain(alice, DayOutcome.SUCCESS to 1)
        assertEquals(Verification.INVALID, view(alice, proofs.map { it.message }, conflict = true).verification)
    }

    @Test
    fun `results after a gap wait for the missing day`() {
        val proofs = chain(alice, DayOutcome.SUCCESS to 1, DayOutcome.SUCCESS to 2, DayOutcome.SUCCESS to 3)
        val result = view(alice, listOf(proofs[0].message, proofs[2].message))
        assertEquals(Verification.SIGNED, result.verification)
        assertEquals(1, result.standing.daysClosed)
    }

    @Test
    fun `results from before the start are invalid`() {
        val early = alice.proof("2026-11-01", DayOutcome.SUCCESS, 1, null)
        assertEquals(Verification.INVALID, view(alice, listOf(early.message)).verification)
    }

    @Test
    fun `the leaderboard ranks fairly and predictably`() {
        fun row(name: String, successes: Int, streak: Int, failures: Int, status: ChallengeStatus, verification: Verification) =
            MemberStanding(name, name, Standing(status, streak, successes + failures, 0, failures), successes, verification, emptyList())
        val rows = listOf(
            row("invalid", 30, 30, 0, ChallengeStatus.COMPLETED, Verification.INVALID),
            row("forfeit", 9, 9, 2, ChallengeStatus.FORFEITED, Verification.SIGNED),
            row("second", 5, 2, 0, ChallengeStatus.ACTIVE, Verification.SIGNED),
            row("first", 5, 5, 0, ChallengeStatus.ACTIVE, Verification.SIGNED),
            row("tie-b", 3, 3, 0, ChallengeStatus.ACTIVE, Verification.SIGNED),
            row("tie-a", 3, 3, 0, ChallengeStatus.ACTIVE, Verification.SIGNED),
        )
        assertEquals(
            listOf("first", "second", "tie-a", "tie-b", "forfeit", "invalid"),
            Leaderboard.rank(rows).map { it.memberId },
        )
        assertTrue(Leaderboard.rank(rows).first().verification != Verification.INVALID)
        assertFalse(Leaderboard.rank(emptyList()).isNotEmpty())
    }
}
