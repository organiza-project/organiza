package org.organiza.presentation

import java.time.LocalDate

/** One change to the hero, as stored in `rpg_event` (ADR-0019). */
public data class RpgEntry(val xp: Int, val hp: Int)

/** A daily and the local days it was checked off. */
public data class DailyInput(val id: String, val createdOn: LocalDate, val done: Set<LocalDate>)

public data class HeroModel(val hp: Int, val maxHp: Int, val progression: ProgressionModel) {
    val badge: String get() = "Lvl ${progression.level} · ${Progression.titleFor(progression.level)}"
}

/**
 * Habitica-style rules (ADR-0019). XP from tasks adds to the XP focus and habits already earn.
 * Health drops for a bad habit, a daily left undone at midnight and a focus block broken with an
 * override. At zero the hero faints: full health again, but back to the start of the previous level.
 */
public object Rpg {
    public const val MAX_HP: Int = 50
    public val HABIT_UP: RpgEntry = RpgEntry(xp = 5, hp = 0)
    public val HABIT_DOWN: RpgEntry = RpgEntry(xp = 0, hp = -5)
    public val DAILY: RpgEntry = RpgEntry(xp = 10, hp = 0)
    public val TODO: RpgEntry = RpgEntry(xp = 15, hp = 0)
    public val MISSED_DAILY: RpgEntry = RpgEntry(xp = 0, hp = -8)
    public val BROKE_BLOCK: RpgEntry = RpgEntry(xp = 0, hp = -10)

    public fun hp(entries: List<RpgEntry>): Int = entries.fold(MAX_HP) { hp, entry -> (hp + entry.hp).coerceIn(0, MAX_HP) }

    public fun faint(totalXp: Int): RpgEntry {
        val floor = Progression.startOf((Progression.level(totalXp) - 1).coerceAtLeast(1))
        return RpgEntry(xp = -(totalXp - floor), hp = MAX_HP)
    }

    /** Damage for each day from [from] up to, not including, [until]: undone dailies, then broken blocks. */
    public fun missed(from: LocalDate, until: LocalDate, dailies: List<DailyInput>, brokenBlocks: Map<LocalDate, Int>): List<Pair<LocalDate, RpgEntry>> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it < until }.flatMap { day ->
            val undone = dailies.count { !it.createdOn.isAfter(day) && day !in it.done }
            List(undone) { day to MISSED_DAILY } + List(brokenBlocks[day] ?: 0) { day to BROKE_BLOCK }
        }.toList()

    public fun hero(baseXp: Int, entries: List<RpgEntry>, badges: List<Badge> = emptyList()): HeroModel =
        HeroModel(hp(entries), MAX_HP, Progression.of(baseXp + entries.sumOf { it.xp }, badges))
}
