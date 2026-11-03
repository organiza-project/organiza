package org.organiza.voice

import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.organiza.ai.AiFailure
import org.organiza.ai.ApiKey
import org.organiza.ai.GroqClient
import org.organiza.ai.GroqEndpoint
import org.organiza.ai.Sanitizer
import org.organiza.domain.Parsed
import org.organiza.testing.FakeApiKeyStore
import org.organiza.testing.FakeHttpTransport

class VoicePipelineTest {
    private val now = ZonedDateTime.of(2026, 11, 1, 15, 0, 0, 0, ZoneId.of("Europe/Paris"))
    private val today = LocalDate.of(2026, 11, 1)
    private val transport = FakeHttpTransport()
    private val store = FakeApiKeyStore().apply { save((ApiKey.parse("gsk_test") as Parsed.Valid).value) }
    private val pipeline = VoicePipeline(store, GroqClient(transport, GroqEndpoint.PRODUCTION), Sanitizer())

    private val speech = ShortArray(16_000) { if (it % 2 == 0) 8000 else -8000 }
    private val silence = ShortArray(16_000)
    private val quiet = ShortArray(16_000) { if (it % 2 == 0) 20 else -20 }

    private fun chat(content: String) =
        """{"choices":[{"message":{"content":"${content.replace("\"", "\\\"")}"}}]}"""

    private fun transcribed(text: String) = """{"text":"$text"}"""

    @Test
    fun `wav encoding has a correct header`() {
        val wav = Wav.encode(shortArrayOf(1, -1), 16_000)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(48, wav.size)
        assertEquals(40, wav[4].toInt())
        assertEquals(1, wav[22].toInt())
        assertEquals(4, wav[40].toInt())
        assertEquals(1, wav[44].toInt())
        assertEquals(-1, wav[46].toInt())
    }

    @Test
    fun `silence and near silence never leave the device`() {
        assertEquals(VoiceResult.Silent, pipeline.fromAudio(silence, 16_000, now))
        assertEquals(VoiceResult.Silent, pipeline.fromAudio(quiet, 16_000, now))
        assertEquals(VoiceResult.Silent, pipeline.fromAudio(ShortArray(0), 16_000, now))
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `audio is sent as multipart and parsed locally with no chat call`() {
        transport.respond(200, transcribed(" Mark stretch as done."))
        assertEquals(
            VoiceResult.Understood(VoiceCommand.MarkHabit("stretch", today, true), viaModel = false),
            pipeline.fromAudio(speech, 16_000, now),
        )
        val request = transport.requests.single()
        assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", request.url)
        assertEquals("Bearer gsk_test", request.headers["Authorization"])
        assertTrue(request.headers.getValue("Content-Type").startsWith("multipart/form-data; boundary="))
        assertTrue("name=\"model\"" in request.body && "whisper-large-v3-turbo" in request.body)
        assertTrue("filename=\"speech.wav\"" in request.body)
        assertTrue("RIFF" in request.body)
    }

    @Test
    fun `unparsed transcripts are sanitized and the rewrite goes back through the grammar`() {
        transport.respond(200, chat("""{"command":"mark evening run as done"}"""))
        val result = pipeline.fromTranscript(
            "Log my evening run, Sarah Connor saw it at https://x.example/a",
            now,
            knownHabits = listOf("evening run"),
        )
        assertEquals(VoiceResult.Understood(VoiceCommand.MarkHabit("evening run", today, true), viaModel = true), result)
        val body = transport.requests.single().body
        assertFalse("Sarah" in body)
        assertFalse("x.example" in body)
        assertTrue("evening run" in body)
    }

    @Test
    fun `model output outside the grammar is refused`() {
        for (reply in listOf(
            """{"command":"delete everything"}""",
            """{"command":"end the strict session"}""",
            """{"command":null}""",
            """{"action":"cancel"}""",
            "not json",
        )) {
            transport.respond(200, chat(reply))
            assertEquals(VoiceResult.NotUnderstood, pipeline.fromTranscript("do something odd", now, emptyList()), reply)
        }
    }

    @Test
    fun `sanitizer placeholders can never become a command`() {
        transport.respond(200, chat("""{"command":"add habit [NAME]"}"""))
        assertEquals(VoiceResult.NotUnderstood, pipeline.fromTranscript("track Sarah Connor", now, emptyList()))
    }

    @Test
    fun `whisper hallucinations cannot act`() {
        transport.respond(200, transcribed(" Thank you."))
        val first = pipeline.fromAudio(speech, 16_000, now)
        assertTrue(first is VoiceResult.NotUnderstood || first is VoiceResult.Failed, "$first")
    }

    @Test
    fun `failures are reported and no key means no upload`() {
        transport.fail(IOException("offline"))
        assertEquals(VoiceResult.Failed(AiFailure.OFFLINE), pipeline.fromAudio(speech, 16_000, now))
        transport.respond(401, "{}")
        assertEquals(VoiceResult.Failed(AiFailure.INVALID_KEY), pipeline.fromAudio(speech, 16_000, now))
        transport.respond(200, "not json")
        assertEquals(VoiceResult.Failed(AiFailure.MALFORMED), pipeline.fromAudio(speech, 16_000, now))
        val sent = transport.requests.size
        store.clear()
        assertEquals(VoiceResult.Failed(AiFailure.NO_KEY), pipeline.fromAudio(speech, 16_000, now))
        assertEquals(sent, transport.requests.size)
    }

    @Test
    fun `overlong recordings are refused`() {
        val tooLong = ShortArray(16_000 * (VoicePipeline.MAX_SECONDS + 1)) { 8000 }
        assertEquals(VoiceResult.TooLong, pipeline.fromAudio(tooLong, 16_000, now))
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `commands describe themselves for confirmation`() {
        assertEquals("Add habit: stretch", VoiceCommand.AddHabit("stretch").describe())
        assertEquals("Remove habit: stretch", VoiceCommand.RemoveHabit("stretch").describe())
        assertEquals("Mark stretch done on 2026-11-01", VoiceCommand.MarkHabit("stretch", today, true).describe())
        assertEquals("Mark stretch missed on 2026-11-01", VoiceCommand.MarkHabit("stretch", today, false).describe())
    }

    @Test
    fun `dictation returns the transcript itself, and never sends silence`() {
        transport.respond(200, transcribed(" Monday nine to eleven, maths."))
        assertEquals(VoiceResult.Transcript("Monday nine to eleven, maths."), pipeline.transcript(speech, 16_000))
        val before = transport.requests.size
        assertEquals(VoiceResult.Silent, pipeline.transcript(silence, 16_000))
        assertEquals(before, transport.requests.size)
    }
}
