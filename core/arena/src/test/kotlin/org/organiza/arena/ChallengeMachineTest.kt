package org.organiza.arena

import kotlin.test.Test
import kotlin.test.assertEquals

class ChallengeMachineTest {
    private val rules = ChallengeRules(lengthDays = 5, freezeAllowance = 1, maxFailures = 1, strict = true)

    private fun run(rules: ChallengeRules, vararg results: DayResult): Standing =
        results.fold(Standing.START) { standing, result ->
            ChallengeMachine.apply(rules, standing, ChallengeMachine.outcomeFor(rules, standing, result))
        }

    @Test
    fun `a met day grows the streak`() {
        assertEquals(Standing(ChallengeStatus.ACTIVE, 2, 2, 0, 0), run(rules, DayResult.MET, DayResult.MET))
    }

    @Test
    fun `a missed day uses a freeze and keeps the streak`() {
        val standing = run(rules, DayResult.MET, DayResult.MET, DayResult.MISSED)
        assertEquals(Standing(ChallengeStatus.ACTIVE, 2, 3, 1, 0), standing)
    }

    @Test
    fun `with no freezes left a missed day fails and resets the streak`() {
        val standing = run(rules, DayResult.MET, DayResult.MISSED, DayResult.MISSED)
        assertEquals(Standing(ChallengeStatus.ACTIVE, 0, 3, 1, 1), standing)
    }

    @Test
    fun `tampering in a strict challenge fails and cannot be frozen`() {
        val standing = run(rules, DayResult.MET, DayResult.TAMPERED)
        assertEquals(Standing(ChallengeStatus.ACTIVE, 0, 2, 0, 1), standing)
    }

    @Test
    fun `tampering outside a strict challenge counts as an ordinary miss`() {
        val relaxed = rules.copy(strict = false)
        assertEquals(DayOutcome.FROZEN, ChallengeMachine.outcomeFor(relaxed, Standing.START, DayResult.TAMPERED))
    }

    @Test
    fun `too many failures forfeit the challenge`() {
        val standing = run(rules, DayResult.TAMPERED, DayResult.TAMPERED)
        assertEquals(ChallengeStatus.FORFEITED, standing.status)
    }

    @Test
    fun `no failures allowed means the first failure forfeits`() {
        val hard = rules.copy(freezeAllowance = 0, maxFailures = 0)
        assertEquals(ChallengeStatus.FORFEITED, run(hard, DayResult.MISSED).status)
    }

    @Test
    fun `surviving every day completes the challenge`() {
        val standing = run(rules, DayResult.MET, DayResult.MISSED, DayResult.MET, DayResult.MISSED, DayResult.MET)
        assertEquals(Standing(ChallengeStatus.COMPLETED, 1, 5, 1, 1), standing)
    }

    @Test
    fun `a finished challenge ignores further days`() {
        val hard = rules.copy(freezeAllowance = 0, maxFailures = 0)
        val forfeited = run(hard, DayResult.MISSED)
        assertEquals(forfeited, ChallengeMachine.apply(hard, forfeited, DayOutcome.SUCCESS))
    }

    @Test
    fun `replaying outcomes rebuilds the standing`() {
        val outcomes = listOf(DayOutcome.SUCCESS, DayOutcome.FROZEN, DayOutcome.SUCCESS)
        assertEquals(Standing(ChallengeStatus.ACTIVE, 2, 3, 1, 0), ChallengeMachine.replay(rules, outcomes))
    }

    @Test
    fun `an impossible history has no standing`() {
        assertEquals(null, ChallengeMachine.replay(rules, listOf(DayOutcome.FROZEN, DayOutcome.FROZEN)))
        val hard = rules.copy(maxFailures = 0)
        assertEquals(null, ChallengeMachine.replay(hard, listOf(DayOutcome.FAILURE, DayOutcome.SUCCESS)))
    }
}
