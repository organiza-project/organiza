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
    fun `the slider moves in steps and stays in range`() {
        assertEquals(listOf(5L, 15L, 30L, 45L, 60L), UsageLimits.STEPS.take(5).map { it.toMinutes() })
        assertEquals(Duration.ofMinutes(180), UsageLimits.STEPS.last())
        assertEquals(Duration.ofMinutes(30), UsageLimits.nearest(Duration.ofMinutes(33)))
        assertEquals(Duration.ofMinutes(5), UsageLimits.nearest(Duration.ZERO))
        assertEquals(Duration.ofMinutes(180), UsageLimits.nearest(Duration.ofHours(9)))
    }
}
