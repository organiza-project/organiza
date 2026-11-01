package org.organiza.enforcement

import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveSessionTest {
    private val blocking = SessionPolicy(BlockList(setOf(pkg("com.a.b")), emptySet()), strict = true)

    @Test
    fun `starts idle`() {
        assertEquals(SessionPolicy.IDLE, ActiveSession().current)
    }

    @Test
    fun `tells listeners the current policy on subscribe and on change`() {
        val session = ActiveSession()
        val seen = mutableListOf<SessionPolicy>()
        session.subscribe { seen += it }
        session.set(blocking)
        assertEquals(listOf(SessionPolicy.IDLE, blocking), seen)
    }

    @Test
    fun `closed subscriptions stop receiving`() {
        val session = ActiveSession()
        val seen = mutableListOf<SessionPolicy>()
        session.subscribe { seen += it }.close()
        session.set(blocking)
        assertEquals(listOf(SessionPolicy.IDLE), seen)
    }
}
