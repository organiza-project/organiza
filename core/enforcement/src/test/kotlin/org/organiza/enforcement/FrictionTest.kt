package org.organiza.enforcement

import java.time.Duration
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrictionTest {
    @Test
    fun `the pause starts at ten seconds and doubles with each override today`() {
        assertEquals(listOf(10L, 20L, 40L, 80L, 120L, 120L), (0..5).map { Friction.pause(strict = false, overridesToday = it).seconds })
    }

    @Test
    fun `strict sessions start at thirty seconds and share the cap`() {
        assertEquals(listOf(30L, 60L, 120L, 120L), (0..3).map { Friction.pause(strict = true, overridesToday = it).seconds })
    }

    @Test
    fun `a huge override count cannot overflow the pause`() {
        assertEquals(Duration.ofMinutes(2), Friction.pause(strict = false, overridesToday = Int.MAX_VALUE))
    }

    @Test
    fun `strict sessions draw the longer phrases`() {
        val random = Random(7)
        repeat(50) {
            assertTrue(Friction.phrase(strict = true, random).text.length >= Friction.LONG_PHRASE)
            assertTrue(Friction.phrase(strict = false, random).text.length < Friction.LONG_PHRASE)
        }
    }

    @Test
    fun `every phrase is long enough that pasting it would show`() {
        Phrases.all.forEach { assertTrue(it.text.length > TypingGuard.MAX_STEP * 2, it.text) }
    }

    @Test
    fun `phrase ids are unique, so the log can name them`() {
        assertEquals(Phrases.all.size, Phrases.all.map { it.id }.toSet().size)
    }

    @Test
    fun `typing matches ignoring case, spacing and punctuation`() {
        val phrase = "I choose where my attention goes."
        assertTrue(PhraseMatch.matches(phrase, "i choose where my attention goes"))
        assertTrue(PhraseMatch.matches(phrase, "  I  choose where, my attention goes!  "))
        assertTrue(PhraseMatch.matches("It's my choice.", "its my choice"))
    }

    @Test
    fun `a missing or changed word does not match`() {
        val phrase = "I choose where my attention goes."
        assertFalse(PhraseMatch.matches(phrase, "I choose where attention goes"))
        assertFalse(PhraseMatch.matches(phrase, "I choose where my attention goes now"))
        assertFalse(PhraseMatch.matches(phrase, ""))
    }

    @Test
    fun `progress counts the leading words typed correctly`() {
        val phrase = "I choose where my attention goes."
        assertEquals(0, PhraseMatch.wordsCorrect(phrase, ""))
        assertEquals(3, PhraseMatch.wordsCorrect(phrase, "I choose where m"))
        assertEquals(2, PhraseMatch.wordsCorrect(phrase, "I choose wear my"))
        assertEquals(6, PhraseMatch.words(phrase))
    }

    @Test
    fun `typing a word at a time is accepted, swipe typing included`() {
        assertTrue(TypingGuard.accepts("", "I"))
        assertTrue(TypingGuard.accepts("I choose ", "I choose intentionally "))
        assertTrue(TypingGuard.accepts("I choose where", "I choose"))
    }

    @Test
    fun `a pasted phrase is refused`() {
        assertFalse(TypingGuard.accepts("", "I choose where my attention goes."))
    }
}
