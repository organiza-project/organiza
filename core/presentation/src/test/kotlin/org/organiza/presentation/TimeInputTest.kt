package org.organiza.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeInputTest {
    @Test
    fun `typed minutes are read inside their range`() {
        assertEquals(45, TimeInput.minutes("45", 1..480))
        assertEquals(90, TimeInput.minutes(" 090 ", 1..480))
        assertNull(TimeInput.minutes("", 1..480))
        assertNull(TimeInput.minutes("0", 1..480))
        assertNull(TimeInput.minutes("481", 1..480))
        assertNull(TimeInput.minutes("4 5", 1..480))
    }

    @Test
    fun `a 24 hour clock reads hours and minutes, an empty minute box meaning on the hour`() {
        assertEquals(8 * 60 + 30, TimeInput.clock("08", "30", pm = null))
        assertEquals(8 * 60, TimeInput.clock("8", "", pm = null))
        assertEquals(23 * 60 + 59, TimeInput.clock("23", "59", pm = null))
        assertNull(TimeInput.clock("24", "00", pm = null))
        assertNull(TimeInput.clock("", "30", pm = null))
        assertNull(TimeInput.clock("9", "60", pm = null))
    }

    @Test
    fun `a 12 hour clock takes hours 1 to 12 and maps 12 AM to midnight`() {
        assertEquals(8 * 60 + 30, TimeInput.clock("8", "30", pm = false))
        assertEquals(20 * 60 + 30, TimeInput.clock("8", "30", pm = true))
        assertEquals(0, TimeInput.clock("12", "00", pm = false))
        assertEquals(12 * 60, TimeInput.clock("12", "00", pm = true))
        assertNull(TimeInput.clock("13", "00", pm = true))
        assertNull(TimeInput.clock("0", "15", pm = false))
    }

    @Test
    fun `times show the way the phone tells time`() {
        assertEquals("08:30", TimeInput.label(8 * 60 + 30, twelveHour = false))
        assertEquals("8:30 AM", TimeInput.label(8 * 60 + 30, twelveHour = true))
        assertEquals("12:05 AM", TimeInput.label(5, twelveHour = true))
        assertEquals("12:00 PM", TimeInput.label(12 * 60, twelveHour = true))
        assertEquals("11:45 PM", TimeInput.label(23 * 60 + 45, twelveHour = true))
    }

    @Test
    fun `lengths read in hours and minutes`() {
        assertEquals("45 minutes", TimeInput.length(45))
        assertEquals("1 hour", TimeInput.length(60))
        assertEquals("1 hour 30 minutes", TimeInput.length(90))
        assertEquals("2 hours 15 minutes", TimeInput.length(135))
        assertEquals("1 minute", TimeInput.length(1))
    }

    @Test
    fun `the first keystroke in a box replaces it, wherever the cursor was`() {
        assertEquals("0", TimeInput.firstKeystroke(old = "8", new = "08"))
        assertEquals("0", TimeInput.firstKeystroke(old = "8", new = "80"))
        assertEquals("4", TimeInput.firstKeystroke(old = "00", new = "040"))
        assertEquals("", TimeInput.firstKeystroke(old = "45", new = "4"))
        assertEquals("12", TimeInput.firstKeystroke(old = "", new = "12"))
        // A paste or a whole replacement is taken as it is.
        assertEquals("45", TimeInput.firstKeystroke(old = "00", new = "45"))
        assertEquals("7", TimeInput.firstKeystroke(old = "20", new = "7"))
        assertEquals("25", TimeInput.firstKeystroke(old = "45", new = "25"))
    }
}
