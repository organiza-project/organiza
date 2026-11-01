package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.organiza.domain.port.Capability
import org.organiza.testing.FakeClock
import org.organiza.testing.FakeEnforcementCapabilities

class TamperMonitorTest {
    private val start = Instant.parse("2026-11-01T10:00:00Z")
    private val clock = FakeClock(start)
    private val capabilities = FakeEnforcementCapabilities()
    private val monitor = TamperMonitor(capabilities, clock)

    @Test
    fun `no loss produces no events`() {
        monitor.arm()
        assertEquals(emptyList(), monitor.check())
    }

    @Test
    fun `a capability lost during a session is reported once with its time`() {
        monitor.arm()
        clock.advance(Duration.ofMinutes(1))
        capabilities.revoke(Capability.VPN)
        val expected = TamperEvent(Capability.VPN, start.plusSeconds(60))
        assertEquals(listOf(expected), monitor.check())
        assertEquals(emptyList(), monitor.check())
        assertEquals(listOf(expected), monitor.events)
    }

    @Test
    fun `losing a capability again after regaining it is a new event`() {
        monitor.arm()
        capabilities.revoke(Capability.VPN)
        monitor.check()
        capabilities.grant(Capability.VPN)
        monitor.check()
        capabilities.revoke(Capability.VPN)
        assertEquals(1, monitor.check().size)
        assertEquals(2, monitor.events.size)
    }

    @Test
    fun `capabilities never granted before the session do not count`() {
        capabilities.revoke(Capability.DEVICE_ADMIN)
        monitor.arm()
        assertEquals(emptyList(), monitor.check())
    }

    @Test
    fun `nothing is reported while disarmed`() {
        monitor.arm()
        monitor.disarm()
        capabilities.revoke(Capability.VPN)
        assertEquals(emptyList(), monitor.check())
    }
}
