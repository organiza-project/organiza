package org.organiza.android.feature.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.organiza.presentation.Cell
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.organiza.android.ui.*

@Composable
fun HabitsScreen(ui: UiSnapshot, actions: Actions, open: (Route) -> Unit) {
    val matrix = ui.habits ?: return
    val colors = Theme.colors
    TitleBlock("Habits", matrix.finding)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Action("New habit", ButtonKind.PRIMARY) { open(Route.NEW_HABIT) }
        Action("Agenda", ButtonKind.SECONDARY) { open(Route.AGENDA) }
        Action("Import schedule", ButtonKind.SECONDARY) { open(Route.IMPORT) }
    }
    Panel {
        PanelHeader("Last 30 days") { Txt("Today", Type.small, colors.data) }
        if (matrix.rows.isEmpty()) {
            Txt("Add a habit below, or hold the talk button and say \"add habit read\".", Type.body, colors.muted)
        }
        matrix.rows.forEachIndexed { index, row ->
            if (index > 0) Rule()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(row.name, Type.bodyStrong, maxLines = 1)
                    Txt("${row.met} of ${row.days} days · streak ${row.streak}", Type.small, colors.muted)
                }
                val today = row.cells.last()
                val done = today == Cell.MET
                if (done) {
                    Txt("Done today", Type.small, colors.muted, Modifier.padding(end = 8.dp))
                    Action("Undo", ButtonKind.TEXT) { actions.markToday(row.id, row.name, false) }
                } else {
                    Action("Mark done", ButtonKind.SECONDARY) { actions.markToday(row.id, row.name, true) }
                }
            }
            HabitCells(row.cells, "${row.name}: ${row.met} of ${row.days} days met")
        }
        if (matrix.rows.isNotEmpty()) MatrixLegend()
    }
    Panel {
        PanelHeader("Speak a command")
        Txt("Say \"mark read as done\" or \"schedule focus tomorrow at 9\". Nothing changes until you confirm.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        TalkButton(actions)
        if (ui.pendingCommand != null) {
            Spacer(Modifier.height(8.dp))
            Txt(ui.pendingCommand, Type.bodyStrong)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Confirm", ButtonKind.PRIMARY) { actions.confirmCommand() }
                Action("Discard", ButtonKind.TEXT) { actions.discardCommand() }
            }
        }
    }
    MessagePanel(ui.message)
    Panel {
        PanelHeader("Coming up") { Action("Import .ics", ButtonKind.TEXT) { actions.importCalendar() } }
        if (ui.upcoming.isEmpty()) Txt("No sessions in the next 7 days. Tag calendar events #focus to schedule them.", Type.body, colors.muted)
        ui.upcoming.forEachIndexed { index, item ->
            if (index > 0) Rule()
            Row(Modifier.fillMaxWidth()) {
                Txt(item.label, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
                Txt("${item.time} · ${item.length}", Type.body, colors.muted)
            }
        }
    }
    Panel {
        PanelHeader("Weekly review")
        Txt("A short, honest summary of your week from your AI coach.", Type.small, colors.muted)
        Spacer(Modifier.height(8.dp))
        Action("Ask for a review", ButtonKind.SECONDARY) { actions.weeklyReview() }
    }
}

@Composable
private fun HabitCells(cells: List<Cell>, description: String) {
    val colors = Theme.colors
    Canvas(Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp).semantics { contentDescription = description }) {
        val gap = 2.dp.toPx()
        val cell = (size.width - gap * (cells.size - 1)) / cells.size
        val h = size.height
        cells.forEachIndexed { index, state ->
            val x = index * (cell + gap)
            val radius = CornerRadius(2.dp.toPx())
            when (state) {
                Cell.MET -> drawRoundRect(if (index == cells.lastIndex) colors.data else colors.text, Offset(x, 0f), Size(cell, h), radius)
                Cell.MISSED -> drawRoundRect(colors.control, Offset(x + 0.75f, 0.75f), Size(cell - 1.5f, h - 1.5f), radius, style = Stroke(1.5.dp.toPx()))
                Cell.UNMARKED_PAST -> drawRect(colors.control, Offset(x, h / 2 - 1), Size(cell, 2f))
                Cell.TODAY_OPEN -> drawRoundRect(colors.data, Offset(x + 0.75f, 0.75f), Size(cell - 1.5f, h - 1.5f), radius, style = Stroke(1.5.dp.toPx()))
                Cell.NOT_STARTED -> Unit
            }
        }
    }
}

@Composable
private fun MatrixLegend() {
    val colors = Theme.colors
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendMark { drawRoundRect(colors.text, cornerRadius = CornerRadius(2.dp.toPx())) }
        Txt("Done", Type.small, colors.muted, Modifier.padding(start = 6.dp, end = 14.dp))
        LegendMark { drawRoundRect(colors.control, style = Stroke(1.5.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx())) }
        Txt("Missed", Type.small, colors.muted, Modifier.padding(start = 6.dp, end = 14.dp))
        LegendMark { drawRect(colors.control, Offset(0f, size.height / 2 - 1), Size(size.width, 2f)) }
        Txt("Not marked", Type.small, colors.muted, Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun LegendMark(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Canvas(Modifier.width(9.dp).height(12.dp), onDraw = draw)
}

@Composable
private fun TalkButton(actions: Actions) {
    val colors = Theme.colors
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(if (held) colors.primary else colors.secondary, RoundedCornerShape(Space.radiusControl))
            .semantics { contentDescription = "Hold to speak a command" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    held = true
                    actions.startTalking()
                    tryAwaitRelease()
                    held = false
                    actions.stopTalking()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Txt(if (held) "Listening. Release to send" else "Hold to speak a command", Type.bodyStrong, if (held) colors.onPrimary else colors.text)
    }
}

