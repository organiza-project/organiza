package org.organiza.voice

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

public sealed interface VoiceCommand {
    public data class AddHabit(val name: String) : VoiceCommand

    public data class RemoveHabit(val name: String) : VoiceCommand

    public data class MarkHabit(val name: String, val date: LocalDate, val done: Boolean) : VoiceCommand

    public data class AddSession(val tag: String, val start: Instant, val minutes: Int) : VoiceCommand

    public data class CancelSession(val tag: String, val start: Instant?) : VoiceCommand

    /** The text shown for confirmation before anything is applied (ADR-0010). */
    public fun describe(zone: ZoneId = ZoneId.systemDefault()): String = when (this) {
        is AddHabit -> "Add habit: $name"
        is RemoveHabit -> "Remove habit: $name"
        is MarkHabit -> "Mark $name ${if (done) "done" else "missed"} on $date"
        is AddSession -> "Schedule $tag at ${start.atZone(zone).toLocalDateTime()} for $minutes minutes"
        is CancelSession -> "Cancel " + (start?.let { "$tag at ${it.atZone(zone).toLocalDateTime()}" } ?: "the next $tag session")
    }
}

public object SpokenNumbers {
    private val WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "forty-five" to 45, "sixty" to 60, "ninety" to 90,
    )

    /** Whisper usually writes digits, but not always, so both spellings must compare equal. */
    public fun toDigits(text: String): String =
        text.split(' ').joinToString(" ") { word -> WORDS[word]?.toString() ?: word }
}

/** The whole command grammar. Nothing else can become an action (ADR-0010). */
public object CommandParser {
    private const val NAME_PREFIX = """(?: the| my)?"""
    private val ADD_HABIT = Regex("""(?:add|create|start|new)(?: a new| a| new)? habit(?: called| named| to)? (.+)""")
    private val REMOVE_HABIT = Regex("""(?:remove|delete|drop|stop)$NAME_PREFIX habit(?: called| named)? (.+)""")
    private val MARK = Regex(
        """(?:mark|check off|tick off)$NAME_PREFIX(?: habit)? (.+?)(?: as)? """ +
            """(not done|done|complete|completed|finished|missed|failed)(?: (today|yesterday))?""",
    )
    private val DID = Regex("""i (?:finished|completed|did)$NAME_PREFIX (.+?)(?: (today|yesterday))?""")
    private val SCHEDULE = Regex("""(?:schedule|plan|book) (.+)""")
    private val CANCEL = Regex("""cancel (.+)""")
    private val DURATION = Regex("""\bfor (half an hour|an hour|(\d{1,4}) (hours?|minutes?|mins?))\b""")
    private val DAY = Regex("""\b(today|tomorrow|(?:next )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\b""")
    private val TIME = Regex("""\bat (noon|midnight|(\d{1,2})(?::(\d{2}))?(?: ?(am|pm))?)\b|\b(\d{1,2})(?::(\d{2}))? ?(am|pm)\b""")
    private val ARTICLES = Regex("""^(?:a|an|the|my) """)
    private val SESSION_WORD = Regex(""" (?:session|block)$""")
    private const val DEFAULT_MINUTES = 60
    private const val MAX_MINUTES = 1440

    public fun parse(text: String, now: ZonedDateTime): VoiceCommand? {
        val line = normalize(text)
        ADD_HABIT.matchEntire(line)?.let { return VoiceCommand.AddHabit(it.groupValues[1]) }
        REMOVE_HABIT.matchEntire(line)?.let { return VoiceCommand.RemoveHabit(it.groupValues[1]) }
        MARK.matchEntire(line)?.let { match ->
            val (name, state, day) = match.destructured
            return VoiceCommand.MarkHabit(name, dateFor(day, now), state !in setOf("not done", "missed", "failed"))
        }
        DID.matchEntire(line)?.let { match ->
            val (name, day) = match.destructured
            return VoiceCommand.MarkHabit(name, dateFor(day, now), true)
        }
        SCHEDULE.matchEntire(line)?.let { return parseSchedule(it.groupValues[1], now) }
        CANCEL.matchEntire(line)?.let { return parseCancel(it.groupValues[1], now) }
        return null
    }

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace("a.m.", "am")
        .replace("p.m.", "pm")
        .replace(Regex("""[,.!?;"]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .removePrefix("please ")

    private fun dateFor(day: String, now: ZonedDateTime): LocalDate =
        if (day == "yesterday") now.toLocalDate().minusDays(1) else now.toLocalDate()

    private fun parseSchedule(raw: String, now: ZonedDateTime): VoiceCommand? {
        var rest = SpokenNumbers.toDigits(raw)
        val duration = DURATION.find(rest)
        val minutes = if (duration == null) DEFAULT_MINUTES else minutesFrom(duration) ?: return null
        duration?.let { rest = rest.replace(it.value, " ") }
        val parts = extractWhen(rest)
        val start = startFrom(parts.day, parts.time ?: return null, now, requireFuture = true) ?: return null
        val tag = tagFrom(parts.rest) ?: return null
        return VoiceCommand.AddSession(tag, start, minutes)
    }

    private fun parseCancel(raw: String, now: ZonedDateTime): VoiceCommand? {
        val parts = extractWhen(SpokenNumbers.toDigits(raw))
        val tag = tagFrom(parts.rest) ?: return null
        val time = parts.time ?: return VoiceCommand.CancelSession(tag, null)
        val start = startFrom(parts.day, time, now, requireFuture = false) ?: return null
        return VoiceCommand.CancelSession(tag, start)
    }

    private class WhenParts(val day: String?, val time: MatchResult?, val rest: String)

    private fun extractWhen(text: String): WhenParts {
        val day = DAY.find(text)
        val withoutDay = day?.let { text.replace(it.value, " ") } ?: text
        val time = TIME.find(withoutDay)
        return WhenParts(day?.value, time, time?.let { withoutDay.replace(it.value, " ") } ?: withoutDay)
    }

    private fun tagFrom(rest: String): String? = rest
        .replace(Regex("""\s+"""), " ")
        .trim()
        .replace(ARTICLES, "")
        .replace(SESSION_WORD, "")
        .takeIf(String::isNotBlank)

    private fun minutesFrom(match: MatchResult): Int? {
        val amount = match.groupValues[2].toIntOrNull()
        val minutes = when {
            match.groupValues[1] == "half an hour" -> 30
            match.groupValues[1] == "an hour" -> 60
            amount == null -> return null
            match.groupValues[3].startsWith("hour") -> amount * 60
            else -> amount
        }
        return minutes.takeIf { it in 1..MAX_MINUTES }
    }

    private fun startFrom(day: String?, time: MatchResult, now: ZonedDateTime, requireFuture: Boolean): Instant? {
        val local = localTime(time) ?: return null
        val date = when {
            day == null -> if (local.isAfter(now.toLocalTime())) now.toLocalDate() else now.toLocalDate().plusDays(1)
            day == "today" -> now.toLocalDate()
            day == "tomorrow" -> now.toLocalDate().plusDays(1)
            else -> weekdayDate(day, local, now)
        }
        val start = date.atTime(local).atZone(now.zone).toInstant()
        return start.takeUnless { requireFuture && !it.isAfter(now.toInstant()) }
    }

    private fun weekdayDate(day: String, time: LocalTime, now: ZonedDateTime): LocalDate {
        val weekday = DayOfWeek.valueOf(day.removePrefix("next ").uppercase(Locale.ROOT))
        val next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(weekday))
        val isPast = next == now.toLocalDate() && !time.isAfter(now.toLocalTime())
        return if (isPast || day.startsWith("next ") && next == now.toLocalDate()) next.plusWeeks(1) else next
    }

    private fun localTime(match: MatchResult): LocalTime? {
        if (match.groupValues[1] == "noon") return LocalTime.NOON
        if (match.groupValues[1] == "midnight") return LocalTime.MIDNIGHT
        val explicit = match.groupValues[5].isNotEmpty()
        val hour = (if (explicit) match.groupValues[5] else match.groupValues[2]).toIntOrNull() ?: return null
        val minute = (if (explicit) match.groupValues[6] else match.groupValues[3]).ifEmpty { "0" }.toIntOrNull() ?: return null
        val meridiem = if (explicit) match.groupValues[7] else match.groupValues[4]
        val resolved = when {
            meridiem == "am" && hour in 1..12 -> hour % 12
            meridiem == "pm" && hour in 1..12 -> hour % 12 + 12
            meridiem.isNotEmpty() -> return null
            hour in 1..6 -> hour + 12
            else -> hour
        }
        return try {
            LocalTime.of(resolved, minute)
        } catch (_: DateTimeException) {
            null
        }
    }
}
