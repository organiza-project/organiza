package org.organiza.android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.organiza.voice.VoicePipeline

/** Records into memory only, while the talk button is held (ADR-0010). */
class VoiceRecorder {
    private var record: AudioRecord? = null
    private var reader: Thread? = null
    private val samples = ShortArray(SAMPLE_RATE * VoicePipeline.MAX_SECONDS)
    @Volatile private var count = 0

    /** The caller holds the microphone permission. Returns false if recording could not start. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return false
        val audio = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minimum * 2,
        )
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            return false
        }
        count = 0
        audio.startRecording()
        record = audio
        reader = Thread({
            // Stops by itself at the limit, even if the button is never released.
            while (!Thread.currentThread().isInterrupted && count < samples.size) {
                val read = audio.read(samples, count, minOf(READ_CHUNK, samples.size - count))
                if (read <= 0) break
                count += read
            }
        }, "voice-recorder").apply { start() }
        return true
    }

    fun stop(): ShortArray {
        reader?.interrupt()
        record?.stop()
        reader?.join(JOIN_MS)
        record?.release()
        record = null
        reader = null
        val recorded = samples.copyOf(count)
        samples.fill(0)
        return recorded
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val READ_CHUNK = 1600
        private const val JOIN_MS = 500L
    }
}
