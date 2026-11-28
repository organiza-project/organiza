package org.organiza.feature.schedule

import org.organiza.storage.CalendarEvent
import java.util.UUID
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
    private companion object {
        const val MAX_TITLE = 120
    }

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

    /** Puts an event on the calendar; [remindBefore] minutes before it a reminder rings, or none when null. */
    fun addEvent(day: LocalDate, startMinute: Int, minutes: Int, title: String, remindBefore: Int?) = app.onStores { stores ->
        val name = title.trim().take(MAX_TITLE)
        if (name.isEmpty()) return@onStores app.showMessage("Give the event a title")
        val start = day.atStartOfDay(ZoneId.systemDefault()).plusMinutes(startMinute.toLong()).toInstant()
        stores.events.add(CalendarEvent(UUID.randomUUID().toString(), name, start, minutes, remindBefore))
        app.showMessage(if (remindBefore == null) "Added to your calendar" else "Added to your calendar, with a reminder")
        app.rescheduleReminders()
    }

    fun deleteEvent(id: String) = app.onStores { stores ->
        stores.events.delete(id)
        app.showMessage("Event removed")
        app.rescheduleReminders()
    }

    fun delete(id: String) = app.onStores { stores ->
        stores.sessions.removeScheduled(id)
        app.showMessage("Focus block removed")
        app.requestReconcileNow()
    }

    /** Storage thread. */
    fun view(now: Instant, zone: ZoneId, sessions: List<SessionView>, stores: OrganizaCore.Storage): CalendarView {
        val today = now.atZone(zone).toLocalDate()
        val day = selected ?: today
        // The month around the chosen day, with the edges of the weeks it shows.
        val month = YearMonth.from(day)
        val events = stores.events.between(
            month.atDay(1).minusDays(7).atStartOfDay(zone).toInstant(),
            month.atEndOfMonth().plusDays(8).atStartOfDay(zone).toInstant(),
        )
        fun days(start: Instant, end: Instant): List<LocalDate> {
            val first = start.atZone(zone).toLocalDate()
            val last = end.minusMillis(1).atZone(zone).toLocalDate()
            return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
        }
        val perDay = (sessions.flatMap { days(it.session.start, it.session.end) } + events.flatMap { days(it.start, it.end) })
            .groupingBy { it }.eachCount()
        val dayEvents = events.filter { it.start.atZone(zone).toLocalDate() == day }.map { event ->
            val start = event.start.atZone(zone)
            CalendarBlock(
                event.id,
                event.title,
                "${start.toLocalTime().toString().take(5)}–${event.end.atZone(zone).toLocalTime().toString().take(5)}",
                start.hour * 60 + start.minute,
                event.minutes,
                editable = false,
                event = true,
            )
        }
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
            CalendarMonth.weekOf(day).map { AgendaModel.build(now, zone, it, sessions, events) },
            AgendaModel.build(now, zone, day, sessions, events),
            (blocks + dayEvents).sortedBy { it.startMinute },
        )
    }
}
