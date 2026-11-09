package org.organiza.presentation

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun `durations read as hours and minutes`() {
        assertEquals("0 m", Format.duration(Duration.ZERO))
        assertEquals("45 m", Format.duration(Duration.ofMinutes(45)))
        assertEquals("1 h 05 m", Format.duration(Duration.ofMinutes(65)))
        assertEquals("24 h 00 m", Format.duration(Duration.ofHours(24)))
        assertEquals("1 m", Format.duration(Duration.ofSeconds(59)))
    }

    @Test
    fun `negative durations read as zero`() {
        assertEquals("0 m", Format.duration(Duration.ofMinutes(-5)))
    }

    @Test
    fun `usage reads compactly against its limit`() {
        assertEquals("0m", Format.usage(0, null))
        assertEquals("46m", Format.usage(46, null))
        assertEquals("1h 46m / 2h", Format.usage(106, 120))
        assertEquals("2h / 1h 30m", Format.usage(120, 90))
    }

    @Test
    fun `counts agree with their nouns`() {
        assertEquals("1 app", Format.count(1, "app", "apps"))
        assertEquals("0 apps", Format.count(0, "app", "apps"))
        assertEquals("3 sites", Format.count(3, "site", "sites"))
    }
}
