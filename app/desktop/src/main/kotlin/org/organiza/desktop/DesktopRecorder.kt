package org.organiza.desktop

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.abs
import org.organiza.app.Recorder
import org.organiza.voice.VoicePipeline

/**
 * Records into memory only, while a talk button is held (ADR-0010). Java Sound reaches PipeWire and
 * PulseAudio through their ALSA layer, so one path covers every Linux sound server.
 */
class DesktopRecorder private constructor(private val line: TargetDataLine) : Recorder {
    override val sampleRate: Int = SAMPLE_RATE
    private val samples = ShortArray(SAMPLE_RATE * VoicePipeline.MAX_SECONDS)
    @Volatile private var count = 0

    @Volatile override var level: Float = 0f
        private set

    private val reader = thread(isDaemon = true, name = "organiza-microphone") {
        val buffer = ByteArray(CHUNK_BYTES)
        while (line.isOpen && count < samples.size) {
            val read = line.read(buffer, 0, buffer.size)
            if (read <= 0) break
            var peak = 0
            for (i in 0 until read / 2) {
                if (count >= samples.size) break
                // Signed 16-bit little endian, as opened below.
                val sample = ((buffer[2 * i + 1].toInt() shl 8) or (buffer[2 * i].toInt() and 0xff)).toShort()
                samples[count++] = sample
                peak = maxOf(peak, abs(sample.toInt()))
            }
            level = peak / 32768f
        }
    }

    override fun stop(): ShortArray {
        line.stop()
        line.close()
        reader.join(JOIN_MS)
        return samples.copyOf(count)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK_BYTES = 3_200
        private const val JOIN_MS = 500L

        /** The default microphone, or null when there is none or it is busy. */
        fun start(): DesktopRecorder? = try {
            val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
            val line = AudioSystem.getTargetDataLine(format)
            line.open(format)
            line.start()
            DesktopRecorder(line)
        } catch (_: LineUnavailableException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
