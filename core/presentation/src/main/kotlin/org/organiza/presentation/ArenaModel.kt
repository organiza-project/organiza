package org.organiza.presentation

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.organiza.arena.MemberStanding
import org.organiza.arena.Verification

public data class ArenaRow(
    val memberId: String,
    val rank: String,
    val name: String,
    val isSelf: Boolean,
    val progress: String,
    val fraction: Double,
    val streak: Int,
    val status: String,
    val verification: String,
    val invalid: Boolean,
    val penalties: List<String>,
)

/** One challenge as the Arena screen shows it. Results are self-reported (ADR-0006). */
public data class ArenaModel(val dayLine: String, val finding: String, val rows: List<ArenaRow>, val freshness: String) {
    public companion object {
        public fun build(
            today: LocalDate,
            startsOn: String,
            lengthDays: Int,
            selfId: String,
            board: List<MemberStanding>,
            syncedAt: String?,
        ): ArenaModel {
            val start = LocalDate.parse(startsOn)
            val day = ChronoUnit.DAYS.between(start, today) + 1
            val dayLine = if (day < 1) "Starts ${Format.dayMonth(start)}" else "Day ${day.coerceAtMost(lengthDays.toLong())} of $lengthDays"
            var rank = 0
            val rows = board.map { member ->
                val invalid = member.verification == Verification.INVALID
                ArenaRow(
                    memberId = member.memberId,
                    rank = if (invalid) "–" else (++rank).toString(),
                    name = member.name,
                    isSelf = member.memberId == selfId,
                    progress = "${member.successes} of $lengthDays days",
                    fraction = member.successes.toDouble() / lengthDays,
                    streak = member.standing.streak,
                    status = member.standing.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    verification = member.verification.name.lowercase().replaceFirstChar { it.uppercase() },
                    invalid = invalid,
                    penalties = member.penalties.map { "${Format.dayMonth(LocalDate.parse(it.date))} ${it.outcome.wire}" },
                )
            }
            return ArenaModel(dayLine, finding(selfId, board), rows, syncedAt?.let { "Synced $it" } ?: "Not synced yet")
        }

        private fun finding(selfId: String, board: List<MemberStanding>): String {
            val self = board.firstOrNull { it.memberId == selfId } ?: return "You have not reported yet"
            val rival = board.firstOrNull { it.memberId != selfId && it.verification != Verification.INVALID }
                ?: return "No one else has reported yet"
            val gap = rival.successes - self.successes
            val days = Format.count(kotlin.math.abs(gap), "day", "days")
            return when {
                gap > 0 -> "You are $days behind ${rival.name}"
                gap < 0 -> "You lead ${rival.name} by $days"
                else -> "You are level with ${rival.name}"
            }
        }
    }
}
