package org.organiza.testing

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.domain.port.BlockAction
import org.organiza.domain.port.Capability

private fun pkg(raw: String) = (PackageName.parse(raw) as Parsed.Valid).value

class FakesTest {
    private val instagram = pkg("com.instagram.android")
    private val start = Instant.parse("2026-11-01T00:00:00Z")

    @Test
    fun `clock only moves when advanced`() {
        val clock = FakeClock(start)
        assertEquals(start, clock.now())
        clock.advance(Duration.ofMinutes(5))
        assertEquals(start.plusSeconds(300), clock.now())
    }

    @Test
    fun `foreground monitor delivers until the subscription closes`() {
        val monitor = FakeForegroundAppMonitor()
        val seen = mutableListOf<PackageName>()
        val subscription = monitor.subscribe { seen += it }
        monitor.emit(instagram)
        subscription.close()
        monitor.emit(pkg("com.other.app"))
        assertEquals(listOf(instagram), seen)
        assertEquals(0, monitor.subscriberCount)
    }

    @Test
    fun `block surface records actions in order and tracks the overlay`() {
        val surface = RecordingBlockSurface()
        surface.showOverlay(instagram)
        surface.goHome()
        assertEquals(true, surface.overlayVisible)
        surface.dismissOverlay()
        assertEquals(false, surface.overlayVisible)
        assertEquals(
            listOf(BlockAction.ShowOverlay(instagram), BlockAction.GoHome, BlockAction.DismissOverlay),
            surface.actions,
        )
    }

    @Test
    fun `dns upstream records forwarded queries and can simulate failure`() {
        val upstream = FakeDnsUpstream(response = byteArrayOf(1, 2))
        assertContentEquals(byteArrayOf(1, 2), upstream.forward(byteArrayOf(9)))
        upstream.response = null
        assertNull(upstream.forward(byteArrayOf(8)))
        assertEquals(2, upstream.forwarded.size)
        assertContentEquals(byteArrayOf(9), upstream.forwarded[0])
    }

    @Test
    fun `usage history sums only the overlap with the requested window`() {
        val usage = FakeUsageHistory()
        usage.record(instagram, start, Duration.ofMinutes(10))
        usage.record(instagram, start.plusSeconds(3600), Duration.ofMinutes(10))
        usage.record(pkg("com.other.app"), start, Duration.ofHours(1))
        assertEquals(
            Duration.ofMinutes(15),
            usage.foregroundTime(instagram, start.plusSeconds(300), start.plusSeconds(3600 + 600)),
        )
    }

    @Test
    fun `capabilities start fully granted and can be revoked`() {
        val capabilities = FakeEnforcementCapabilities()
        assertEquals(Capability.entries.toSet(), capabilities.granted())
        capabilities.revoke(Capability.VPN)
        assertEquals(Capability.entries.toSet() - Capability.VPN, capabilities.granted())
        capabilities.grant(Capability.VPN)
        assertEquals(Capability.entries.toSet(), capabilities.granted())
    }
}
