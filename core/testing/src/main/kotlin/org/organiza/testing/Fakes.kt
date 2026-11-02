package org.organiza.testing

import java.time.Duration
import java.time.Instant
import org.organiza.domain.PackageName
import org.organiza.domain.port.BlockAction
import org.organiza.domain.port.BlockSurface
import org.organiza.domain.port.BlockTarget
import org.organiza.domain.port.Capability
import org.organiza.domain.port.Clock
import org.organiza.domain.port.DnsUpstream
import org.organiza.domain.port.EnforcementCapabilities
import org.organiza.domain.port.ForegroundAppMonitor
import org.organiza.domain.port.UsageHistory

public class FakeClock(private var current: Instant) : Clock {
    override fun now(): Instant = current

    public fun advance(by: Duration) {
        current = current.plus(by)
    }
}

public class FakeForegroundAppMonitor : ForegroundAppMonitor {
    private val listeners = mutableListOf<(PackageName) -> Unit>()

    public val subscriberCount: Int get() = listeners.size

    override fun subscribe(listener: (PackageName) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    public fun emit(app: PackageName) {
        listeners.toList().forEach { it(app) }
    }
}

public class RecordingBlockSurface : BlockSurface {
    private val recorded = mutableListOf<BlockAction>()

    public val actions: List<BlockAction> get() = recorded.toList()

    public var overlayVisible: Boolean = false
        private set

    override fun showOverlay(target: BlockTarget) {
        overlayVisible = true
        recorded += BlockAction.ShowOverlay(target)
    }

    override fun dismissOverlay() {
        overlayVisible = false
        recorded += BlockAction.DismissOverlay
    }

    override fun goHome() {
        recorded += BlockAction.GoHome
    }
}

public class FakeDnsUpstream(public var response: ByteArray? = null) : DnsUpstream {
    private val queries = mutableListOf<ByteArray>()

    public val forwarded: List<ByteArray> get() = queries.toList()

    override fun forward(query: ByteArray): ByteArray? {
        queries += query
        return response
    }
}

public class FakeUsageHistory : UsageHistory {
    private data class Interval(val app: PackageName, val start: Instant, val end: Instant)

    private val intervals = mutableListOf<Interval>()

    public fun record(app: PackageName, start: Instant, duration: Duration) {
        intervals += Interval(app, start, start.plus(duration))
    }

    override fun foregroundTime(app: PackageName, from: Instant, to: Instant): Duration =
        intervals.filter { it.app == app }.fold(Duration.ZERO) { total, interval ->
            val start = maxOf(interval.start, from)
            val end = minOf(interval.end, to)
            if (start < end) total.plus(Duration.between(start, end)) else total
        }
}

public class FakeEnforcementCapabilities(
    granted: Set<Capability> = Capability.entries.toSet(),
) : EnforcementCapabilities {
    private val current = granted.toMutableSet()

    override fun granted(): Set<Capability> = current.toSet()

    public fun grant(capability: Capability) {
        current += capability
    }

    public fun revoke(capability: Capability) {
        current -= capability
    }
}
