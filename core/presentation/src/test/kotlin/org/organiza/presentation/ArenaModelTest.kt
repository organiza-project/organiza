package org.organiza.presentation

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.arena.ChallengeStatus
import org.organiza.arena.DayOutcome
import org.organiza.arena.MemberStanding
import org.organiza.arena.Penalty
import org.organiza.arena.Standing
import org.organiza.arena.Verification

class ArenaModelTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun member(id: String, name: String, successes: Int, streak: Int, verification: Verification = Verification.SIGNED) =
        MemberStanding(id, name, Standing(ChallengeStatus.ACTIVE, streak, successes, 0, 0), successes, verification, emptyList())

    private val board = listOf(
        member("b", "Sam", 9, 9),
        member("me", "You", 8, 3),
        member("c", "Cheater", 0, 0, Verification.INVALID),
    )

    @Test
    fun `the header states the day of the challenge`() {
        val model = ArenaModel.build(today, "2026-11-02", 30, "me", board, syncedAt = null)
        assertEquals("Day 11 of 30", model.dayLine)
        assertEquals("Day 1 of 30", ArenaModel.build(LocalDate.of(2026, 11, 2), "2026-11-02", 30, "me", board, null).dayLine)
    }

    @Test
    fun `rows rank only valid members and mark the device owner`() {
        val rows = ArenaModel.build(today, "2026-11-02", 30, "me", board, null).rows
        assertEquals(listOf("1", "2", "–"), rows.map { it.rank })
        assertEquals(listOf(false, true, false), rows.map { it.isSelf })
        assertEquals(listOf("9 of 30 days", "8 of 30 days", "0 of 30 days"), rows.map { it.progress })
        assertEquals(listOf(0.3, 0.2667, 0.0), rows.map { "%.4f".format(it.fraction).toDouble() })
        assertEquals(listOf("Signed", "Signed", "Invalid"), rows.map { it.verification })
    }

    @Test
    fun `the finding compares the device owner with the leader`() {
        assertEquals("You are 1 day behind Sam", ArenaModel.build(today, "2026-11-02", 30, "me", board, null).finding)
        val leading = listOf(member("me", "You", 9, 9), member("b", "Sam", 9, 2))
        assertEquals("You are level with Sam", ArenaModel.build(today, "2026-11-02", 30, "me", leading, null).finding)
        val alone = listOf(member("me", "You", 2, 2))
        assertEquals("No one else has reported yet", ArenaModel.build(today, "2026-11-02", 30, "me", alone, null).finding)
    }

    @Test
    fun `penalties read as dated words`() {
        val withPenalty = board.map {
            if (it.memberId == "me") it.copy(penalties = listOf(Penalty("2026-11-05", DayOutcome.FROZEN))) else it
        }
        val rows = ArenaModel.build(today, "2026-11-02", 30, "me", withPenalty, null).rows
        assertEquals(listOf("5 Nov frozen"), rows.first { it.isSelf }.penalties)
    }

    @Test
    fun `freshness says when it last synced`() {
        val model = ArenaModel.build(today, "2026-11-02", 30, "me", board, syncedAt = "10:42")
        assertEquals("Synced 10:42", model.freshness)
        assertEquals("Not synced yet", ArenaModel.build(today, "2026-11-02", 30, "me", board, null).freshness)
    }

    @Test
    fun `rows carry challenge xp and the podium takes the valid top three`() {
        val model = ArenaModel.build(today, "2026-11-02", 30, "me", board, null)
        model.rows.forEach { assertEquals(it.successes * 10 + it.streak * 2, it.xp) }
        assertEquals(model.rows.filterNot { it.invalid }.take(3).map { it.memberId }, model.podium.map { it.memberId })
    }
}
