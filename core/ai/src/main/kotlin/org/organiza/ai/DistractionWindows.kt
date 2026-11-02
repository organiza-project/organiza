package org.organiza.ai

import java.time.Instant
import java.time.ZoneId

/** A run of local hours, [startHour] up to [endHour] exclusive, where focus breaks most often. */
public data class DistractionWindow(val startHour: Int, val endHour: Int, val attempts: Int, val overrides: Int) {
    public val label: String get() = "%02d:00 to %02d:00".format(startHour, endHour)
}

/** The local half of the copilot (ADR-0017): it needs no key and sends nothing anywhere. */
public object DistractionWindows {
    /** An hour counts once it holds this many blocked attempts over the period looked at. */
    public const val MIN_ATTEMPTS: Int = 3
    private const val MAX_WINDOWS = 3
    private const val HOURS = 24

    public fun byHour(instants: List<Instant>, zone: ZoneId): List<Int> {
        val counts = IntArray(HOURS)
        instants.forEach { counts[it.atZone(zone).hour]++ }
        return counts.toList()
    }

    public fun find(attemptsByHour: List<Int>, overridesByHour: List<Int>): List<DistractionWindow> {
        val hot = (0 until HOURS).map { attemptsByHour[it] >= MIN_ATTEMPTS }
        if (hot.none { it }) return emptyList()
        // Start the scan just after a quiet hour, so a run across midnight is not cut in two.
        val first = (0 until HOURS).firstOrNull { !hot[it] }?.let { (it + 1) % HOURS } ?: 0
        val windows = mutableListOf<DistractionWindow>()
        var run = mutableListOf<Int>()
        for (offset in 0..HOURS) {
            val hour = (first + offset) % HOURS
            if (offset < HOURS && hot[hour]) {
                run += hour
            } else if (run.isNotEmpty()) {
                windows += DistractionWindow(
                    run.first(),
                    (run.last() + 1) % HOURS,
                    run.sumOf { attemptsByHour[it] },
                    run.sumOf { overridesByHour[it] },
                )
                run = mutableListOf()
            }
        }
        return windows.sortedByDescending { it.attempts }.take(MAX_WINDOWS)
    }
}
