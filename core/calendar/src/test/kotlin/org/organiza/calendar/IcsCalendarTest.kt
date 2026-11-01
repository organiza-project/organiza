package org.organiza.calendar

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class IcsCalendarTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val from = Instant.parse("2026-01-01T00:00:00Z")
    private val to = Instant.parse("2027-01-01T00:00:00Z")

    private fun ics(vararg lines: String) = (
        listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT") + lines + listOf("END:VEVENT", "END:VCALENDAR")
        ).joinToString("\r\n")

    private fun read(text: String, zone: ZoneId = paris) = IcsCalendar.read(text, from, to, zone)

    private fun starts(result: CalendarRead) = result.occurrences.map { it.start.toString() }

    @Test
    fun `reads a utc event`() {
        val result = read(
            ics("UID:a", "SUMMARY:Write #focus", "DTSTART:20260310T090000Z", "DTEND:20260310T100000Z"),
        )
        val occurrence = result.occurrences.single()
        assertEquals("a", occurrence.uid)
        assertEquals(Instant.parse("2026-03-10T09:00:00Z"), occurrence.start)
        assertEquals(Instant.parse("2026-03-10T10:00:00Z"), occurrence.end)
        assertEquals(setOf("#focus"), occurrence.tags)
        assertEquals(emptyList(), result.issues)
    }

    @Test
    fun `applies a named time zone`() {
        val result = read(
            ics("UID:a", "DTSTART;TZID=America/New_York:20260310T090000", "DURATION:PT1H"),
        )
        assertEquals(Instant.parse("2026-03-10T13:00:00Z"), result.occurrences.single().start)
        assertEquals(Instant.parse("2026-03-10T14:00:00Z"), result.occurrences.single().end)
    }

    @Test
    fun `floating times use the device zone`() {
        val result = read(ics("UID:a", "DTSTART:20260310T090000", "DURATION:PT30M"))
        assertEquals(Instant.parse("2026-03-10T08:00:00Z"), result.occurrences.single().start)
    }

    @Test
    fun `all day events cover the local day`() {
        val result = read(ics("UID:a", "DTSTART;VALUE=DATE:20260310"))
        val occurrence = result.occurrences.single()
        assertEquals(Instant.parse("2026-03-09T23:00:00Z"), occurrence.start)
        assertEquals(Instant.parse("2026-03-10T23:00:00Z"), occurrence.end)
    }

    @Test
    fun `unfolds lines and accepts bare newlines`() {
        val text = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:a\nDESCRIPTION:long text with\n  #deep\n DTAG end #focus\n" +
            "DTSTART:20260310T090000Z\nDURATION:PT1H\nEND:VEVENT\nEND:VCALENDAR\n"
        assertEquals(setOf("#deepdtag", "#focus"), read(text).occurrences.single().tags)
    }

    @Test
    fun `finds tags in summary description and categories without partial matches`() {
        val result = read(
            ics(
                "UID:a",
                "SUMMARY:#Focus and issue#12 and ##x",
                "DESCRIPTION:see #deepwork\\, then rest",
                "CATEGORIES:#admin,plain",
                "DTSTART:20260310T090000Z",
                "DURATION:PT1H",
            ),
        )
        assertEquals(setOf("#focus", "#deepwork", "#admin"), result.occurrences.single().tags)
    }

    @Test
    fun `cancelled events are ignored`() {
        val result = read(ics("UID:a", "STATUS:CANCELLED", "DTSTART:20260310T090000Z", "DURATION:PT1H"))
        assertEquals(emptyList(), result.occurrences)
        assertEquals(emptyList(), result.issues)
    }

    @Test
    fun `events outside the window are not returned`() {
        val result = read(ics("UID:a", "DTSTART:20250310T090000Z", "DURATION:PT1H"))
        assertEquals(emptyList(), result.occurrences)
    }

    @Test
    fun `an event still running at the window start is returned`() {
        val result = read(ics("UID:a", "DTSTART:20251231T233000Z", "DURATION:PT1H"))
        assertEquals(1, result.occurrences.size)
    }

    @Test
    fun `daily recurrence honours count and interval`() {
        val result = read(
            ics("UID:a", "DTSTART:20260310T090000Z", "DURATION:PT1H", "RRULE:FREQ=DAILY;INTERVAL=2;COUNT=3"),
        )
        assertEquals(
            listOf("2026-03-10T09:00:00Z", "2026-03-12T09:00:00Z", "2026-03-14T09:00:00Z"),
            starts(result),
        )
    }

    @Test
    fun `weekly byday recurrence stops at until`() {
        val result = read(
            ics(
                "UID:a",
                "DTSTART:20260302T090000Z",
                "DURATION:PT1H",
                "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260309T090000Z",
            ),
        )
        assertEquals(
            listOf("2026-03-02T09:00:00Z", "2026-03-04T09:00:00Z", "2026-03-09T09:00:00Z"),
            starts(result),
        )
    }

    @Test
    fun `recurrence keeps local time across a daylight saving change`() {
        val result = read(
            ics("UID:a", "DTSTART;TZID=Europe/Paris:20260328T090000", "DURATION:PT1H", "RRULE:FREQ=DAILY;COUNT=2"),
        )
        assertEquals(listOf("2026-03-28T08:00:00Z", "2026-03-29T07:00:00Z"), starts(result))
    }

    @Test
    fun `monthly recurrence supports month days and ordinal weekdays`() {
        val byDay = read(
            ics("UID:a", "DTSTART:20260310T090000Z", "DURATION:PT1H", "RRULE:FREQ=MONTHLY;BYDAY=2TU;COUNT=2"),
        )
        assertEquals(listOf("2026-03-10T09:00:00Z", "2026-04-14T09:00:00Z"), starts(byDay))
        val lastFriday = read(
            ics("UID:b", "DTSTART:20260327T090000Z", "DURATION:PT1H", "RRULE:FREQ=MONTHLY;BYDAY=-1FR;COUNT=2"),
        )
        assertEquals(listOf("2026-03-27T09:00:00Z", "2026-04-24T09:00:00Z"), starts(lastFriday))
    }

    @Test
    fun `monthly recurrence skips months without that day`() {
        val result = read(
            ics("UID:a", "DTSTART:20260131T090000Z", "DURATION:PT1H", "RRULE:FREQ=MONTHLY;COUNT=2"),
        )
        assertEquals(listOf("2026-01-31T09:00:00Z", "2026-03-31T09:00:00Z"), starts(result))
    }

    @Test
    fun `yearly recurrence repeats the date`() {
        val result = read(
            ics("UID:a", "DTSTART:20250310T090000Z", "DURATION:PT1H", "RRULE:FREQ=YEARLY;COUNT=2"),
        )
        assertEquals(listOf("2026-03-10T09:00:00Z"), starts(result))
    }

    @Test
    fun `exdate removes single occurrences`() {
        val result = read(
            ics(
                "UID:a",
                "DTSTART:20260310T090000Z",
                "DURATION:PT1H",
                "RRULE:FREQ=DAILY;COUNT=3",
                "EXDATE:20260311T090000Z",
            ),
        )
        assertEquals(listOf("2026-03-10T09:00:00Z", "2026-03-12T09:00:00Z"), starts(result))
    }

    @Test
    fun `recurrence id replaces one occurrence`() {
        val text = listOf(
            "BEGIN:VCALENDAR",
            "BEGIN:VEVENT", "UID:a", "SUMMARY:#focus", "DTSTART:20260310T090000Z", "DURATION:PT1H",
            "RRULE:FREQ=DAILY;COUNT=2", "END:VEVENT",
            "BEGIN:VEVENT", "UID:a", "SUMMARY:moved #deep", "RECURRENCE-ID:20260311T090000Z",
            "DTSTART:20260311T150000Z", "DURATION:PT1H", "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n")
        val occurrences = read(text).occurrences
        assertEquals(listOf("2026-03-10T09:00:00Z", "2026-03-11T15:00:00Z"), occurrences.map { it.start.toString() })
        assertEquals(setOf("#deep"), occurrences.last().tags)
    }

    @Test
    fun `a cancelled override removes the occurrence`() {
        val text = listOf(
            "BEGIN:VCALENDAR",
            "BEGIN:VEVENT", "UID:a", "DTSTART:20260310T090000Z", "DURATION:PT1H", "RRULE:FREQ=DAILY;COUNT=2", "END:VEVENT",
            "BEGIN:VEVENT", "UID:a", "STATUS:CANCELLED", "RECURRENCE-ID:20260311T090000Z",
            "DTSTART:20260311T090000Z", "DURATION:PT1H", "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n")
        assertEquals(listOf("2026-03-10T09:00:00Z"), starts(read(text)))
    }

    @Test
    fun `unsupported recurrence is reported and skipped not guessed`() {
        val result = read(
            ics("UID:a", "DTSTART:20260310T090000Z", "DURATION:PT1H", "RRULE:FREQ=WEEKLY;BYSETPOS=1;BYDAY=MO"),
        )
        assertEquals(emptyList(), result.occurrences)
        assertEquals(listOf(CalendarIssue("a", CalendarIssue.Kind.UNSUPPORTED_RECURRENCE)), result.issues)
    }

    @Test
    fun `problems are reported per event and others still load`() {
        val text = listOf(
            "BEGIN:VCALENDAR",
            "BEGIN:VEVENT", "UID:zone", "DTSTART;TZID=Mars/Olympus:20260310T090000", "DURATION:PT1H", "END:VEVENT",
            "BEGIN:VEVENT", "UID:date", "DTSTART:notadate", "DURATION:PT1H", "END:VEVENT",
            "BEGIN:VEVENT", "DTSTART:20260310T090000Z", "DURATION:PT1H", "END:VEVENT",
            "BEGIN:VEVENT", "UID:empty", "DTSTART:20260310T090000Z", "END:VEVENT",
            "BEGIN:VEVENT", "UID:back", "DTSTART:20260310T090000Z", "DTEND:20260310T080000Z", "END:VEVENT",
            "BEGIN:VEVENT", "UID:good", "DTSTART:20260310T090000Z", "DURATION:PT1H", "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n")
        val result = read(text)
        assertEquals(listOf("good"), result.occurrences.map { it.uid })
        assertEquals(
            listOf(
                CalendarIssue("zone", CalendarIssue.Kind.UNKNOWN_TIME_ZONE),
                CalendarIssue("date", CalendarIssue.Kind.INVALID_DATE),
                CalendarIssue(null, CalendarIssue.Kind.MISSING_UID),
                CalendarIssue("empty", CalendarIssue.Kind.NO_DURATION),
                CalendarIssue("back", CalendarIssue.Kind.NO_DURATION),
            ),
            result.issues,
        )
    }

    @Test
    fun `recurrence expansion is capped`() {
        val result = IcsCalendar.read(
            ics("UID:a", "DTSTART:20260101T000000Z", "DURATION:PT1M", "RRULE:FREQ=DAILY"),
            from,
            Instant.parse("2200-01-01T00:00:00Z"),
            paris,
        )
        assertEquals(IcsCalendar.MAX_OCCURRENCES, result.occurrences.size)
        assertEquals(listOf(CalendarIssue(null, CalendarIssue.Kind.TOO_MANY_OCCURRENCES)), result.issues)
    }

    @Test
    fun `oversized input is refused`() {
        val result = read("X".repeat(IcsCalendar.MAX_INPUT_CHARS + 1))
        assertEquals(emptyList(), result.occurrences)
        assertEquals(listOf(CalendarIssue(null, CalendarIssue.Kind.TOO_LARGE)), result.issues)
    }

    @Test
    fun `output is deterministic and sorted`() {
        val text = listOf(
            "BEGIN:VCALENDAR",
            "BEGIN:VEVENT", "UID:late", "DTSTART:20260310T120000Z", "DURATION:PT1H", "END:VEVENT",
            "BEGIN:VEVENT", "UID:early", "DTSTART:20260310T090000Z", "DURATION:PT1H", "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n")
        assertEquals(listOf("early", "late"), read(text).occurrences.map { it.uid })
        assertEquals(read(text), read(text))
    }

    @Test
    fun `garbage never throws`() {
        val random = Random(7)
        val valid = ics(
            "UID:a", "SUMMARY:#focus", "DTSTART;TZID=Europe/Paris:20260310T090000", "DURATION:PT1H",
            "RRULE:FREQ=MONTHLY;BYDAY=2TU;COUNT=3", "EXDATE:20260311T090000Z",
        )
        repeat(20_000) {
            val chars = valid.toCharArray()
            repeat(random.nextInt(1, 6)) { chars[random.nextInt(chars.size)] = random.nextInt(32, 127).toChar() }
            read(String(chars))
        }
    }
}
