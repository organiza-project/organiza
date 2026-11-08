package org.organiza.desktop

import java.time.Duration
import java.time.Instant
import org.organiza.domain.PackageName
import org.organiza.domain.port.UsageHistory
import org.organiza.enforcement.ForegroundTime
import org.organiza.enforcement.UsageEvent
import org.organiza.storage.FocusLog

/** Foreground time from the desktop's own focus log (ADR-0024), counted the way Android's is. */
class LoggedUsage(private val log: FocusLog) : UsageHistory {
    override fun foregroundTime(app: PackageName, from: Instant, to: Instant): Duration = perApp(from, to)[app] ?: Duration.ZERO

    // Each change ends whatever was in front, then starts the new app, if any.
    fun perApp(from: Instant, to: Instant): Map<PackageName, Duration> = ForegroundTime.perApp(
        log.changes(from, to).flatMap { change ->
            listOfNotNull(UsageEvent.ScreenOff(change.at), change.app?.let { UsageEvent.Resumed(it, "", change.at) })
        },
        from,
        to,
    )
}
