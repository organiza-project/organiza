package org.organiza.ai

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.organiza.domain.InvalidReason
import org.organiza.domain.Parsed
import org.organiza.testing.FakeApiKeyStore
import org.organiza.testing.FakeHttpTransport

class GroqClientTest {
    private val sanitizer = Sanitizer()
    private val key = (ApiKey.parse("gsk_test_key_value") as Parsed.Valid).value
    private val transport = FakeHttpTransport()
    private val client = GroqClient(transport, GroqEndpoint.PRODUCTION)
    private val prompt = Prompts.gatekeeper(GatekeeperFacts(30, sanitizer.clean("I need my bank app")))

    private fun reply(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":${"\"" + content.replace("\"", "\\\"") + "\""}}}]}"""

    @Test
    fun `api keys are validated and redacted`() {
        assertEquals(Parsed.Invalid(InvalidReason.BLANK), ApiKey.parse(" "))
        assertEquals(Parsed.Invalid(InvalidReason.MALFORMED), ApiKey.parse("has space"))
        assertFalse("gsk_test_key_value" in key.toString())
        assertFalse("gsk_test_key_value" in "$key")
    }

    @Test
    fun `sends a well formed request to the fixed endpoint`() {
        transport.respond(200, reply("hi"))
        assertEquals(AiResult.Ok("hi"), client.complete(key, prompt))
        val request = transport.requests.single()
        assertEquals("https://api.groq.com/openai/v1/chat/completions", request.url)
        assertEquals("Bearer gsk_test_key_value", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertTrue("\"model\":\"openai/gpt-oss-20b\"" in request.body)
        assertTrue("\"max_completion_tokens\"" in request.body)
        assertTrue("\"json_object\"" in request.body)
        assertFalse("gsk_test_key_value" in request.body)
    }

    @Test
    fun `the model can be changed`() {
        transport.respond(200, reply("hi"))
        GroqClient(transport, GroqEndpoint.PRODUCTION, model = "other/model").complete(key, prompt)
        assertTrue("\"model\":\"other/model\"" in transport.requests.single().body)
    }

    @Test
    fun `http failures map to typed results`() {
        val cases = listOf(
            401 to AiFailure.INVALID_KEY,
            403 to AiFailure.INVALID_KEY,
            413 to AiFailure.BAD_REQUEST,
            422 to AiFailure.BAD_REQUEST,
            429 to AiFailure.RATE_LIMITED,
            500 to AiFailure.SERVER,
            503 to AiFailure.SERVER,
            498 to AiFailure.SERVER,
        )
        for ((status, failure) in cases) {
            transport.respond(status, """{"error":{"message":"x","type":"y"}}""")
            assertEquals(AiResult.Failed(failure), client.complete(key, prompt), "status $status")
        }
    }

    @Test
    fun `missing or retired models are their own failure`() {
        transport.respond(404, """{"error":{"message":"model not found","type":"invalid_request_error"}}""")
        assertEquals(AiResult.Failed(AiFailure.MODEL_UNAVAILABLE), client.complete(key, prompt))
        transport.respond(
            400,
            """{"error":{"message":"The model `x` has been decommissioned","type":"invalid_request_error"}}""",
        )
        assertEquals(AiResult.Failed(AiFailure.MODEL_UNAVAILABLE), client.complete(key, prompt))
        transport.respond(400, """{"error":{"message":"messages is required","type":"invalid_request_error"}}""")
        assertEquals(AiResult.Failed(AiFailure.BAD_REQUEST), client.complete(key, prompt))
    }

    @Test
    fun `no network is offline`() {
        transport.fail(IOException("no route"))
        assertEquals(AiResult.Failed(AiFailure.OFFLINE), client.complete(key, prompt))
    }

    @Test
    fun `unreadable success bodies are malformed`() {
        for (body in listOf("not json", "{}", """{"choices":[]}""", """{"choices":[{"message":{"content":null}}]}""")) {
            transport.respond(200, body)
            assertEquals(AiResult.Failed(AiFailure.MALFORMED), client.complete(key, prompt), body)
        }
    }

    @Test
    fun `the coach needs a key and never calls out without one`() {
        val coach = AiCoach(FakeApiKeyStore(), client, sanitizer)
        assertEquals(AiResult.Failed(AiFailure.NO_KEY), coach.weeklyReview(WeeklyFacts(0, 0, 0, 0, emptyList())))
        assertEquals(0, transport.requests.size)
    }
}
