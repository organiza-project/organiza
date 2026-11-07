package org.organiza.voice

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandParserTest {
    private val zone = ZoneId.of("Europe/Paris")
    // A Sunday afternoon.
    private val now = ZonedDateTime.of(2026, 11, 1, 15, 0, 0, 0, zone)
    private val today = LocalDate.of(2026, 11, 1)

    private fun parse(text: String) = CommandParser.parse(text, now)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant()

    @Test
    fun `adds habits as whisper writes them`() {
        assertEquals(VoiceCommand.AddHabit("read 10 pages"), parse(" Add a habit called read 10 pages."))
        assertEquals(VoiceCommand.AddHabit("stretch"), parse("New habit stretch"))
        assertEquals(VoiceCommand.AddHabit("evening run"), parse("Please create a new habit named evening run!"))
    }

    @Test
    fun `removes habits`() {
        assertEquals(VoiceCommand.RemoveHabit("read 10 pages"), parse(" Remove the habit, read 10 pages."))
        assertEquals(VoiceCommand.RemoveHabit("stretch"), parse("delete my habit stretch"))
    }

    @Test
    fun `marks habits done for today by default`() {
        assertEquals(VoiceCommand.MarkHabit("read 10 pages", today, true), parse(" Mark read 10 pages as done."))
        assertEquals(VoiceCommand.MarkHabit("stretch", today, true), parse("mark the habit stretch complete"))
        assertEquals(VoiceCommand.MarkHabit("evening run", today, true), parse(" I finished my evening run today."))
    }

    @Test
    fun `marks habits for yesterday and as missed`() {
        assertEquals(
            VoiceCommand.MarkHabit("stretch", today.minusDays(1), true),
            parse("mark stretch as done yesterday"),
        )
        assertEquals(VoiceCommand.MarkHabit("stretch", today, false), parse("mark stretch as missed"))
        assertEquals(VoiceCommand.MarkHabit("stretch", today, false), parse("mark stretch not done"))
        assertEquals(VoiceCommand.MarkHabit("stretch", today.minusDays(1), true), parse("I did my stretch yesterday"))
    }

    @Test
    fun `schedules sessions as whisper writes them`() {
        assertEquals(
            VoiceCommand.AddSession("focus", at(2, 9), 60),
            parse(" Schedule focus tomorrow at 9 for 1 hour."),
        )
    }

    @Test
    fun `accepts number words and other orders`() {
        val expected = VoiceCommand.AddSession("focus", at(2, 9), 60)
        assertEquals(expected, parse("schedule focus tomorrow at nine for one hour"))
        assertEquals(expected, parse("schedule a focus session for an hour tomorrow at 9 am"))
        assertEquals(
            VoiceCommand.AddSession("deep work", at(2, 21, 30), 90),
            parse("book a deep work block tomorrow at 9:30 pm for 90 minutes"),
        )
        assertEquals(
            VoiceCommand.AddSession("focus", at(2, 12), 30),
            parse("plan focus tomorrow at noon for half an hour"),
        )
    }

    @Test
    fun `resolves weekdays to the next one`() {
        assertEquals(VoiceCommand.AddSession("focus", at(4, 14), 60), parse("schedule focus wednesday at 2 for 1 hour"))
    }

    @Test
    fun `times without a day use today when still ahead and otherwise tomorrow`() {
        assertEquals(VoiceCommand.AddSession("focus", at(1, 17), 60), parse("schedule focus at 5 for 1 hour"))
        assertEquals(VoiceCommand.AddSession("focus", at(2, 9), 60), parse("schedule focus at 9 for 1 hour"))
    }

    @Test
    fun `a time already past today is not understood`() {
        assertEquals(null, parse("schedule focus today at 9 am for 1 hour"))
    }

    @Test
    fun `sessions need a time and a sensible length`() {
        assertEquals(null, parse("schedule focus tomorrow"))
        assertEquals(null, parse("schedule focus tomorrow at 25 for 1 hour"))
        assertEquals(null, parse("schedule focus tomorrow at 9 for 0 minutes"))
    }

    @Test
    fun `sessions default to one hour when no length is said`() {
        assertEquals(VoiceCommand.AddSession("focus", at(2, 9), 60), parse("schedule focus tomorrow at 9"))
    }

    @Test
    fun `cancels sessions with or without a time`() {
        assertEquals(VoiceCommand.CancelSession("focus", at(2, 9)), parse(" Cancel the focus session tomorrow at 9."))
        assertEquals(VoiceCommand.CancelSession("focus", null), parse("cancel my focus session"))
    }

    @Test
    fun `whisper noise and chat are not commands`() {
        for (text in listOf("", " Thank you.", "hello there", "Add a hobbit cold read 10 pages.", "mark", "add habit")) {
            assertEquals(null, parse(text), text)
        }
    }

    @Test
    fun `garbage never throws`() {
        val random = Random(3)
        val words = listOf("add", "habit", "mark", "done", "schedule", "at", "for", "99", "pm", "cancel", "tomorrow", ":", "9:99")
        repeat(20_000) {
            parse(List(random.nextInt(0, 9)) { words[random.nextInt(words.size)] }.joinToString(" "))
        }
    }

    @Test
    fun `adds habits with a frequency or a purpose`() {
        assertEquals(VoiceCommand.AddHabit("coding"), parse("Create a new daily habit for coding"))
        assertEquals(VoiceCommand.AddHabit("guitar"), parse("start a weekly habit called guitar"))
    }

    @Test
    fun `logs minutes against a habit`() {
        assertEquals(VoiceCommand.LogMinutes("reading", today, 30), parse("Log 30 minutes of reading."))
        assertEquals(VoiceCommand.LogMinutes("coding", today.minusDays(1), 60), parse("log an hour of coding yesterday"))
        assertEquals(VoiceCommand.LogMinutes("guitar", today, 90), parse("track 1 hour 30 minutes of my guitar"))
        assertEquals(VoiceCommand.LogMinutes("yoga", today, 45), parse("log forty-five minutes for yoga"))
    }

    @Test
    fun `meetings become calendar events, keeping the spoken name`() {
        assertEquals(VoiceCommand.AddEvent("Meeting with John", at(2, 14), 60), parse("Schedule a meeting with John tomorrow at 2 PM"))
        assertEquals(VoiceCommand.AddEvent("Call with the bank", at(2, 9), 30), parse("book a call with the bank tomorrow at 9 for 30 minutes"))
        assertEquals(VoiceCommand.AddSession("deep work", at(2, 9), 60), parse("schedule deep work tomorrow at 9"))
    }
}
