package org.organiza.desktop

import java.time.Duration
import java.time.Instant
import org.organiza.app.AppUsage
import org.organiza.domain.PackageName
import org.organiza.enforcement.ForegroundTime
import org.organiza.enforcement.UsageEvent
import org.organiza.storage.FocusLog

/**
 * Foreground time from the desktop's own focus log (ADR-0024), counted the way Android's is. The log
 * is there once storage opens, and the time counts once a focus source is reporting.
 */
class LoggedUsage(private val log: () -> FocusLog?, private val tracking: () -> Boolean) : AppUsage {
    override fun permitted(): Boolean = tracking()

    // Each change ends whatever was in front, then starts the new app, if any.
    override fun perApp(from: Instant, to: Instant): Map<PackageName, Duration> {
        val changes = log()?.changes(from, to) ?: return emptyMap()
        return ForegroundTime.perApp(
            changes.flatMap { change -> listOfNotNull(UsageEvent.ScreenOff(change.at), change.app?.let { UsageEvent.Resumed(it, "", change.at) }) },
            from,
            to,
        )
    }
}
