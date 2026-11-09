package org.organiza.ai

import java.util.Locale

/** Text that has been through [Sanitizer]. Only this module can create one (ADR-0009). */
public class SanitizedText internal constructor(
    public val value: String,
    public val removedAnything: Boolean,
) {
    override fun toString(): String = value
}

/**
 * Removes identifiers from short user-written text before it can leave the device.
 *
 * This is best-effort, especially for names with no context around them and names in
 * scripts without capital letters, so prompts send as little free text as possible.
 */
public class Sanitizer(privateTerms: Set<String> = emptySet()) {
    private val privateRule: Regex? = privateTerms
        .filter(String::isNotBlank)
        .sortedByDescending(String::length)
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString("|", "(?<![\\p{L}\\p{N}])(?:", ")(?![\\p{L}\\p{N}])") { Regex.escape(it.trim()) }
        ?.let { Regex(it, setOf(RegexOption.IGNORE_CASE)) }

    /**
     * [names], when given, collects each capitalised pair it replaces and numbers its placeholder
     * (`[NAME1]`), so a reply can be mended on the device: a timetable's "Linear Algebra" looks like a name.
     */
    public fun clean(raw: String, names: MutableList<String>? = null): SanitizedText {
        val flat = raw.take(MAX_INPUT_CHARS).replace(CONTROL, " ").replace(SPACES, " ")
        var text = privateRule?.replace(flat, PRIVATE) ?: flat
        text = SCHEME_URL.replace(text, URL)
        text = EMAIL.replace(text, EMAIL_TOKEN)
        text = IPV4.replace(text, IP)
        text = IPV6.replace(text, IP)
        text = WWW.replace(text, URL)
        text = LOWERCASE_DOMAIN.replace(text, URL)
        text = COMMON_TLD_DOMAIN.replace(text, URL)
        text = GROUPED_DIGITS.replace(text) { match ->
            // A timetable row, "08.00 09.30 11.00", is times, not one long number.
            if (TIMES.matches(match.value)) return@replace match.value
            when (match.value.count(Char::isDigit)) {
                in 0 until MIN_PHONE_DIGITS -> match.value
                in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS -> PHONE
                else -> NUMBER
            }
        }
        text = LONG_DIGITS.replace(text, NUMBER)
        text = HANDLE.replace(text, HANDLE_TOKEN)
        text = TITLED_NAME.replace(text, NAME)
        text = CONTEXT_NAME.replace(text, ::replaceName)
        text = RELATION_NAME.replace(text, ::replaceName)
        // Over-scrubs phrases like "New York", which is the safe way to be wrong here.
        text = NAME_PAIR.replace(text) { match ->
            val first = match.groupValues[1].lowercase(Locale.ROOT)
            when {
                first in NOT_NAMES -> match.value
                names == null -> NAME
                else -> "[NAME${names.apply { add(match.value) }.size}]"
            }
        }
        return SanitizedText(text.take(MAX_CHARS), text != flat)
    }

    private fun replaceName(match: MatchResult): String {
        val name = match.groupValues[2]
        val isCalendarWord = name.substringBefore(' ').lowercase(Locale.ROOT) in NOT_NAMES
        return if (isCalendarWord) match.value else match.groupValues[1] + NAME
    }

    public companion object {
        public const val MAX_CHARS: Int = 4000
        private const val MAX_INPUT_CHARS = 64_000
        private const val MIN_PHONE_DIGITS = 9
        private const val MAX_PHONE_DIGITS = 15
        private const val URL = "[URL]"
        private const val EMAIL_TOKEN = "[EMAIL]"
        private const val IP = "[IP]"
        private const val PHONE = "[PHONE]"
        private const val NUMBER = "[NUMBER]"
        private const val HANDLE_TOKEN = "[HANDLE]"
        private const val NAME = "[NAME]"
        private const val PRIVATE = "[PRIVATE]"
        private const val NAME_WORD = """\p{Lu}[\p{L}'’-]+"""

        private val CONTROL = Regex("""\p{Cc}+""")
        private val SPACES = Regex(""" {2,}""")
        private val SCHEME_URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")
        private val EMAIL = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
        private val IPV4 = Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")
        private val IPV6 = Regex("""\b([0-9a-fA-F]{1,4}:){3,7}[0-9a-fA-F]{1,4}\b""")
        private val WWW = Regex("""\bwww\.\S+""", RegexOption.IGNORE_CASE)
        private val LOWERCASE_DOMAIN = Regex("""\b([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,}\b([/?#]\S*)?""")
        private val COMMON_TLD_DOMAIN = Regex(
            """\b[\w-]+(\.[\w-]+)*\.(com|org|net|io|co|app|dev|ai|edu|gov|tv|me|info)\b([/?#]\S*)?""",
            RegexOption.IGNORE_CASE,
        )
        private val TIME = """([01]?\d|2[0-3])\.[0-5]\d"""
        private val TIMES = Regex("""$TIME([ -]$TIME)+""")
        private val GROUPED_DIGITS = Regex("""(?<![\w])\+?\(?\d{1,4}\)?([ .-]\(?\d{1,4}\)?){2,}""")
        private val LONG_DIGITS = Regex("""\d{7,}""")
        private val HANDLE = Regex("""(?<![\w.])@\w{2,}""")
        private val TITLED_NAME = Regex("""\b(Dr|Mr|Mrs|Ms|Mx|Prof|Sir)\.?\s+$NAME_WORD(\s+$NAME_WORD)?""")
        private val CONTEXT_NAME = Regex(
            """\b((?i:with|for|from|by|call|meet|email|text|see|ask|message|visit|phone)\s+)($NAME_WORD(\s+$NAME_WORD)?)""",
        )
        private val RELATION_NAME = Regex(
            """\b((?i:wife|husband|mom|mum|dad|mother|father|sister|brother|son|daughter|friend|boss|partner|""" +
                """girlfriend|boyfriend|colleague|doctor)\s+)($NAME_WORD(\s+$NAME_WORD)?)""",
        )
        private val NAME_PAIR = Regex("""\b($NAME_WORD)\s+$NAME_WORD""")
        private val NOT_NAMES = setOf(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "january", "february", "march", "april", "may", "june", "july", "august",
            "september", "october", "november", "december", "the", "my", "our", "your",
        )
    }
}
