package org.organiza.presentation

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RpgTest {
    private val monday = LocalDate.parse("2026-11-02")

    @Test
    fun `health starts full, drops with damage and never leaves its range`() {
        assertEquals(Rpg.MAX_HP, Rpg.hp(emptyList()))
        assertEquals(Rpg.MAX_HP - 5, Rpg.hp(listOf(Rpg.HABIT_DOWN)))
        assertEquals(Rpg.MAX_HP, Rpg.hp(listOf(Rpg.HABIT_DOWN, RpgEntry(0, 40))))
        assertEquals(0, Rpg.hp(List(20) { Rpg.MISSED_DAILY }))
    }

    @Test
    fun `fainting refills health and drops to the start of the previous level`() {
        // 350 XP is level 3 (starts at 300), so the hero falls to level 2, which starts at 100.
        assertEquals(RpgEntry(xp = -250, hp = Rpg.MAX_HP), Rpg.faint(totalXp = 350))
        assertEquals(RpgEntry(xp = -40, hp = Rpg.MAX_HP), Rpg.faint(totalXp = 40))
        assertEquals(Rpg.MAX_HP, Rpg.hp(List(10) { Rpg.MISSED_DAILY } + Rpg.faint(350)))
    }

    @Test
    fun `each daily left undone on a past day costs health, from the day it was made`() {
        val dailies = listOf(
            DailyInput("water", createdOn = monday, done = setOf(monday)),
            DailyInput("read", createdOn = monday.plusDays(1), done = emptySet()),
        )
        val damage = Rpg.missed(from = monday, until = monday.plusDays(3), dailies, brokenBlocks = emptyMap())
        assertEquals(
            listOf(
                monday.plusDays(1) to Rpg.MISSED_DAILY, // water
                monday.plusDays(1) to Rpg.MISSED_DAILY, // read
                monday.plusDays(2) to Rpg.MISSED_DAILY,
                monday.plusDays(2) to Rpg.MISSED_DAILY,
            ),
            damage,
        )
    }

    @Test
    fun `a focus block broken by an override costs health on its day`() {
        val damage = Rpg.missed(monday, monday.plusDays(1), emptyList(), mapOf(monday to 2, monday.plusDays(5) to 1))
        assertEquals(listOf(monday to Rpg.BROKE_BLOCK, monday to Rpg.BROKE_BLOCK), damage)
    }

    @Test
    fun `the hero adds earned xp to the xp from focus and habits`() {
        val hero = Rpg.hero(baseXp = 90, entries = listOf(Rpg.DAILY, Rpg.HABIT_DOWN))
        assertEquals(100, hero.progression.xp)
        assertEquals(2, hero.progression.level)
        assertEquals(Rpg.MAX_HP - 5, hero.hp)
        assertEquals("Lvl 2 · Focus Novice", hero.badge)
    }
}
