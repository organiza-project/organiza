package org.organiza.enforcement

import java.time.Duration
import java.time.Instant
import org.organiza.domain.PackageName

/** The usage events foreground time is built from. Android: UsageStatsManager.queryEvents. */
public sealed interface UsageEvent {
    public val at: Instant

    public data class Resumed(val app: PackageName, val activity: String, override val at: Instant) : UsageEvent

    public data class Paused(val app: PackageName, val activity: String, override val at: Instant) : UsageEvent

    /** Comes after the pause, so it never ends a stretch. */
    public data class Stopped(val app: PackageName, val activity: String, override val at: Instant) : UsageEvent

    /** The screen went off or the device shut down: nothing is in front any more. */
    public data class ScreenOff(override val at: Instant) : UsageEvent
}

/**
 * Foreground time per app (ADR-0019). An app is in front while any of its activities is resumed, so
 * moving between its own screens is one stretch. Time is clipped to the window; an app still in
 * front at its end counts up to then.
 */
public object ForegroundTime {
    public fun perApp(events: List<UsageEvent>, from: Instant, to: Instant): Map<PackageName, Duration> {
        val totals = mutableMapOf<PackageName, Duration>()
        val resumed = mutableMapOf<PackageName, MutableSet<String>>()
        val since = mutableMapOf<PackageName, Instant>()

        fun close(app: PackageName, end: Instant) {
            val start = since.remove(app) ?: return
            resumed.remove(app)
            val clipped = Duration.between(maxOf(start, from), minOf(end, to))
            if (!clipped.isNegative && !clipped.isZero) totals[app] = totals.getOrDefault(app, Duration.ZERO) + clipped
        }

        for (event in events.sortedBy { it.at }) {
            when (event) {
                is UsageEvent.Resumed -> {
                    resumed.getOrPut(event.app) { mutableSetOf() }.add(event.activity)
                    since.putIfAbsent(event.app, event.at)
                }
                is UsageEvent.Paused -> {
                    val open = resumed[event.app] ?: continue
                    open.remove(event.activity)
                    if (open.isEmpty()) close(event.app, event.at)
                }
                is UsageEvent.Stopped -> Unit
                is UsageEvent.ScreenOff -> since.keys.toList().forEach { close(it, event.at) }
            }
        }
        since.keys.toList().forEach { close(it, to) }
        return totals
    }
}
