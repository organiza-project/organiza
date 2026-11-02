package org.organiza.domain.port

import java.time.Duration
import java.time.Instant
import org.organiza.domain.DomainName
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

/**
 * What an intervention is about, and what an override pass lifts (ADR-0016). [app] is the app on
 * screen. A [Site] is keyed by the block-list [entry] it matched, so a pass covers every browser.
 */
public sealed interface BlockTarget {
    public val app: PackageName

    public data class App(override val app: PackageName) : BlockTarget

    public data class Site(override val app: PackageName, val entry: DomainName) : BlockTarget

    /** The short video feed inside [app], not the whole app. */
    public data class ShortForm(override val app: PackageName) : BlockTarget

    /** Organiza's own settings during a strict session. It never takes a pass. */
    public data class Guard(override val app: PackageName) : BlockTarget
}

/** The visible side of a block. Android: an accessibility overlay and the global home action. */
public interface BlockSurface {
    /** Shows the intervention for [target]. Calling it again replaces the target. */
    public fun showOverlay(target: BlockTarget)

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

/** DAEMON is the Linux enforcement service itself (ADR-0013). */
public enum class Capability { ACCESSIBILITY, OVERLAY, VPN, DEVICE_ADMIN, USAGE_STATS, DAEMON }

/** Which enforcement layers the OS currently grants. Losing one is a tamper signal. */
public fun interface EnforcementCapabilities {
    public fun granted(): Set<Capability>
}

/** A recorded [BlockSurface] call, for assertions in tests and the local audit trail. */
public sealed interface BlockAction {
    public data class ShowOverlay(val target: BlockTarget) : BlockAction

    public data object DismissOverlay : BlockAction

    public data object GoHome : BlockAction
}
