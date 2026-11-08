package org.organiza.enforcement

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageLimitsTest {
    @Test
    fun `time left counts down to zero and never below`() {
        assertEquals(Duration.ofMinutes(10), UsageLimits.left(Duration.ofMinutes(30), Duration.ofMinutes(20)))
        assertEquals(Duration.ZERO, UsageLimits.left(Duration.ofMinutes(30), Duration.ofMinutes(45)))
    }

    @Test
    fun `an app is over once its time is used up`() {
        assertEquals(false, UsageLimits.over(Duration.ofMinutes(30), Duration.ofMinutes(29)))
        assertEquals(true, UsageLimits.over(Duration.ofMinutes(30), Duration.ofMinutes(30)))
    }

    @Test
    fun `a typed limit is kept as typed, within what the schema stores`() {
        assertEquals(5..720, UsageLimits.MINUTES)
    }
}
