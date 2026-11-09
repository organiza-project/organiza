package org.organiza.desktop

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RemindersTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `a reminder keeps its clock time across a daylight saving change`() {
        // Clocks go forward at 02:00 on 29 March 2026; the day is 23 hours long.
        val evening = ZonedDateTime.of(2026, 3, 28, 22, 0, 0, 0, berlin)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 9, 0, 0, 0, berlin), DesktopPlatform.nextReminder(9 * 60, evening))
        val morning = ZonedDateTime.of(2026, 3, 29, 1, 0, 0, 0, berlin)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 9, 0, 0, 0, berlin), DesktopPlatform.nextReminder(9 * 60, morning))
    }

    @Test
    fun `a reminder whose time has passed today is next due tomorrow`() {
        val now = ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, berlin)
        assertEquals(ZonedDateTime.of(2026, 11, 10, 9, 0, 0, 0, berlin), DesktopPlatform.nextReminder(9 * 60, now))
    }
}
