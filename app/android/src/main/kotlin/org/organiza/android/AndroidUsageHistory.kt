package org.organiza.android

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Duration
import java.time.Instant
import org.organiza.domain.PackageName
import org.organiza.domain.port.UsageHistory

/** Reads foreground time on this device. The result stays local (ADR-0011). */
class AndroidUsageHistory(private val context: Context) : UsageHistory {
    fun permitted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun foregroundTime(app: PackageName, from: Instant, to: Instant): Duration {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val events = manager.queryEvents(from.toEpochMilli(), to.toEpochMilli())
        val event = UsageEvents.Event()
        var total = 0L
        var resumedAt: Long? = null
        while (events.getNextEvent(event)) {
            if (event.packageName != app.value) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (resumedAt == null) resumedAt = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    total += event.timeStamp - (resumedAt ?: from.toEpochMilli())
                    resumedAt = null
                }
            }
        }
        // Still open at the end of the window, so it counts up to then.
        resumedAt?.let { total += minOf(to.toEpochMilli(), System.currentTimeMillis()) - it }
        return Duration.ofMillis(total)
    }
}
