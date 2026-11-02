package org.organiza.linux

import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class HeartbeatTest {
    private val file = Files.createTempDirectory("hb").resolve("heartbeat")
    private val now = Instant.parse("2026-11-02T10:00:00Z")

    @Test
    fun `a first start has no gap`() {
        assertEquals(null, Heartbeat(file).gapSinceLastBeat(now))
    }

    @Test
    fun `a beat a moment ago is not a gap`() {
        val heartbeat = Heartbeat(file)
        heartbeat.beat(now)
        assertEquals(null, heartbeat.gapSinceLastBeat(now.plusSeconds(8)))
    }

    @Test
    fun `a long silence is reported with its start`() {
        val heartbeat = Heartbeat(file)
        heartbeat.beat(now)
        assertEquals(now, heartbeat.gapSinceLastBeat(now.plus(Duration.ofMinutes(10))))
    }

    @Test
    fun `a damaged heartbeat file counts as no beat`() {
        Files.writeString(file, "garbage")
        assertEquals(null, Heartbeat(file).gapSinceLastBeat(now))
    }
}
