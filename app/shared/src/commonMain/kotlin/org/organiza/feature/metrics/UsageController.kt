package org.organiza.feature.metrics

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.organiza.app.OrganizaCore
import org.organiza.presentation.AppMinutes
import org.organiza.presentation.Format
import org.organiza.presentation.UsageReport
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
            ?: build(stores, first, last, today, zone, allDevices && linked).also { cached = Triple(key, now, it) }
        return UsageView(report, span, Format.dayMonth(first), Format.dayMonth(last), allDevices, linked, last < today)
    }

    private fun build(stores: OrganizaCore.Storage, first: LocalDate, last: LocalDate, today: LocalDate, zone: ZoneId, others: Boolean): UsageReport {
        val usage = app.platform.usage
        val categories = app.platform.launchable().associate { it.pkg to it.category }
        val days = (0..ChronoUnit.DAYS.between(first, last)).map { first.plusDays(it) }.associateWith { day ->
            if (!usage.permitted()) emptyList() else {
                val start = day.atStartOfDay(zone).toInstant()
                usage.perApp(start, minOf(day.plusDays(1).atStartOfDay(zone).toInstant(), app.clock.now()).coerceAtLeast(start))
                    .filterKeys { it.value != app.platform.selfPackage }
                    .map { (pkg, time) -> AppMinutes(app.platform.appLabel(pkg), categories[pkg.value] ?: "Other", time.toMinutes()) }
            }
        }
        val remote = if (others) stores.deviceUsage.between(first, last) else emptyMap()
        val merged = days.mapValues { (day, local) -> local + remote[day].orEmpty().map { AppMinutes(it.app, it.category, it.minutes) } }
        return UsageReport.build(merged, first, last, today)
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
    }
}
