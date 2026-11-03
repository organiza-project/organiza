package org.organiza.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZonedDateTime
import kotlin.math.sqrt
import org.organiza.ai.AiFailure
import org.organiza.ai.AiResult
import org.organiza.ai.ApiKeyStore
import org.organiza.ai.GroqClient
import org.organiza.ai.Sanitizer

public object Wav {
    private const val HEADER_BYTES = 44
    private const val BYTES_PER_SAMPLE = 2

    /** Encodes 16-bit mono PCM. */
    public fun encode(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = samples.size * BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(HEADER_BYTES - 8 + dataBytes).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        buffer.putInt(sampleRate).putInt(sampleRate * BYTES_PER_SAMPLE).putShort(BYTES_PER_SAMPLE.toShort()).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataBytes)
        samples.forEach(buffer::putShort)
        return buffer.array()
    }
}

public sealed interface VoiceResult {
    /** Waiting for the user to confirm. Nothing has been applied (ADR-0010). */
    public data class Understood(val command: VoiceCommand, val viaModel: Boolean) : VoiceResult

    public data object NotUnderstood : VoiceResult

    public data object Silent : VoiceResult

    public data object TooLong : VoiceResult

    public data class Failed(val failure: AiFailure) : VoiceResult

    /** Plain text for a dictated schedule, which is sanitized later, before any prompt (ADR-0018). */
    public data class Transcript(val text: String) : VoiceResult
}

/** Turns speech into a command. Only the local grammar can produce one (ADR-0010). */
public class VoicePipeline(
    private val keys: ApiKeyStore,
    private val client: GroqClient,
    private val sanitizer: Sanitizer,
) {
    public fun fromAudio(
        samples: ShortArray,
        sampleRate: Int,
        now: ZonedDateTime,
        knownHabits: List<String> = emptyList(),
    ): VoiceResult {
        if (samples.size > sampleRate.toLong() * MAX_SECONDS) return VoiceResult.TooLong
        if (isSilent(samples)) return VoiceResult.Silent
        val key = keys.load() ?: return VoiceResult.Failed(AiFailure.NO_KEY)
        return when (val text = client.transcribe(key, Wav.encode(samples, sampleRate))) {
            is AiResult.Failed -> VoiceResult.Failed(text.failure)
            is AiResult.Ok -> fromTranscript(text.value, now, knownHabits)
        }
    }

    /** Transcribes a dictation without reading it as a command. */
    public fun transcript(samples: ShortArray, sampleRate: Int): VoiceResult {
        if (samples.size > sampleRate.toLong() * MAX_SECONDS) return VoiceResult.TooLong
        if (isSilent(samples)) return VoiceResult.Silent
        val key = keys.load() ?: return VoiceResult.Failed(AiFailure.NO_KEY)
        return when (val text = client.transcribe(key, Wav.encode(samples, sampleRate))) {
            is AiResult.Failed -> VoiceResult.Failed(text.failure)
            is AiResult.Ok -> VoiceResult.Transcript(text.value.trim())
        }
    }

    public fun fromTranscript(text: String, now: ZonedDateTime, knownHabits: List<String>): VoiceResult {
        CommandParser.parse(text, now)?.let { return VoiceResult.Understood(it, viaModel = false) }
        val key = keys.load() ?: return VoiceResult.Failed(AiFailure.NO_KEY)
        val rewrite = client.rewriteCommand(key, sanitizer.clean(text), knownHabits.map(sanitizer::clean))
        if (rewrite is AiResult.Failed) return VoiceResult.Failed(rewrite.failure)
        val line = (rewrite as AiResult.Ok).value ?: return VoiceResult.NotUnderstood
        // A placeholder means the sanitizer removed the very thing the command would act on.
        if ('[' in line) return VoiceResult.NotUnderstood
        return CommandParser.parse(line, now)?.let { VoiceResult.Understood(it, viaModel = true) }
            ?: VoiceResult.NotUnderstood
    }

    private fun isSilent(samples: ShortArray): Boolean {
        if (samples.isEmpty()) return true
        val power = samples.sumOf { it.toDouble() * it.toDouble() } / samples.size
        return sqrt(power) < SILENCE_RMS
    }

    public companion object {
        public const val MAX_SECONDS: Int = 30
        private const val SILENCE_RMS = 150.0
    }
}
