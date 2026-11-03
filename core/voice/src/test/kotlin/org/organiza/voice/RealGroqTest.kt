package org.organiza.voice

import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.organiza.ai.AiCoach
import org.organiza.ai.AiResult
import org.organiza.ai.BusyBlock
import org.organiza.ai.CopilotFacts
import org.organiza.ai.ApiKey
import org.organiza.ai.GroqClient
import org.organiza.ai.GroqEndpoint
import org.organiza.ai.HabitFacts
import org.organiza.ai.Sanitizer
import org.organiza.ai.UrlConnectionTransport
import org.organiza.ai.WeeklyFacts
import org.organiza.domain.OverrideReason
import org.organiza.domain.Parsed
import org.organiza.testing.FakeApiKeyStore

/**
 * Calls the real Groq API, so it runs only when GROQ_API_KEY is set locally. CI skips it, and the
 * key is never stored in the repository. ORGANIZA_VOICE_SAMPLES points at folder of test recordings,
 * and ORGANIZA_GROQ_MODEL tries another chat model than the default.
 */
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class RealGroqTest {
    private val store = FakeApiKeyStore().apply {
        save((ApiKey.parse(System.getenv("GROQ_API_KEY").orEmpty()) as Parsed.Valid).value)
    }
    private val client = GroqClient(
        UrlConnectionTransport(),
        GroqEndpoint.PRODUCTION,
        System.getenv("ORGANIZA_GROQ_MODEL")?.takeIf(String::isNotBlank) ?: GroqClient.DEFAULT_MODEL,
    )
    private val sanitizer = Sanitizer()
    private val coach = AiCoach(store, client, sanitizer)
    private val pipeline = VoicePipeline(store, client, sanitizer)
    private val now = ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZoneId.of("Europe/Paris"))

    @Test
    fun `weekly review parses from the real model`() {
        val facts = WeeklyFacts(5, 1, 300, 0, listOf(HabitFacts(sanitizer.clean("Read"), 4, 7)))
        val result = coach.weeklyReview(facts)
        assertTrue(result is AiResult.Ok, "$result")
        assertTrue(result.value.summary.isNotBlank())
    }

    @Test
    fun `schedule suggestion parses from the real model`() {
        val result = coach.suggestSchedule("Write my thesis for two hours each morning", emptyList())
        assertTrue(result is AiResult.Ok, "$result")
        assertTrue(result.value.blocks.isNotEmpty())
    }

    @Test
    fun `the copilot proposes blocks from counts on the real model`() {
        val facts = CopilotFacts(
            days = 14,
            attemptsByHour = List(24) { hour -> if (hour in 21..22) 6 else if (hour == 9) 3 else 0 },
            overridesByHour = List(24) { hour -> if (hour == 22) 3 else 0 },
            focusMinutes = 540,
            habits = listOf(HabitFacts(sanitizer.clean("Read"), 4, 7)),
            busy = listOf(BusyBlock(java.time.DayOfWeek.MONDAY, 9 * 60, 60, "#standup")),
        )
        val result = coach.copilot(facts)
        assertTrue(result is AiResult.Ok, "$result")
        assertTrue(result.value.focusBlocks.isNotEmpty() || result.value.warnings.isNotEmpty(), "${result.value}")
    }

    @Test
    fun `an override reflection comes back from the real model`() {
        val result = coach.reflect(30, 2, OverrideReason.BOREDOM, "I am bored and want to scroll for a while")
        assertTrue(result is AiResult.Ok, "$result")
        assertTrue(result.value.isNotBlank())
    }

    @Test
    fun `natural speech is rewritten into the grammar by the real model`() {
        val result = pipeline.fromTranscript("Could you log my evening run for today", now, listOf("evening run"))
        assertTrue(result is VoiceResult.Understood, "$result")
        assertEquals(true, result.viaModel)
        assertTrue(result.command is VoiceCommand.MarkHabit, "${result.command}")
    }

    @Test
    fun `an off topic request stays not understood on the real model`() {
        assertEquals(VoiceResult.NotUnderstood, pipeline.fromTranscript("Tell me a joke about cats", now, emptyList()))
    }

    @Test
    fun `real recordings become the expected commands`() {
        val folder = System.getenv("ORGANIZA_VOICE_SAMPLES") ?: return
        val expected = mapOf(
            "mark.wav" to VoiceCommand.MarkHabit::class,
            "remove.wav" to VoiceCommand.RemoveHabit::class,
            "schedule.wav" to VoiceCommand.AddSession::class,
            "cancel.wav" to VoiceCommand.CancelSession::class,
            "add.wav" to VoiceCommand.AddHabit::class,
            "did.wav" to VoiceCommand.MarkHabit::class,
        )
        for ((file, type) in expected) {
            val bytes = File(folder, file).readBytes()
            val samples = ShortArray((bytes.size - 44) / 2) { index ->
                ((bytes[44 + index * 2 + 1].toInt() shl 8) or (bytes[44 + index * 2].toInt() and 0xff)).toShort()
            }
            val result = pipeline.fromAudio(samples, 16_000, now)
            assertTrue(result is VoiceResult.Understood, "$file -> $result")
            assertEquals(type, result.command::class, "$file -> ${result.command}")
        }
    }
}
