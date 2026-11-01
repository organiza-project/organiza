package org.organiza.domain.port

import java.time.Duration
import java.time.Instant
import org.organiza.domain.PackageName

/** The only source of time for domain logic. */
public fun interface Clock {
    public fun now(): Instant
}

/** Foreground app changes. Android: AccessibilityService window-state events. */
public fun interface ForegroundAppMonitor {
    /** Delivers changes until the returned handle is closed. */
    public fun subscribe(listener: (PackageName) -> Unit): AutoCloseable
}

/** The visible side of a block. Android: accessibility overlay and global home action. */
public interface BlockSurface {
    public fun showOverlay(blocked: PackageName)

    public fun dismissOverlay()

    public fun goHome()
}

/** Forwards an allowed raw DNS query to the device's resolver (ADR-0005). */
public fun interface DnsUpstream {
    /** Returns the raw response, or null when the resolver cannot be reached. */
    public fun forward(query: ByteArray): ByteArray?
}

/** Historical foreground time. Android: UsageStatsManager. */
public fun interface UsageHistory {
    public fun foregroundTime(app: PackageName, from: Instant, to: Instant): Duration
}

public enum class Capability { ACCESSIBILITY, OVERLAY, VPN, DEVICE_ADMIN, USAGE_STATS }

/** Which enforcement layers the OS currently grants. Losing one is a tamper signal. */
public fun interface EnforcementCapabilities {
    public fun granted(): Set<Capability>
}

/** A recorded [BlockSurface] call, for assertions in tests and the local audit trail. */
public sealed interface BlockAction {
    public data class ShowOverlay(val blocked: PackageName) : BlockAction

    public data object DismissOverlay : BlockAction

    public data object GoHome : BlockAction
}
