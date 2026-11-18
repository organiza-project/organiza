package org.organiza.feature.metrics

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.organiza.app.OrganizaCore
import org.organiza.presentation.AppMinutes
import org.organiza.presentation.Format
import org.organiza.presentation.UsageReport
import org.organiza.storage.DeviceAppMinutes
import org.organiza.storage.DeviceUsageStore
import org.organiza.ui.UsageSpan
import org.organiza.ui.UsageView

/** The Metrics report's range and device choice (ADR-0031). This device's usage plus other devices' history. */
class UsageController(private val app: OrganizaCore) {
    @Volatile private var span = UsageSpan.WEEK
    @Volatile private var end: LocalDate? = null
    @Volatile private var from: LocalDate? = null
    @Volatile private var allDevices = true
    @Volatile private var cached: Triple<String, Long, UsageReport>? = null

    fun setSpan(span: UsageSpan) = change { this.span = span; end = null; from = null }

    /** Moves the whole range back or forward by its own length, never past today. */
    fun shift(step: Int) = change {
        val today = today()
        val current = end ?: today
        val moved = when (span) {
            UsageSpan.DAY -> current.plusDays(step.toLong())
            UsageSpan.WEEK -> current.plusWeeks(step.toLong())
            UsageSpan.MONTH -> current.plusMonths(step.toLong())
            UsageSpan.RANGE -> current.plusDays(step * (rangeOf(today).let { ChronoUnit.DAYS.between(it.first, it.second) + 1 }))
        }
        if (span == UsageSpan.RANGE) from = rangeOf(today).first.plusDays(ChronoUnit.DAYS.between(current, minOf(moved, today)))
        end = minOf(moved, today)
    }

    /** Moves one end of a custom range by [days]. */
    fun shiftEdge(start: Boolean, days: Int) = change {
        val today = today()
        val (first, last) = rangeOf(today)
        if (start) from = minOf(first.plusDays(days.toLong()), last) else end = minOf(maxOf(last.plusDays(days.toLong()), first), today)
    }

    fun setAllDevices(on: Boolean) = change { allDevices = on }

    /** Storage thread. */
    fun view(stores: OrganizaCore.Storage, today: LocalDate, zone: ZoneId): UsageView {
        val (first, last) = rangeOf(today)
        val linked = app.devices.linked
        val key = "$first $last ${allDevices && linked}"
        val now = System.nanoTime()
        val report = cached?.takeIf { it.first == key && now - it.second < CACHE_NS }?.third
            ?: build(stores, first, last, today, zone, allDevices && linked).also { keep(stores, today, zone); cached = Triple(key, now, it) }
        return UsageView(report, span, Format.dayMonth(first), Format.dayMonth(last), allDevices, linked, last < today)
    }

    private fun build(stores: OrganizaCore.Storage, first: LocalDate, last: LocalDate, today: LocalDate, zone: ZoneId, others: Boolean): UsageReport {
        val saved = stores.deviceUsage.between(first, last, mine = true)
        val remote = if (others) stores.deviceUsage.between(first, last) else emptyMap()
        val days = (0..ChronoUnit.DAYS.between(first, last)).map { first.plusDays(it) }.associateWith { day ->
            (mine(day, today, zone, saved) + remote[day].orEmpty()).map { AppMinutes(it.app, it.category, it.minutes) }
        }
        return UsageReport.build(days, first, last, today)
    }

    /**
     * Storage thread. This device's minutes per app name on [day]: the kept count for a finished day,
     * since Android forgets app usage after about a week, otherwise what the OS still knows.
     */
    fun mine(day: LocalDate, today: LocalDate, zone: ZoneId, saved: Map<LocalDate, List<DeviceAppMinutes>>): List<DeviceAppMinutes> =
        saved[day]?.takeIf { day < today.minusDays(1) } ?: live(day, zone)

    private fun live(day: LocalDate, zone: ZoneId): List<DeviceAppMinutes> {
        val usage = app.platform.usage
        if (!usage.permitted()) return emptyList()
        val categories = app.platform.launchable().associate { it.pkg to it.category }
        val start = day.atStartOfDay(zone).toInstant()
        val end = minOf(maxOf(app.clock.now(), start), day.plusDays(1).atStartOfDay(zone).toInstant())
        // By name, so two packages one app ships under count as that app.
        return usage.perApp(start, end)
            .filterKeys { it.value != app.platform.selfPackage }
            .entries.groupBy({ app.platform.appLabel(it.key) to (categories[it.key.value] ?: OTHER) }, { it.value.toMinutes() })
            .map { (key, minutes) -> DeviceAppMinutes(key.first, key.second, minutes.sum()) }
            .filter { it.minutes > 0 }
    }

    /**
     * Storage thread, with each new report. Keeps the finished days the OS still knows, before it forgets them.
     * shortcut: a missing day with no usage is asked again each time, at most six reads a minute; mark empty days if that shows up.
     */
    private fun keep(stores: OrganizaCore.Storage, today: LocalDate, zone: ZoneId) {
        if (!app.platform.usage.permitted()) return
        val saved = stores.deviceUsage.between(today.minusDays(KEEP_DAYS), today.minusDays(1), mine = true)
        (1..KEEP_DAYS).map(today::minusDays).filter { it !in saved }.forEach { day ->
            live(day, zone).takeIf { it.isNotEmpty() }?.let { stores.deviceUsage.put(DeviceUsageStore.SELF, day, it) }
        }
    }

    private fun rangeOf(today: LocalDate): Pair<LocalDate, LocalDate> {
        val last = end ?: today
        return when (span) {
            UsageSpan.DAY -> last to last
            UsageSpan.WEEK -> last.minusDays(6) to last
            UsageSpan.MONTH -> last.withDayOfMonth(1) to minOf(last.withDayOfMonth(last.lengthOfMonth()), today)
            UsageSpan.RANGE -> {
                val first = from ?: last.minusDays(6)
                maxOf(first, last.minusDays(UsageReport.MAX_DAYS - 1L)) to last
            }
        }
    }

    private fun today(): LocalDate = app.clock.now().atZone(ZoneId.systemDefault()).toLocalDate()

    private fun change(work: () -> Unit) {
        work()
        cached = null
        app.requestUiRefresh()
    }

    private companion object {
        const val CACHE_NS = 60_000_000_000L
        const val KEEP_DAYS = 6L
        const val OTHER = "Other"
    }
}
