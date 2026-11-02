package org.organiza.presentation

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.organiza.domain.port.Capability
import org.organiza.enforcement.SessionPolicy
import org.organiza.storage.SessionState
import org.organiza.storage.StoredSession

public data class SessionView(val session: StoredSession, val tag: String?, val strict: Boolean)

public enum class FocusState { OFF, ACTIVE, STRICT }

public enum class BarKind { PAST, NOW, LATER }

/** [start] and [end] are fractions of the strip's width. */
public data class StripBar(val id: String, val start: Double, val end: Double, val kind: BarKind, val label: String?)

public data class StripTick(val label: String, val at: Double)

/** The signature element: today from 06:00 (or the first session) to midnight (ADR-0012). */
public data class DayStrip(val from: LocalTime, val bars: List<StripBar>, val now: Double, val ticks: List<StripTick>)

/** In order of importance: blocking first, then its back-ups. */
public enum class Protection(public val title: String, public val detail: String, public val action: String) {
    ACCESSIBILITY("App blocking", "Watches which app is open and sends blocked ones home", "Turn on"),
    VPN_CONSENT("Website blocking", "A local DNS filter. No traffic leaves through it", "Allow"),
    DEVICE_ADMIN("Uninstall protection", "Stops Organiza being removed in one step", "Enable"),
    USAGE("Usage access", "Lets habits judge screen time on this phone", "Allow"),
}

public data class ProtectionItem(val kind: Protection, val on: Boolean)

public data class FocusModel(
    val state: FocusState,
    val headline: String,
    val subline: String,
    val strip: DayStrip,
    val protection: List<ProtectionItem>,
    val protectionSummary: String,
    val tamperLine: String,
) {
    public companion object {
        private val DAY_START = LocalTime.of(6, 0)
        private const val DAY_MINUTES = 24 * 60.0

        public fun build(
            now: Instant,
            zone: ZoneId,
            policy: SessionPolicy,
            sessions: List<SessionView>,
            granted: Set<Capability>,
            tamperThisWeek: Int,
        ): FocusModel {
            val active = sessions.filter { it.session.state == SessionState.ACTIVE }
            val state = when {
                policy == SessionPolicy.IDLE && active.isEmpty() -> FocusState.OFF
                policy.strict -> FocusState.STRICT
                else -> FocusState.ACTIVE
            }
            val until = active.maxOfOrNull { it.session.end }
            val headline = if (state == FocusState.OFF || until == null) "Off" else Format.duration(Duration.between(now, until))
            val protection = Protection.entries.map { ProtectionItem(it, isOn(it, granted)) }
            val off = protection.count { !it.on }
            return FocusModel(
                state = state,
                headline = headline,
                subline = subline(state, policy, until, now, zone, sessions),
                strip = strip(now, zone, sessions),
                protection = protection,
                protectionSummary = if (off == 0) "All protections are on" else Format.count(off, "protection is", "protections are") + " off",
                tamperLine = if (tamperThisWeek == 0) "No tamper events this week" else
                    Format.count(tamperThisWeek, "tamper event", "tamper events") + " this week",
            )
        }

        private fun isOn(kind: Protection, granted: Set<Capability>) = when (kind) {
            Protection.ACCESSIBILITY -> Capability.ACCESSIBILITY in granted
            Protection.VPN_CONSENT -> Capability.VPN in granted
            Protection.DEVICE_ADMIN -> Capability.DEVICE_ADMIN in granted
            Protection.USAGE -> Capability.USAGE_STATS in granted
        }

        private fun subline(
            state: FocusState,
            policy: SessionPolicy,
            until: Instant?,
            now: Instant,
            zone: ZoneId,
            sessions: List<SessionView>,
        ): String {
            if (state == FocusState.OFF || until == null) {
                val today = now.atZone(zone).toLocalDate()
                val next = sessions
                    .filter { it.session.state == SessionState.SCHEDULED && it.session.start.isAfter(now) }
                    .filter { it.session.start.atZone(zone).toLocalDate() == today }
                    .minByOrNull { it.session.start }
                    ?: return "Nothing is blocked and nothing is scheduled today"
                return "Nothing is blocked. Next: ${next.tag ?: "a session"} at ${clock(next.session.start, zone)}"
            }
            val apps = policy.blockList.packages.size
            val sites = policy.blockList.domains.size
            val what = listOfNotNull(
                Format.count(apps, "app", "apps").takeIf { apps > 0 },
                Format.count(sites, "site", "sites").takeIf { sites > 0 },
            )
            val base = if (what.isEmpty()) "A session is running until ${clock(until, zone)} with nothing on its list" else
                "Blocking ${what.joinToString(" and ")} until ${clock(until, zone)}"
            return if (state == FocusState.STRICT) "$base. Strict: it cannot be stopped early" else base
        }

        private fun clock(instant: Instant, zone: ZoneId) = instant.atZone(zone).toLocalTime().toString().take(5)

        private fun strip(now: Instant, zone: ZoneId, sessions: List<SessionView>): DayStrip {
            val today = now.atZone(zone).toLocalDate()
            val dayStart = today.atStartOfDay(zone).toInstant()
            val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
            val todays = sessions.filter { it.session.end > dayStart && it.session.start < dayEnd }
            val earliest = todays.minOfOrNull { maxOf(it.session.start, dayStart).atZone(zone).toLocalTime() }
            val from = if (earliest != null && earliest < DAY_START) earliest.withMinute(0) else DAY_START
            val fromMinutes = from.toSecondOfDay() / 60.0
            val span = DAY_MINUTES - fromMinutes
            fun at(instant: Instant): Double {
                val minutes = Duration.between(dayStart, instant.coerceIn(dayStart, dayEnd)).toMinutes().toDouble()
                return ((minutes - fromMinutes) / span).coerceIn(0.0, 1.0)
            }
            val bars = todays.sortedBy { it.session.start }.map { view ->
                val kind = when (view.session.state) {
                    SessionState.ACTIVE -> BarKind.NOW
                    SessionState.SCHEDULED -> BarKind.LATER
                    else -> BarKind.PAST
                }
                StripBar(view.session.id, at(view.session.start), at(view.session.end), kind, view.tag)
            }
            val ticks = listOf(6, 12, 18, 24).map { hour ->
                StripTick("%02d:00".format(hour), ((hour * 60.0) - fromMinutes) / span)
            }
            return DayStrip(from, bars, at(now), ticks)
        }
    }
}
