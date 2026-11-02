package org.organiza.arena

public data class ChallengeRules(
    val lengthDays: Int = 30,
    val freezeAllowance: Int = 0,
    val maxFailures: Int = 0,
    val strict: Boolean = false,
)

/** What happened on a day, before the rules decide what it counts as. */
public enum class DayResult { MET, MISSED, TAMPERED }

public enum class DayOutcome(public val wire: String) {
    SUCCESS("success"),
    FAILURE("failure"),
    FROZEN("frozen"),
}

public enum class ChallengeStatus { ACTIVE, COMPLETED, FORFEITED }

public data class Standing(
    val status: ChallengeStatus,
    val streak: Int,
    val daysClosed: Int,
    val freezesUsed: Int,
    val failures: Int,
) {
    public companion object {
        public val START: Standing = Standing(ChallengeStatus.ACTIVE, 0, 0, 0, 0)
    }
}

/** The challenge rules as pure functions, so every member computes the same answer (ADR-0011). */
public object ChallengeMachine {
    public fun outcomeFor(rules: ChallengeRules, standing: Standing, result: DayResult): DayOutcome = when {
        result == DayResult.MET -> DayOutcome.SUCCESS
        result == DayResult.TAMPERED && rules.strict -> DayOutcome.FAILURE
        standing.freezesUsed < rules.freezeAllowance -> DayOutcome.FROZEN
        else -> DayOutcome.FAILURE
    }

    public fun apply(rules: ChallengeRules, standing: Standing, outcome: DayOutcome): Standing {
        if (standing.status != ChallengeStatus.ACTIVE) return standing
        val next = when (outcome) {
            DayOutcome.SUCCESS -> standing.copy(streak = standing.streak + 1)
            DayOutcome.FROZEN -> standing.copy(freezesUsed = standing.freezesUsed + 1)
            DayOutcome.FAILURE -> standing.copy(streak = 0, failures = standing.failures + 1)
        }.copy(daysClosed = standing.daysClosed + 1)
        return when {
            next.failures > rules.maxFailures -> next.copy(status = ChallengeStatus.FORFEITED)
            next.daysClosed >= rules.lengthDays -> next.copy(status = ChallengeStatus.COMPLETED)
            else -> next
        }
    }

    /** Rebuilds a standing from outcomes, or returns null if the rules forbid that history. */
    public fun replay(rules: ChallengeRules, outcomes: List<DayOutcome>): Standing? =
        outcomes.fold(Standing.START as Standing?) { standing, outcome ->
            standing?.let { step(rules, it, outcome) }
        }

    /** One step of [replay]: null when [outcome] could not legally follow [standing]. */
    internal fun step(rules: ChallengeRules, standing: Standing, outcome: DayOutcome): Standing? {
        val finished = standing.status != ChallengeStatus.ACTIVE
        val noFreezeLeft = outcome == DayOutcome.FROZEN && standing.freezesUsed >= rules.freezeAllowance
        return if (finished || noFreezeLeft) null else apply(rules, standing, outcome)
    }
}
