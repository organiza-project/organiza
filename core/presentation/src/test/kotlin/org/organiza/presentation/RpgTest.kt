package org.organiza.presentation

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RpgTest {
    private val monday = LocalDate.parse("2026-11-02")

    @Test
    fun `health starts full, drops with damage, heals with good days, and never leaves its range`() {
        assertEquals(50, Rpg.hp(emptyList(), 50))
        assertEquals(45, Rpg.hp(listOf(Rpg.HABIT_DOWN), 50))
        // A daily done heals 2, a good habit 1, a to-do 3.
        assertEquals(48, Rpg.hp(listOf(Rpg.MISSED_DAILY, Rpg.DAILY, Rpg.HABIT_UP, Rpg.TODO), 50))
        assertEquals(50, Rpg.hp(listOf(Rpg.HABIT_DOWN, RpgEntry(0, 40)), 50))
        assertEquals(0, Rpg.hp(List(20) { Rpg.MISSED_DAILY }, 50))
    }

    @Test
    fun `health grows from 50 at level 1 to 100 at level 100`() {
        assertEquals(50, Rpg.maxHp(1))
        assertEquals(75, Rpg.maxHp(50))
        assertEquals(100, Rpg.maxHp(100))
    }

    @Test
    fun `fainting refills health and drops to the start of the previous level`() {
        // 350 XP is level 4 (starts at 240), so the hero falls to level 3, which starts at 140.
        assertEquals(RpgEntry(xp = -210, hp = Rpg.FULL), Rpg.faint(totalXp = 350))
        assertEquals(RpgEntry(xp = -40, hp = Rpg.FULL), Rpg.faint(totalXp = 40))
        assertEquals(51, Rpg.hp(List(10) { Rpg.MISSED_DAILY } + Rpg.faint(350), 51))
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
        assertEquals(51, hero.maxHp)
        assertEquals(46, hero.hp)
        assertEquals("Lvl 2 · Novice", hero.badge)
    }
}
