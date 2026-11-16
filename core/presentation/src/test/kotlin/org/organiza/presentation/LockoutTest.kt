package org.organiza.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LockoutTest {
    private val start = Lockout.ESSAY.take(10)

    @Test
    fun `only the next right letter or a deletion is taken`() {
        assertEquals(start + Lockout.ESSAY[10], Lockout.accept(start, start + Lockout.ESSAY[10]))
        assertEquals(start, Lockout.accept(start, start + "#"))
        assertEquals(start.dropLast(1), Lockout.accept(start, start.dropLast(1)))
    }

    @Test
    fun `pasting is refused, even the right text`() {
        assertEquals("", Lockout.accept("", Lockout.ESSAY))
        assertEquals(start, Lockout.accept(start, Lockout.ESSAY.take(20)))
    }

    @Test
    fun `only the whole essay unlocks`() {
        assertFalse(Lockout.done(Lockout.ESSAY.dropLast(1)))
        assertTrue(Lockout.done(Lockout.ESSAY))
        assertTrue(Lockout.ESSAY.split(" ").size >= 60)
    }
}
