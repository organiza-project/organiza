package org.organiza.linux

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/** A timestamp written while the daemon runs. A long silence means it was stopped (ADR-0013). */
class Heartbeat(private val file: Path) {
    fun beat(now: Instant) {
        Files.writeString(file, now.toEpochMilli().toString())
    }

    /** When the silence began, or null when there was no real gap. */
    fun gapSinceLastBeat(now: Instant): Instant? {
        val last = try {
            Files.readString(file).trim().toLongOrNull()?.let(Instant::ofEpochMilli)
        } catch (_: IOException) {
            null
        } ?: return null
        return last.takeIf { Duration.between(it, now) > THRESHOLD }
    }

    companion object {
        val INTERVAL: Duration = Duration.ofSeconds(5)
        private val THRESHOLD: Duration = Duration.ofSeconds(30)
    }
}
