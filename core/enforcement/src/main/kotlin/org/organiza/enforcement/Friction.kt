package org.organiza.enforcement

import java.time.Duration
import kotlin.random.Random

/** A reflective phrase. [id] names it in the local log without repeating the text. */
public data class Phrase(val id: String, val text: String)

/** How much effort an override costs (ADR-0016). */
public object Friction {
    /** Strict sessions draw phrases at least this long. */
    public const val LONG_PHRASE: Int = 70
    private const val BASE_SECONDS = 10L
    private const val STRICT_BASE_SECONDS = 30L
    private val CAP: Duration = Duration.ofMinutes(2)

    /** The unskippable pause: it doubles with each override already made today, up to two minutes. */
    public fun pause(strict: Boolean, overridesToday: Int): Duration {
        val base = if (strict) STRICT_BASE_SECONDS else BASE_SECONDS
        // Past 2^4 every base is over the cap, and stopping there keeps the shift from overflowing.
        val doubled = base shl overridesToday.coerceIn(0, 4)
        return minOf(Duration.ofSeconds(doubled), CAP)
    }

    public fun phrase(strict: Boolean, random: Random = Random.Default): Phrase {
        val pool = Phrases.all.filter { (it.text.length >= LONG_PHRASE) == strict }
        return pool[random.nextInt(pool.size)]
    }
}

/** Original phrases written for Organiza, so they carry no attribution or licence. */
public object Phrases {
    public val all: List<Phrase> = listOf(
        Phrase("s01", "I am choosing this, not drifting into it."),
        Phrase("s02", "This minute is mine, and I spend it on purpose."),
        Phrase("s03", "The urge will pass whether I open this or not."),
        Phrase("s04", "I can wait ten more minutes and decide again."),
        Phrase("s05", "Attention is the one thing I cannot get back."),
        Phrase("s06", "What I do right now is what my whole day becomes."),
        Phrase("s07", "I opened this on autopilot, and I am awake now."),
        Phrase("s08", "Boredom is not an emergency, and I can sit with it."),
        Phrase("s09", "I decide what deserves my attention today."),
        Phrase("s10", "A small pause now saves a long scroll later."),
        Phrase("s11", "I am the one who set this block, for a reason."),
        Phrase("s12", "Nothing in this feed is more urgent than my plan."),
        Phrase("l01", "I set this block when my mind was clear, and I trust that version of myself more than this urge."),
        Phrase("l02", "The things that matter most rarely shout, while the things that matter least never stop asking."),
        Phrase("l03", "If I still need this after a calm breath, I will open it on purpose and close it when I am done."),
        Phrase("l04", "My time is the material my life is made of, and I will not hand it to a feed without noticing."),
        Phrase("l05", "I cannot control every impulse that arrives, but I can choose which of them I act on right now."),
        Phrase("l06", "This is a moment to practise the discipline I wanted when I chose to protect this time."),
        Phrase("l07", "An hour lost to scrolling is not taken from me; I give it away, one small choice at a time."),
        Phrase("l08", "Before I open this, I will name what I am avoiding, and ask whether this will really help."),
    )
}

/** Compares typed text with a phrase, ignoring case, spacing and punctuation. */
public object PhraseMatch {
    public fun matches(phrase: String, typed: String): Boolean {
        val expected = normalise(phrase)
        return expected.isNotEmpty() && expected == normalise(typed)
    }

    public fun words(phrase: String): Int = normalise(phrase).size

    /** How many words from the start are already right, for a progress line. */
    public fun wordsCorrect(phrase: String, typed: String): Int {
        val expected = normalise(phrase)
        val actual = normalise(typed)
        return expected.zip(actual).takeWhile { (want, got) -> want == got }.size
    }

    private fun normalise(text: String): List<String> = text
        .lowercase()
        .replace("'", "")
        .replace("’", "")
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter(String::isNotEmpty)
}

/**
 * Refuses pasted text. One change may add a word, which keeps swipe typing and suggestions
 * working, but not a whole phrase.
 */
public object TypingGuard {
    public const val MAX_STEP: Int = 20

    public fun accepts(previous: String, next: String): Boolean = next.length - previous.length <= MAX_STEP
}
