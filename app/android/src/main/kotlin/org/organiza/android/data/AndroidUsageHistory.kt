package org.organiza.android.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Duration
import java.time.Instant
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed
import org.organiza.app.AppUsage
import org.organiza.enforcement.ForegroundTime
import org.organiza.enforcement.UsageEvent

/** Reads foreground time on this device. The result stays local (ADR-0011, ADR-0019). */
class AndroidUsageHistory(private val context: Context) : AppUsage {
    override fun permitted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun foregroundTime(app: PackageName, from: Instant, to: Instant): Duration =
        perApp(from, to)[app] ?: Duration.ZERO

    /** Every app's foreground time in the window, the sum of which is screen time. */
    override fun perApp(from: Instant, to: Instant): Map<PackageName, Duration> {
        val end = minOf(to, Instant.now())
        return ForegroundTime.perApp(events(from.minus(LOOKBACK), end), from, end)
    }

    // Reads from before the window so an app already open at its start is counted from there.
    private fun events(from: Instant, to: Instant): List<UsageEvent> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val events = manager.queryEvents(from.toEpochMilli(), to.toEpochMilli())
        val event = UsageEvents.Event()
        val out = ArrayList<UsageEvent>()
        while (events.getNextEvent(event)) {
            val at = Instant.ofEpochMilli(event.timeStamp)
            val app = (PackageName.parse(event.packageName ?: "") as? Parsed.Valid)?.value
            val activity = event.className ?: ""
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> app?.let { out += UsageEvent.Resumed(it, activity, at) }
                UsageEvents.Event.ACTIVITY_PAUSED -> app?.let { out += UsageEvent.Paused(it, activity, at) }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> out += UsageEvent.ScreenOff(at)
            }
        }
        return out
    }

    private companion object {
        val LOOKBACK: Duration = Duration.ofHours(3)
    }
}
