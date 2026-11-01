package org.organiza.enforcement

import java.time.Instant
import org.organiza.domain.port.Capability
import org.organiza.domain.port.Clock
import org.organiza.domain.port.EnforcementCapabilities

public data class TamperEvent(val capability: Capability, val at: Instant)

/** Records enforcement capabilities lost while a session is armed (ADR-0005). */
public class TamperMonitor(
    private val capabilities: EnforcementCapabilities,
    private val clock: Clock,
) {
    private val recorded = mutableListOf<TamperEvent>()
    private var armed = false
    private var lastGranted = emptySet<Capability>()

    public val events: List<TamperEvent> get() = recorded.toList()

    /** Starts watching. Pass a stored [baseline] to resume after the process was killed. */
    public fun arm(baseline: Set<Capability> = capabilities.granted()) {
        armed = true
        lastGranted = baseline
    }

    public fun disarm() {
        armed = false
    }

    /** Returns the capabilities newly lost since the previous check. */
    public fun check(): List<TamperEvent> {
        if (!armed) return emptyList()
        val granted = capabilities.granted()
        val now = clock.now()
        val lost = (lastGranted - granted).map { TamperEvent(it, now) }
        lastGranted = granted
        recorded += lost
        return lost
    }
}
