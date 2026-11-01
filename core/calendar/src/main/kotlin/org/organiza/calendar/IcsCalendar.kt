package org.organiza.calendar

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

public data class Occurrence(
    val uid: String,
    val start: Instant,
    val end: Instant,
    val tags: Set<String>,
)

public data class CalendarIssue(val uid: String?, val kind: Kind) {
    public enum class Kind {
        MISSING_UID,
        INVALID_DATE,
        UNKNOWN_TIME_ZONE,
        NO_DURATION,
        UNSUPPORTED_RECURRENCE,
        TOO_MANY_OCCURRENCES,
        TOO_LARGE,
    }
}

public data class CalendarRead(val occurrences: List<Occurrence>, val issues: List<CalendarIssue>)

/**
 * Reads an RFC 5545 subset (ADR-0008). Input is untrusted, so expansion is bounded, and
 * anything that cannot be interpreted faithfully becomes an issue rather than a guess.
 * The same input and arguments always give the same result.
 */
public object IcsCalendar {
    public const val MAX_INPUT_CHARS: Int = 5_000_000
    public const val MAX_OCCURRENCES: Int = 5_000

    private val TAG = Regex("""(?<![\p{L}\p{N}_#])#[\p{L}\p{N}_-]+""")
    private val DURATION_WEEKS = Regex("""P(\d{1,4})W""")

    public fun read(text: String, from: Instant, to: Instant, deviceZone: ZoneId): CalendarRead {
        if (text.length > MAX_INPUT_CHARS) {
            return CalendarRead(emptyList(), listOf(CalendarIssue(null, CalendarIssue.Kind.TOO_LARGE)))
        }
        val issues = mutableListOf<CalendarIssue>()
        val events = rawEvents(text).mapNotNull { event ->
            try {
                parseEvent(event, deviceZone)
            } catch (problem: EventProblem) {
                issues += CalendarIssue(event.first("UID")?.value, problem.kind)
                null
            } catch (_: DateTimeException) {
                issues += CalendarIssue(event.first("UID")?.value, CalendarIssue.Kind.INVALID_DATE)
                null
            } catch (_: ArithmeticException) {
                issues += CalendarIssue(event.first("UID")?.value, CalendarIssue.Kind.INVALID_DATE)
                null
            }
        }
        val overrides = events.filter { it.recurrenceId != null }
        val replaced = overrides.mapNotNull { override -> override.recurrenceId?.let { override.uid to it } }.toSet()
        val occurrences = mutableListOf<Occurrence>()
        for (event in events) {
            if (event.cancelled) continue
            try {
                event.expand(to, MAX_OCCURRENCES + 1) { start, end ->
                    val isReplacedOriginal = event.recurrenceId == null && (event.uid to start) in replaced
                    if (!isReplacedOriginal && end > from && start < to) {
                        occurrences += Occurrence(event.uid, start, end, event.tags)
                    }
                    occurrences.size <= MAX_OCCURRENCES
                }
            } catch (_: DateTimeException) {
                issues += CalendarIssue(event.uid, CalendarIssue.Kind.INVALID_DATE)
            } catch (_: ArithmeticException) {
                issues += CalendarIssue(event.uid, CalendarIssue.Kind.INVALID_DATE)
            }
        }
        if (occurrences.size > MAX_OCCURRENCES) issues += CalendarIssue(null, CalendarIssue.Kind.TOO_MANY_OCCURRENCES)
        val sorted = occurrences.sortedWith(compareBy(Occurrence::start, Occurrence::uid)).take(MAX_OCCURRENCES)
        return CalendarRead(sorted, issues)
    }

    private class EventProblem(val kind: CalendarIssue.Kind) : Exception()

    private class Property(val name: String, val params: Map<String, String>, val value: String)

    private class RawEvent(val properties: List<Property>) {
        fun first(name: String): Property? = properties.firstOrNull { it.name == name }

        fun all(name: String): List<Property> = properties.filter { it.name == name }
    }

    private fun rawEvents(text: String): List<RawEvent> {
        val unfolded = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "")
        val events = mutableListOf<RawEvent>()
        var current: MutableList<Property>? = null
        var nested = 0
        for (line in unfolded.split('\n')) {
            val property = parseLine(line) ?: continue
            val value = property.value.trim().uppercase(Locale.ROOT)
            when {
                property.name == "BEGIN" && value == "VEVENT" && current == null -> current = mutableListOf()
                property.name == "BEGIN" && current != null -> nested++
                property.name == "END" && nested > 0 -> nested--
                property.name == "END" && value == "VEVENT" -> {
                    current?.let { events += RawEvent(it) }
                    current = null
                }
                nested == 0 -> current?.add(property)
            }
        }
        return events
    }

    private fun parseLine(line: String): Property? {
        var quoted = false
        var colon = -1
        for ((index, char) in line.withIndex()) {
            if (char == '"') quoted = !quoted
            if (char == ':' && !quoted) {
                colon = index
                break
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { param ->
            val separator = param.indexOf('=')
            if (separator <= 0) null else param.substring(0, separator).uppercase(Locale.ROOT) to
                param.substring(separator + 1).trim('"')
        }.toMap()
        return Property(head.first().uppercase(Locale.ROOT), params, line.substring(colon + 1))
    }

    private fun parseEvent(raw: RawEvent, deviceZone: ZoneId): Event {
        val uid = raw.first("UID")?.value?.trim()?.takeIf(String::isNotEmpty)
            ?: throw EventProblem(CalendarIssue.Kind.MISSING_UID)
        val cancelled = raw.first("STATUS")?.value?.trim().equals("CANCELLED", ignoreCase = true)
        val startProperty = raw.first("DTSTART") ?: throw EventProblem(CalendarIssue.Kind.INVALID_DATE)
        val start = parseMoment(startProperty, deviceZone)
        val length = parseLength(raw, start, deviceZone)
        val excluded = raw.all("EXDATE").flatMap { property ->
            property.value.split(',').map { parseMoment(Property("EXDATE", property.params, it), deviceZone).instant() }
        }.toSet()
        if (raw.first("RDATE") != null || raw.first("EXRULE") != null || raw.all("RRULE").size > 1) {
            throw EventProblem(CalendarIssue.Kind.UNSUPPORTED_RECURRENCE)
        }
        val rule = raw.first("RRULE")?.let { Recurrence.parse(it.value) { value ->
            parseMoment(Property("UNTIL", emptyMap(), value), deviceZone).instant()
        } ?: throw EventProblem(CalendarIssue.Kind.UNSUPPORTED_RECURRENCE) }
        return Event(
            uid = uid,
            cancelled = cancelled,
            start = start,
            length = length,
            rule = rule,
            excluded = excluded,
            recurrenceId = raw.first("RECURRENCE-ID")?.let { parseMoment(it, deviceZone).instant() },
            tags = tags(raw),
        )
    }

    private fun tags(raw: RawEvent): Set<String> =
        listOf("SUMMARY", "DESCRIPTION", "CATEGORIES")
            .flatMap(raw::all)
            .flatMap { property -> TAG.findAll(unescape(property.value)).map { it.value.lowercase(Locale.ROOT) } }
            .toSet()

    private fun unescape(value: String): String =
        value.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun parseMoment(property: Property, deviceZone: ZoneId): Moment {
        val value = property.value.trim()
        val date = parseDate(value)
        if (value.length == 8) return Moment(date.atStartOfDay(), deviceZone, allDay = true)
        if (value.length !in 15..16 || value[8] != 'T') throw EventProblem(CalendarIssue.Kind.INVALID_DATE)
        val digits = value.substring(9, 15)
        if (!digits.all(Char::isDigit)) throw EventProblem(CalendarIssue.Kind.INVALID_DATE)
        val time = date.atTime(digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(), digits.substring(4, 6).toInt())
        val utc = value.length == 16
        if (utc && value[15] != 'Z') throw EventProblem(CalendarIssue.Kind.INVALID_DATE)
        return Moment(time, if (utc) ZoneOffset.UTC else zone(property.params["TZID"], deviceZone), allDay = false)
    }

    private fun parseDate(value: String): LocalDate {
        if (value.length < 8 || !value.take(8).all(Char::isDigit)) throw EventProblem(CalendarIssue.Kind.INVALID_DATE)
        return LocalDate.of(value.substring(0, 4).toInt(), value.substring(4, 6).toInt(), value.substring(6, 8).toInt())
    }

    private fun zone(tzid: String?, deviceZone: ZoneId): ZoneId {
        if (tzid == null) return deviceZone
        try {
            return ZoneId.of(tzid)
        } catch (_: DateTimeException) {
            throw EventProblem(CalendarIssue.Kind.UNKNOWN_TIME_ZONE)
        }
    }

    private fun parseLength(raw: RawEvent, start: Moment, deviceZone: ZoneId): Length {
        val endProperty = raw.first("DTEND")
        val durationProperty = raw.first("DURATION")
        val length = when {
            endProperty != null -> {
                val end = parseMoment(endProperty, deviceZone)
                if (start.allDay) Length.Days(ChronoUnit.DAYS.between(start.local, end.local))
                else Length.Exact(Duration.between(start.instant(), end.instant()))
            }
            durationProperty != null -> Length.Exact(parseDuration(durationProperty.value.trim()))
            start.allDay -> Length.Days(1)
            else -> throw EventProblem(CalendarIssue.Kind.NO_DURATION)
        }
        if (!length.isPositive) throw EventProblem(CalendarIssue.Kind.NO_DURATION)
        return length
    }

    private fun parseDuration(value: String): Duration {
        val weeks = DURATION_WEEKS.matchEntire(value)?.groupValues?.get(1)?.toLong()
        if (weeks != null) return Duration.ofDays(weeks * 7)
        try {
            return Duration.parse(value)
        } catch (_: java.time.format.DateTimeParseException) {
            throw EventProblem(CalendarIssue.Kind.NO_DURATION)
        }
    }

    private data class Moment(val local: LocalDateTime, val zone: ZoneId, val allDay: Boolean) {
        fun instant(): Instant = local.atZone(zone).toInstant()
    }

    private sealed interface Length {
        val isPositive: Boolean

        data class Exact(val duration: Duration) : Length {
            override val isPositive get() = !duration.isNegative && !duration.isZero
        }

        data class Days(val days: Long) : Length {
            override val isPositive get() = days > 0
        }
    }

    private class Event(
        val uid: String,
        val cancelled: Boolean,
        val start: Moment,
        val length: Length,
        val rule: Recurrence?,
        val excluded: Set<Instant>,
        val recurrenceId: Instant?,
        val tags: Set<String>,
    ) {
        /** Calls [emit] with each start and end, oldest first, until it returns false. */
        fun expand(to: Instant, limit: Int, emit: (Instant, Instant) -> Boolean) {
            val starts = rule?.dates(start.local.toLocalDate()) ?: sequenceOf(start.local.toLocalDate())
            var emitted = 0
            for ((index, date) in starts.withIndex()) {
                val local = date.atTime(start.local.toLocalTime()).atZone(start.zone)
                val instant = local.toInstant()
                if (instant >= to || rule?.isPast(index, instant) == true) return
                if (instant in excluded) continue
                val end = when (length) {
                    is Length.Exact -> instant.plus(length.duration)
                    is Length.Days -> date.plusDays(length.days).atStartOfDay(start.zone).toInstant()
                }
                if (!emit(instant, end) || ++emitted >= limit) return
            }
        }
    }
}
