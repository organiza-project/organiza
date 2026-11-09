package org.organiza.feature.schedule

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.organiza.app.OrganizaCore
import org.organiza.ui.CalendarBlock
import org.organiza.ui.CalendarMode
import org.organiza.ui.CalendarView
import org.organiza.presentation.AgendaModel
import org.organiza.presentation.CalendarMonth
import org.organiza.presentation.SessionView
import org.organiza.scheduler.VoiceOutcome
import org.organiza.storage.SessionSource
import org.organiza.storage.SessionState
import org.organiza.voice.VoiceCommand

/**
 * The calendar (ADR-0019): month, week and day views over every session, and focus blocks added,
 * moved or removed on any day. A block is a scheduled session, so the scheduler arms it at its start.
 */
class CalendarController(private val app: OrganizaCore) {
    @Volatile private var selected: LocalDate? = null
    @Volatile private var mode = CalendarMode.MONTH

    fun select(day: LocalDate, then: CalendarMode? = null) {
        selected = day
        then?.let { mode = it }
        app.requestUiRefresh()
    }

    fun show(next: CalendarMode) {
        mode = next
        app.requestUiRefresh()
    }

    /** Adds a focus block, or moves [replacing] there. The old one goes only once the new one is in. */
    fun save(replacing: String?, day: LocalDate, startMinute: Int, minutes: Int) = app.onStores { stores ->
        app.hub.focusRule(stores)
        val start = day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(startMinute.toLong()).toInstant()
        val outcome = stores.voice.apply(VoiceCommand.AddSession("focus", start, minutes))
        if (outcome == VoiceOutcome.Done) replacing?.let(stores.sessions::removeScheduled)
        app.showMessage(
            when (outcome) {
                VoiceOutcome.Done -> if (replacing == null) "Focus block added" else "Focus block moved"
                VoiceOutcome.RefusedStrict -> "A strict session is running. Change blocks after it ends"
                VoiceOutcome.InvalidTime -> "Pick a start later than now"
                else -> "Could not add that block"
            },
        )
        app.requestReconcileNow()
    }

    fun delete(id: String) = app.onStores { stores ->
        stores.sessions.removeScheduled(id)
        app.showMessage("Focus block removed")
        app.requestReconcileNow()
    }

    fun view(now: Instant, zone: ZoneId, sessions: List<SessionView>): CalendarView {
        val today = now.atZone(zone).toLocalDate()
        val day = selected ?: today
        val perDay = sessions.flatMap { view ->
            val first = view.session.start.atZone(zone).toLocalDate()
            val last = view.session.end.minusMillis(1).atZone(zone).toLocalDate()
            generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
        }.groupingBy { it }.eachCount()
        val blocks = sessions
            .filter { it.session.start.atZone(zone).toLocalDate() == day }
            .sortedBy { it.session.start }
            .map { view ->
                val start = view.session.start.atZone(zone)
                val minutes = Duration.between(view.session.start, view.session.end).toMinutes().toInt()
                CalendarBlock(
                    view.session.id,
                    view.tag?.let { "#${it.removePrefix("#")}" } ?: "Focus session",
                    "${start.toLocalTime().toString().take(5)}–${view.session.end.atZone(zone).toLocalTime().toString().take(5)}",
                    start.hour * 60 + start.minute,
                    minutes,
                    view.session.state == SessionState.SCHEDULED && view.session.source == SessionSource.MANUAL,
                )
            }
        return CalendarView(
            mode,
            day,
            CalendarMonth.build(YearMonth.from(day), today, day, perDay),
            CalendarMonth.weekOf(day).map { AgendaModel.build(now, zone, it, sessions) },
            AgendaModel.build(now, zone, day, sessions),
            blocks,
        )
    }
}
