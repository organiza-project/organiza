package org.organiza.ai

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.organiza.domain.InvalidReason
import org.organiza.domain.Parsed

/** A user's own API key. Deliberately not a data class, so it never prints its value. */
public class ApiKey private constructor(public val value: String) {
    override fun toString(): String = "ApiKey(redacted)"

    override fun equals(other: Any?): Boolean = other is ApiKey && other.value == value

    override fun hashCode(): Int = value.hashCode()

    public companion object {
        private const val MAX_LENGTH = 512

        public fun parse(raw: String): Parsed<ApiKey> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> Parsed.Invalid(InvalidReason.BLANK)
                trimmed.length > MAX_LENGTH -> Parsed.Invalid(InvalidReason.TOO_LONG)
                trimmed.any { it.isWhitespace() || it.isISOControl() } -> Parsed.Invalid(InvalidReason.MALFORMED)
                else -> Parsed.Valid(ApiKey(trimmed))
            }
        }
    }
}

/** Key storage. Android: sealed by a Keystore key (ADR-0009). */
public interface ApiKeyStore {
    public fun save(key: ApiKey)

    public fun load(): ApiKey?

    public fun clear()
}

public class HttpReply(public val status: Int, public val body: String)

public fun interface HttpTransport {
    /** Throws [IOException] when the server cannot be reached. */
    public fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpReply
}

public fun HttpTransport.post(url: String, headers: Map<String, String>, body: String): HttpReply =
    post(url, headers, body.toByteArray())

public class UrlConnectionTransport : HttpTransport {
    override fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpReply {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // A redirect would carry the Authorization header to a host nobody chose.
            connection.instanceFollowRedirects = false
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return HttpReply(status, stream?.use { it.readBytes().decodeToString() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}

public class GroqEndpoint private constructor(baseUrl: String) {
    internal val chatUrl = "$baseUrl/openai/v1/chat/completions"
    internal val transcriptionUrl = "$baseUrl/openai/v1/audio/transcriptions"

    public companion object {
        public val PRODUCTION: GroqEndpoint = GroqEndpoint("https://api.groq.com")

        /** A local stand-in server. Only tests and debug builds may use this. */
        public fun forTesting(baseUrl: String): GroqEndpoint = GroqEndpoint(baseUrl)
    }
}

public enum class AiFailure {
    NO_KEY,
    INVALID_KEY,
    RATE_LIMITED,
    MODEL_UNAVAILABLE,
    BAD_REQUEST,
    SERVER,
    OFFLINE,
    MALFORMED,
}

public sealed interface AiResult<out T> {
    public data class Ok<T>(val value: T) : AiResult<T>

    public data class Failed(val failure: AiFailure) : AiResult<Nothing>
}

/** Sends a [Prompt] to Groq. There is no way to send anything else (ADR-0009). */
public class GroqClient(
    private val transport: HttpTransport,
    private val endpoint: GroqEndpoint,
    private val model: String = DEFAULT_MODEL,
) {
    public fun complete(key: ApiKey, prompt: Prompt): AiResult<String> {
        val headers = mapOf("Authorization" to "Bearer ${key.value}", "Content-Type" to "application/json")
        val reply = try {
            transport.post(endpoint.chatUrl, headers, requestBody(prompt).toByteArray())
        } catch (_: IOException) {
            return AiResult.Failed(AiFailure.OFFLINE)
        }
        return if (reply.status == OK) readContent(reply.body) else AiResult.Failed(failureFor(reply))
    }

    /** Sends a WAV recording to Whisper. Audio cannot be sanitized first (ADR-0010). */
    public fun transcribe(key: ApiKey, wav: ByteArray): AiResult<String> {
        val boundary = "organiza-" + UUID.randomUUID()
        val headers = mapOf(
            "Authorization" to "Bearer ${key.value}",
            "Content-Type" to "multipart/form-data; boundary=$boundary",
        )
        val reply = try {
            transport.post(endpoint.transcriptionUrl, headers, multipart(boundary, wav))
        } catch (_: IOException) {
            return AiResult.Failed(AiFailure.OFFLINE)
        }
        if (reply.status != OK) return AiResult.Failed(failureFor(reply))
        val text = runCatching {
            ((Json.parseToJsonElement(reply.body) as JsonObject)["text"] as JsonPrimitive).takeIf { it.isString }?.content
        }.getOrNull()
        return text?.let { AiResult.Ok(it) } ?: AiResult.Failed(AiFailure.MALFORMED)
    }

    /** Asks the model to restate [transcript] in the command grammar. Null means it could not. */
    public fun rewriteCommand(key: ApiKey, transcript: SanitizedText, habits: List<SanitizedText>): AiResult<String?> =
        when (val reply = complete(key, Prompts.voiceCommandRewrite(transcript, habits))) {
            is AiResult.Failed -> reply
            is AiResult.Ok -> AiResult.Ok(
                runCatching {
                    ((Json.parseToJsonElement(reply.value) as JsonObject)["command"] as JsonPrimitive)
                        .takeIf { it.isString }?.content
                }.getOrNull(),
            )
        }

    private fun multipart(boundary: String, wav: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        fun field(name: String, value: String) {
            bytes.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        }
        field("model", TRANSCRIPTION_MODEL)
        field("response_format", "json")
        field("temperature", "0")
        field("prompt", TRANSCRIPTION_HINT)
        bytes.write(
            ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n" +
                "Content-Type: audio/wav\r\n\r\n").toByteArray(),
        )
        bytes.write(wav)
        bytes.write("\r\n--$boundary--\r\n".toByteArray())
        return bytes.toByteArray()
    }

    private fun requestBody(prompt: Prompt): String = buildJsonObject {
        put("model", model)
        put("temperature", TEMPERATURE)
        put("max_completion_tokens", prompt.maxTokens)
        put("response_format", buildJsonObject { put("type", "json_object") })
        put(
            "messages",
            buildJsonArray {
                add(message("system", prompt.system))
                add(message("user", prompt.user))
            },
        )
    }.toString()

    private fun message(role: String, content: String) = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    private fun readContent(body: String): AiResult<String> {
        val content = runCatching {
            val choices = Json.parseToJsonElement(body) as JsonObject
            val first = (choices["choices"] as JsonArray).first() as JsonObject
            val message = first["message"] as JsonObject
            (message["content"] as JsonPrimitive).takeIf { it.isString }?.content
        }.getOrNull()
        return content?.let { AiResult.Ok(it) } ?: AiResult.Failed(AiFailure.MALFORMED)
    }

    private fun failureFor(reply: HttpReply): AiFailure = when (reply.status) {
        401, 403 -> AiFailure.INVALID_KEY
        404 -> AiFailure.MODEL_UNAVAILABLE
        429 -> AiFailure.RATE_LIMITED
        400 -> if (MODEL_GONE.containsMatchIn(reply.body)) AiFailure.MODEL_UNAVAILABLE else AiFailure.BAD_REQUEST
        CAPACITY -> AiFailure.SERVER
        in 400..499 -> AiFailure.BAD_REQUEST
        else -> AiFailure.SERVER
    }

    public companion object {
        /** Hosted model IDs retire often. Check Groq's deprecations page before changing this. */
        public const val DEFAULT_MODEL: String = "openai/gpt-oss-20b"

        /** The copilot chat's larger model (ADR-0020). llama-3.3-70b-versatile was retired from Groq by 2026-11. */
        public const val CHAT_MODEL: String = "openai/gpt-oss-120b"
        private const val TRANSCRIPTION_MODEL = "whisper-large-v3-turbo"

        // A fixed hint with no user data, which steers Whisper towards command vocabulary.
        private const val TRANSCRIPTION_HINT = "Add a habit. Mark habit as done. Remove the habit. " +
            "Schedule focus tomorrow at 9 for 1 hour. Cancel the session. Log 30 minutes of reading."
        private const val OK = 200
        private const val CAPACITY = 498
        private const val TEMPERATURE = 0.3
        private val MODEL_GONE = Regex("""model.*(decommission|not found|does not exist)""", RegexOption.IGNORE_CASE)
    }
}
