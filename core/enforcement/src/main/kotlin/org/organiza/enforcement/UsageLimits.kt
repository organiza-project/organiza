package org.organiza.enforcement

import java.time.Duration

/** Daily foreground limits per app (ADR-0018). Usage is read from the OS; this decides. */
public object UsageLimits {
    /** The choices the limit slider offers. */
    public val STEPS: List<Duration> = listOf(5L, 15L, 30L, 45L, 60L, 90L, 120L, 180L).map(Duration::ofMinutes)

    public fun left(limit: Duration, used: Duration): Duration = maxOf(Duration.ZERO, limit.minus(used))

    public fun over(limit: Duration, used: Duration): Boolean = used >= limit

    /** The step closest to [wanted], for a slider position or a stored value. */
    public fun nearest(wanted: Duration): Duration = STEPS.minBy { (it.toMinutes() - wanted.toMinutes()).let(Math::abs) }
}
