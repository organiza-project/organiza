package org.organiza.android.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import org.organiza.android.ui.*
import org.organiza.presentation.AgendaModel
import org.organiza.presentation.BarKind
import org.organiza.presentation.CalendarDay
import org.organiza.presentation.CalendarMonth
import org.organiza.presentation.Format

/** The calendar (ADR-0019): month, week or day, then the chosen day's blocks to add, move or remove. */
@Composable
fun CalendarScreen(ui: UiSnapshot, actions: Actions, back: () -> Unit, open: (Route) -> Unit) {
    val calendar = ui.calendar
    TopBar("Calendar", back)
    MessagePanel(ui.message)
    Segmented(CalendarMode.entries, calendar.mode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, actions::showCalendar)
    val day = calendar.selected
    Panel {
        when (calendar.mode) {
            CalendarMode.MONTH -> {
                Stepped(calendar.month?.title.orEmpty(), { actions.selectDay(day.minusMonths(1), null) }, { actions.selectDay(day.plusMonths(1), null) })
                calendar.month?.let { MonthGrid(it) { date -> actions.selectDay(date, null) } }
            }
            CalendarMode.WEEK -> {
                Stepped("Week of ${Format.dayMonth(CalendarMonth.weekOf(day).first())}", { actions.selectDay(day.minusWeeks(1), null) }, { actions.selectDay(day.plusWeeks(1), null) })
                WeekColumns(calendar.week, CalendarMonth.weekOf(day), day) { date -> actions.selectDay(date, CalendarMode.DAY) }
            }
            CalendarMode.DAY -> {
                Stepped(dayTitle(day), { actions.selectDay(day.minusDays(1), null) }, { actions.selectDay(day.plusDays(1), null) })
                Spacer(Modifier.height(8.dp))
                calendar.day?.let { AgendaGrid(it) }
            }
        }
    }
    DayPanel(day, calendar.blocks, actions)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Action("Import schedule", ButtonKind.SECONDARY) { open(Route.IMPORT) }
        Action("Import .ics", ButtonKind.SECONDARY) { actions.importCalendar() }
    }
}

private fun dayTitle(day: LocalDate) = "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK)} ${Format.dayMonth(day)}"

/** A title between back and forward arrows. */
@Composable
private fun Stepped(title: String, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Arrow(Glyph.BACK, "Previous", previous)
        Txt(title, Type.section, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Arrow(Glyph.NEXT, "Next", next)
    }
}

@Composable
private fun Arrow(glyph: Glyph, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Space.radiusControl)
    Box(
        Modifier
            .size(Space.target)
            .border(Space.stroke, Theme.colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(glyph) }
}

@Composable
private fun MonthGrid(month: CalendarMonth, onPick: (LocalDate) -> Unit) {
    val colors = Theme.colors
    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Txt(it, Type.small.copy(textAlign = TextAlign.Center), colors.muted, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(4.dp))
    month.weeks.forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { day -> DayCell(day, Modifier.weight(1f)) { onPick(day.date) } }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** A day: ink when chosen, a blue ring on today, one dot per block up to three. */
@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier, onClick: () -> Unit) {
    val colors = Theme.colors
    val shape = RoundedCornerShape(8.dp)
    val ink = when {
        day.selected -> colors.onPrimary
        !day.inMonth -> colors.muted
        else -> colors.text
    }
    Column(
        modifier
            .heightIn(min = Space.target)
            .aspectRatio(0.9f)
            .background(if (day.selected) colors.primary else colors.surface, shape)
            .then(if (day.isToday && !day.selected) Modifier.border(2.dp, colors.data, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                selected = day.selected
                contentDescription = "${Format.dayMonth(day.date)}, ${day.blocks} blocks"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Txt(day.date.dayOfMonth.toString(), Type.bodyStrong, ink)
        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(minOf(day.blocks, 3)) { Box(Modifier.size(5.dp).background(if (day.selected) colors.onPrimary else colors.data, CircleShape)) }
        }
    }
}

/** Seven narrow hour columns, 06:00 to midnight, with each day's blocks drawn in place. */
@Composable
private fun WeekColumns(week: List<AgendaModel>, dates: List<LocalDate>, chosen: LocalDate, onPick: (LocalDate) -> Unit) {
    val colors = Theme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.zip(dates).forEach { (agenda, date) ->
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onPick(date) }
                    .semantics { contentDescription = "${dayTitle(date)}, ${agenda.blocks.size} blocks" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK), Type.small, colors.muted)
                Txt(date.dayOfMonth.toString(), Type.bodyStrong, if (date == chosen) colors.data else colors.text)
                Spacer(Modifier.height(4.dp))
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .border(Space.stroke, if (date == chosen) colors.data else colors.rule, RoundedCornerShape(6.dp)),
                ) {
                    agenda.blocks.forEach { block ->
                        Box(
                            Modifier
                                .offset(y = maxHeight * block.top.toFloat())
                                .padding(horizontal = 2.dp)
                                .fillMaxWidth()
                                .height(maxOf(maxHeight * (block.bottom - block.top).toFloat(), 6.dp))
                                .background(if (block.kind == BarKind.PAST) colors.control.copy(alpha = 0.35f) else colors.data, RoundedCornerShape(3.dp)),
                        )
                    }
                    agenda.now?.let { Box(Modifier.offset(y = maxHeight * it.toFloat()).fillMaxWidth().height(2.dp).background(colors.negative)) }
                }
            }
        }
    }
    Txt("Tap a day to open it. Blue: planned or running. Red line: now.", Type.small, colors.muted, Modifier.padding(top = 8.dp))
}

/** The chosen day's sessions, with edit and remove on focus blocks that have not started, and the editor. */
@Composable
private fun DayPanel(day: LocalDate, blocks: List<CalendarBlock>, actions: Actions) {
    val colors = Theme.colors
    var editing by rememberSaveable(day) { mutableStateOf<String?>(null) }
    var hour by rememberSaveable(day) { mutableIntStateOf(9) }
    var quarter by rememberSaveable(day) { mutableIntStateOf(0) }
    var length by rememberSaveable(day) { mutableIntStateOf(60) }
    Panel {
        PanelHeader(dayTitle(day)) { Txt(Format.count(blocks.size, "session", "sessions"), Type.small, colors.muted) }
        if (blocks.isEmpty()) Txt("Nothing planned. Add a focus block below.", Type.body, colors.muted)
        blocks.forEachIndexed { index, block ->
            if (index > 0) Rule()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(block.label, Type.bodyStrong, maxLines = 1)
                    Txt(block.time + if (block.editable) "" else " · locked", Type.small, colors.muted)
                }
                if (block.editable) {
                    Action("Edit", ButtonKind.TEXT) {
                        editing = block.id
                        hour = block.startMinute / 60
                        quarter = block.startMinute % 60 / 15
                        length = block.minutes
                    }
                    Spacer(Modifier.size(8.dp))
                    Action("Remove", ButtonKind.DANGER) { actions.deleteFocusBlock(block.id) }
                }
            }
        }
    }
    Panel {
        PanelHeader(if (editing == null) "Add a focus block" else "Move this focus block")
        Txt("Starts", Type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Stepper(hour, "h", 0..23) { hour = it }
        }
        Spacer(Modifier.height(8.dp))
        Segmented(listOf(0, 1, 2, 3), quarter, { ":%02d".format(it * 15) }) { quarter = it }
        Spacer(Modifier.height(12.dp))
        Txt("Length", Type.bodyStrong, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            // Minus and plus move a quarter hour, not a minute.
            Stepper(length, "min", MIN_LENGTH..MAX_LENGTH) { wanted -> length = (if (wanted > length) length + 15 else length - 15).coerceIn(MIN_LENGTH, MAX_LENGTH) }
        }
        Spacer(Modifier.height(12.dp))
        Txt("The blocker arms itself at the start with your focus list.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(if (editing == null) "Add block" else "Save", ButtonKind.PRIMARY) {
                actions.saveFocusBlock(editing, day, hour * 60 + quarter * 15, length)
                editing = null
            }
            if (editing != null) Action("Cancel", ButtonKind.SECONDARY) { editing = null }
        }
    }
}

private const val MIN_LENGTH = 15
private const val MAX_LENGTH = 480
