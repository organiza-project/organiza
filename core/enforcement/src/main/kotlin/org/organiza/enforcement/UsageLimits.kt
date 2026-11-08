package org.organiza.enforcement

import java.time.Duration

/** Daily foreground limits per app (ADR-0018). Usage is read from the OS; this decides. */
public object UsageLimits {
    /** The minutes a person can type for a limit, as `usage_limit` stores them. */
    public val MINUTES: IntRange = 5..720

    public fun left(limit: Duration, used: Duration): Duration = maxOf(Duration.ZERO, limit.minus(used))

    public fun over(limit: Duration, used: Duration): Boolean = used >= limit
}
